package com.tianji.promotion.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.cache.CategoryCache;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.*;
import com.tianji.promotion.constants.PromotionConstants;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.dto.CouponIssueFormDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.CouponScope;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.domain.vo.CouponVO;
import com.tianji.promotion.enums.CouponStatus;
import com.tianji.promotion.enums.ObtainType;
import com.tianji.promotion.enums.UserCouponStatus;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.service.ICouponScopeService;
import com.tianji.promotion.service.ICouponService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.events.Event;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.tianji.promotion.enums.CouponStatus.*;

/**
 * <p>
 * 优惠券的规则信息 服务实现类
 * </p>
 *
 * @author author
 * @since 2023-08-19
 */
@Service
@RequiredArgsConstructor
public class CouponServiceImpl extends ServiceImpl<CouponMapper, Coupon> implements ICouponService {

    private final ICouponScopeService scopeService;

    private final IExchangeCodeService codeService;

    private final IUserCouponService userCouponService;

    private final CategoryCache categoryCache;

    private final StringRedisTemplate redisTemplate;
    /**
     * 保存优惠券信息。
     * <p>
     * 此方法首先将提供的DTO转换为Coupon对象并将其保存到数据库中。
     * 如果优惠券具有特定的适用范围，它还会保存这些适用范围。
     * </p>
     *
     * @param dto 优惠券的表单数据传输对象，包含了优惠券的详细信息和可能的适用范围。
     * @throws BadRequestException 如果优惠券具有特定的适用范围，但未提供任何范围，则抛出此异常。
     */
    @Override
    @Transactional  // 声明该方法为事务性的，确保数据的完整性和一致性
    public void saveCoupon(CouponFormDTO dto) {

        // 将提供的DTO转换为Coupon实体对象
        Coupon coupon = BeanUtils.copyBean(dto, Coupon.class);

        // 将Coupon对象保存到数据库中
        save(coupon);

        // 如果优惠券不具有特定的适用范围，则结束方法
        if (!dto.getSpecific()){
            return;
        }

        // 获取优惠券在数据库中的ID
        Long couponId = coupon.getId();

        // 获取DTO中的适用范围列表
        List<Long> scopes = dto.getScopes();

        // 如果范围列表为空，抛出异常
        if (CollUtils.isEmpty(scopes)) {
            throw new BadRequestException("限定范围不能为空");
        }

        // 将范围列表转换为CouponScope对象列表
        List<CouponScope> list = scopes.stream()
                .map(bizId -> new CouponScope().setBizId(bizId).setCouponId(couponId))
                .collect(Collectors.toList());

        // 批量保存CouponScope对象列表到数据库中
        scopeService.saveBatch(list);
    }


    @Override
    /**
     * 通过分页查询优惠券信息。
     *
     * @param query 优惠券查询条件对象，包含类型、状态和名称等查询条件
     * @return 返回一个包含优惠券视图对象列表的分页数据传输对象
     */
    public PageDTO<CouponPageVO> queryCouponByPage(CouponQuery query) {

        // 获取查询条件的类型、状态和名称
        Integer type = query.getType();
        Integer status = query.getStatus();
        String name = query.getName();

        // 使用lambda查询构建分页查询条件
        Page<Coupon> page = lambdaQuery()
                // 如果类型不为null，增加按类型等值查询条件
                .eq(type != null, Coupon::getDiscountType, type)
                // 如果状态不为null，增加按状态等值查询条件
                .eq(status != null, Coupon::getStatus, status)
                // 如果名称不为空，增加按名称模糊查询条件
                .like(StringUtils.isNotBlank(name), Coupon::getName, name)
                // 执行分页查询，并默认按创建时间降序排序
                .page(query.toMpPageDefaultSortByCreateTimeDesc());

        // 获取查询结果记录
        List<Coupon> records = page.getRecords();

        // 如果记录为空，返回一个空的分页数据传输对象
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }

        // 将Coupon对象列表转换为CouponPageVO对象列表
        List<CouponPageVO> list = BeanUtils.copyList(records, CouponPageVO.class);

