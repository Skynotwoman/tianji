package com.tianji.promotion.service.impl;

import ch.qos.logback.core.joran.conditional.IfAction;
import cn.hutool.log.Log;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.domain.dto.CouponDiscountDTO;
import com.tianji.promotion.domain.dto.OrderCourseDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.CouponScope;
import com.tianji.promotion.mapper.UserCouponMapper;
import com.tianji.promotion.service.ICouponScopeService;
import com.tianji.promotion.service.ICouponService;
import com.tianji.promotion.service.IDiscountService;
import com.tianji.promotion.strategy.discount.Discount;
import com.tianji.promotion.strategy.discount.DiscountStrategy;
import com.tianji.promotion.utils.PermuteUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscountServiceImpl implements IDiscountService {
    private final UserCouponMapper userCouponMapper;
    private final ICouponScopeService scopeService;

    private final Executor discountSolutionExecutor;

    @Override
    public List<CouponDiscountDTO> findDiscountSolution(List<OrderCourseDTO> orderCourse) {
        List<Coupon> coupons = userCouponMapper.queryMyCoupons(UserContext.getUser());
        if (CollUtils.isEmpty(coupons)){
            return CollUtils.emptyList();
        }
        int totalAmount = orderCourse.stream().mapToInt(OrderCourseDTO::getPrice).sum();

        List<Coupon> availableCoupons = coupons.stream()
                .filter(c -> DiscountStrategy.getDiscount(
                        c.getDiscountType())
                        .canUse(totalAmount, c))
                .collect(Collectors.toList());
        if (CollUtils.isEmpty(availableCoupons)) {
            return CollUtils.emptyList();
        }
        Map<Coupon, List<OrderCourseDTO>> availableCouponMap = findAvailableCoupon(availableCoupons, orderCourse);
        //排列组合
        if (CollUtils.isEmpty(availableCouponMap)) {
            return CollUtils.emptyList();
        }
        availableCoupons =new ArrayList<>(availableCouponMap.keySet());
        List<List<Coupon>> solutions = PermuteUtil.permute(availableCoupons);
        for (Coupon c : availableCoupons) {
            solutions.add(List.of(c));
        }
        List<CouponDiscountDTO> list = Collections.synchronizedList(new ArrayList<>(solutions.size()));
        CountDownLatch latch = new CountDownLatch(solutions.size());
        for (List<Coupon> solution : solutions) {
            CompletableFuture
                    .supplyAsync(() ->calculateSolutionDiscount(availableCouponMap, orderCourse, solution))
                    .thenAccept(dto ->{
                        list.add(dto);
                        latch.countDown();
                    });
        }
        //
        try {
            latch.await(2, TimeUnit.SECONDS);
        }catch (InterruptedException e){
            log.error("优惠方案计算被中断，{}", e.getMessage());
        }
        //最优解

        return findBestSolution(list);
    }

    private List<CouponDiscountDTO> findBestSolution(List<CouponDiscountDTO> list) {
        Map<String, CouponDiscountDTO> moreDiscountMap = new HashMap<>();
        Map<Integer, CouponDiscountDTO> lessCouponMap = new HashMap<>();
        for (CouponDiscountDTO solution : list){
            String ids = solution.getIds().stream().sorted(Long::compare).map(String::valueOf)
                    .collect(Collectors.joining(","));
            CouponDiscountDTO best = moreDiscountMap.get(ids);
            if (best != null && best.getDiscountAmount() >= solution.getDiscountAmount()) {
                continue;
            }
            best = lessCouponMap.get(solution.getDiscountAmount());
            if (solution.getIds().size() > 1 && best != null && best.getIds().size() <= solution.getIds().size()) {
                continue;
            }
            moreDiscountMap.put(ids, solution);
            lessCouponMap.put(solution.getDiscountAmount(), solution);
        }
        Collection<CouponDiscountDTO> bestSolution = CollUtils.intersection(moreDiscountMap.values(), lessCouponMap.values());
        return bestSolution.stream()
                .sorted(Comparator.comparingInt(CouponDiscountDTO::getDiscountAmount).reversed())
                .collect(Collectors.toList());
    }

    private CouponDiscountDTO calculateSolutionDiscount(
            Map<Coupon, List<OrderCourseDTO>> couponMap, List<OrderCourseDTO> courses, List<Coupon> solution) {
        CouponDiscountDTO dto = new CouponDiscountDTO();
        Map<Long, Integer> detailMap = courses.stream().collect(Collectors.toMap(OrderCourseDTO::getId, oc -> 0));
        for (Coupon coupon : solution) {
            List<OrderCourseDTO> availableCourses = couponMap.get(coupon);
            //计算总价
            int totalAmount = availableCourses.stream()
                    .mapToInt(oc -> oc.getPrice() - detailMap.get(oc.getId())).sum();
            Discount discount = DiscountStrategy.getDiscount(coupon.getDiscountType());
            if (!discount.canUse(totalAmount, coupon)) {
                continue;
            }
            int discountAmount = discount.calculateDiscount(totalAmount, coupon);
            calculateDiscountDetails(detailMap, availableCourses, totalAmount, discountAmount);
            dto.getIds().add(coupon.getId());
            dto.getRules().add(discount.getRule(coupon));
            dto.setDiscountAmount(discountAmount + dto.getDiscountAmount());
        }
        return dto;
    }

    private void calculateDiscountDetails(
            Map<Long, Integer> detailMap, List<OrderCourseDTO> courses, int totalAmount, int discountAmount) {
        int time = 0;
        int remainDiscount = discountAmount;
        for (OrderCourseDTO course : courses) {
            time++;
            int discount = 0;
            if (time == courses.size()) {
                //最后一个课程
                discount = remainDiscount;

            }else {
                discount = discountAmount * course.getPrice() / totalAmount;
                remainDiscount -= discount;
            }

            detailMap.put(course.getId(), discount + detailMap.get(course.getId()));

        }

    }

    private Map<Coupon, List<OrderCourseDTO>> findAvailableCoupon(
            List<Coupon> coupons, List<OrderCourseDTO> courses) {
        Map<Coupon, List<OrderCourseDTO>> map = new HashMap<>(coupons.size());
        for (Coupon coupon : coupons) {
            List<OrderCourseDTO> availableCourse =courses;
            if (coupon.getSpecific()){
                List<CouponScope> scopes = scopeService.lambdaQuery()
                        .eq(CouponScope::getCouponId, coupon.getId()).list();
                Set<Long> scopeIds = scopes.stream().map(CouponScope::getBizId).collect(Collectors.toSet());
                availableCourse = courses.stream().filter(c -> scopeIds.contains(c.getCateId())).collect(Collectors.toList());
            }
            if (CollUtils.isEmpty(availableCourse)){
                continue;
            }
            int totalAmount = availableCourse.stream().mapToInt(OrderCourseDTO::getPrice).sum();
            Discount discount = DiscountStrategy.getDiscount(coupon.getDiscountType());
            if (discount.canUse(totalAmount, coupon)) {
                map.put(coupon, availableCourse);
            }
        }
        return map;
    }
}