        // 返回包含CouponPageVO对象列表的分页数据传输对象
        return PageDTO.of(page, list);
    }

    @Override
    /**
     * 根据提供的发放表单数据开始发放优惠券。
     *
     * @param dto 优惠券发放的表单数据传输对象，包含了优惠券的发放开始时间和优惠券ID等信息。
     * @throws BadRequestException 当优惠券不存在时抛出。
     * @throws BizIllegalException 当优惠券的状态不为DRAFT或PAUSE时抛出。
     */
    @Transactional
    public void beginIssue(CouponIssueFormDTO dto) {

        // 根据DTO中的ID从数据库中获取优惠券信息
        Coupon coupon = getById(dto.getId());

        // 如果从数据库中未查询到优惠券信息，抛出异常
        if (coupon == null) {
            throw new BadRequestException("优惠券不存在！");
        }

        // 检查优惠券的状态。如果状态不是草稿(DRAFT)或暂停(PAUSE)，抛出异常
        if (coupon.getStatus() != CouponStatus.DRAFT && coupon.getStatus() != PAUSE){
            throw new BizIllegalException("优惠券状态错误！");
        }

        // 获取发放的开始时间和当前时间
        LocalDateTime issueBeginTime = dto.getIssueBeginTime();
        LocalDateTime now = LocalDateTime.now();

        // 判断是否应立即开始发放。如果发放的开始时间为null或在当前时间之后，则返回true
        boolean isBegin = issueBeginTime == null || !issueBeginTime.isAfter(now);

        // 使用DTO的内容创建一个新的Coupon对象
        Coupon c = BeanUtils.copyBean(dto, Coupon.class);


        // 如果应立即开始发放，将状态设置为正在发放(ISSUING)，并将发放开始时间设置为当前时间
        if (isBegin) {
            c.setStatus(ISSUING);
            c.setIssueBeginTime(now);
        } else {
            // 否则，将状态设置为未发放(UN_ISSUE)
            c.setStatus(UN_ISSUE);
        }

        // 更新数据库中的优惠券信息
        updateById(c);

       if (isBegin) {
            coupon.setIssueBeginTime(c.getIssueBeginTime());
            coupon.setIssueEndTime(c.getIssueEndTime());
            cacheCouponInfo(coupon);
        }

        // 6.判断是否需要生成兑换码，优惠券类型必须是兑换码，优惠券状态必须是待发放 哦哦
        if (coupon.getObtainWay() == ObtainType.ISSUE && coupon.getStatus() == CouponStatus.DRAFT) {
            coupon.setIssueEndTime(c.getIssueEndTime());
            codeService.asyncGenerateCode(coupon);
        }
    }

    private void cacheCouponInfo(Coupon coupon) {
        Map<String,String> map = new HashMap<>();
        map.put("issueBeginTime", String.valueOf(DateUtils.toEpochMilli(coupon.getIssueBeginTime())));
        map.put("issueEndnTime", String.valueOf(DateUtils.toEpochMilli(coupon.getIssueEndTime())));
        map.put("totalNum", String.valueOf(coupon.getIssueNum()));
        map.put("userLimit", String.valueOf(coupon.getUserLimit()));
        redisTemplate.opsForHash().putAll(PromotionConstants.COUPON_CACHE_KEY_PREFIX + coupon.getId(), map);
    }

    @Override
    public List<CouponVO> queryIssuingCoupon() {

        // 通过lambda查询，获取所有状态为ISSUING并且获取方式为PUBLIC的优惠券
        List<Coupon> coupons = lambdaQuery()
                .eq(Coupon::getStatus, ISSUING)
                .eq(Coupon::getObtainWay, ObtainType.PUBLIC)
                .list();

        // 如果没有查询到任何优惠券，则返回一个空列表
        if (CollUtils.isEmpty(coupons)) {
            return CollUtils.emptyList();
        }

        // 提取所有查询到的优惠券的ID
        List<Long> collectIds = coupons.stream().map(Coupon::getId).collect(Collectors.toList());

        // 根据当前用户的ID和上面提取的优惠券ID，查询用户所拥有的优惠券
        List<UserCoupon> userCoupons = userCouponService.lambdaQuery()
                .eq(UserCoupon::getUserId, UserContext.getUser())
                .eq(UserCoupon::getCouponId, collectIds)
                .list();

        // 创建一个映射，显示每张优惠券被用户领取的次数
        Map<Long, Long> issuedMap = userCoupons.stream()
                .collect(Collectors.groupingBy(UserCoupon::getCouponId, Collectors.counting()));

        // 创建一个映射，显示每张优惠券被用户领取但未使用的次数
        Map<Long, Long> unusedMap = userCoupons.stream()
                .filter(uc -> uc.getStatus() == UserCouponStatus.UNUSED)
                .collect(Collectors.groupingBy(UserCoupon::getCouponId, Collectors.counting()));

        // 将Coupon对象列表转换为CouponVO对象列表，并设置额外的属性
        List<CouponVO> list = new ArrayList<>(coupons.size());
        for (Coupon c : coupons) {
            // 将Coupon对象转换为CouponVO对象
            CouponVO vo = BeanUtils.copyBean(c, CouponVO.class);
            list.add(vo);

            // 根据一定的条件设置优惠券是否可用
            vo.setAvailable(
                    c.getIssueNum() < c.getTotalNum()
                            && issuedMap.getOrDefault(c.getId(), 0L) < c.getUserLimit()
            );

            // 设置用户是否已经领取并且还未使用这张优惠券
            vo.setReceived(unusedMap.getOrDefault(c.getId(), 0L) > 0);
        }
        return list;
    }

    @Override
    @Transactional
    public void pauseIssue(long id) {
        Coupon coupon = getById(id);
        if (coupon == null) {
            throw new BadRequestException("优惠券不存在！");
        }

        CouponStatus status = coupon.getStatus();
        if (status != UN_ISSUE && status != ISSUING) {
            return;
        }

        boolean success = lambdaUpdate()
                .set(Coupon::getStatus, PAUSE)
                .eq(Coupon::getId, id)
                .in(Coupon::getStatus, UN_ISSUE, ISSUING)
                .update();
        if (!success) {
            log.error("重复暂停优惠券！");
        }

        redisTemplate.delete(PromotionConstants.COUPON_CACHE_KEY_PREFIX + id);

    }

}
