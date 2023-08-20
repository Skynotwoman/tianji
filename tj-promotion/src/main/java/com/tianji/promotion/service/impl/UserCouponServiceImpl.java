package com.tianji.promotion.service.impl;

import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.enums.ExchangeCodeStatus;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.mapper.UserCouponMapper;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.promotion.utils.CodeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 服务实现类
 * </p>
 *
 * @author author
 * @since 2023-08-20
 */
@Service
@RequiredArgsConstructor
public class UserCouponServiceImpl extends ServiceImpl<UserCouponMapper, UserCoupon> implements IUserCouponService {

    private CouponMapper couponMapper;

    private IExchangeCodeService codeService;
    @Override
    @Transactional
    public void receiveCoupon(Long couponId) {

        // 从数据库中通过ID查询优惠券
        Coupon coupon = couponMapper.selectById(couponId);

        // 如果查询不到这个优惠券，抛出异常
        if (coupon == null) {
            throw new BadRequestException("优惠券不存在！");
        }

        LocalDateTime now = LocalDateTime.now();

        // 检查当前时间是否在优惠券的发放时间范围内
        if (now.isBefore(coupon.getIssueBeginTime()) || now.isAfter(coupon.getIssueEndTime())) {
            throw new BadRequestException("优惠券发放已经结束或尚未开始！");
        }

        // 检查优惠券的已发放数量是否已经达到总数限制
        if (coupon.getIssueNum() >= coupon.getTotalNum()) {
            throw new BadRequestException("优惠券库存不足！");
        }

        // 从上下文中获取当前用户的ID
        Long userId = UserContext.getUser();

        // 查询该用户已领取该优惠券的数量
        checkAndCreateUserCoupon(coupon, userId);
    }

    private void checkAndCreateUserCoupon(Coupon coupon, Long userId) {
        Integer count = lambdaQuery()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getCouponId, coupon.getId())
                .count();

        // 检查该用户是否已达到领取该优惠券的上限
        if (count != null && count >= coupon.getUserLimit()) {
            throw new BadRequestException("领取次数太多！");
        }

        // 增加该优惠券的已发放数量
        couponMapper.incrIssueNum(coupon.getId());

        // 保存用户领取的优惠券记录
        saveUserCoupon(coupon, userId);
    }

    @Override
    @Transactional  // 声明该方法需要事务管理
    public void exchangeCoupon(String code) {
        // 使用工具方法解析兑换码，获取序列号
        long serialNum = CodeUtil.parseCode(code);

        // 尝试将兑换码标记为已兑换
        boolean exchanged = codeService.updateExchangeMark(serialNum, true);

        // 如果兑换码已经被标记为已兑换，则抛出异常
        if (exchanged) {
            throw new BadRequestException("兑换码已经被兑换！");
        }

        try {
            // 通过序列号查询兑换码详情
            ExchangeCode exchangeCode = codeService.getById(serialNum);

            // 如果兑换码不存在，抛出异常
            if (exchangeCode == null) {
                throw new BadRequestException("兑换码不存在！");
            }

            // 获取当前时间
            LocalDateTime now = LocalDateTime.now();

            // 检查兑换码是否已经过期
            if (now.isAfter(exchangeCode.getExpiredTime())) {
                throw new BadRequestException("兑换码已经过期！");
            }

            // 获取与兑换码关联的优惠券详情
            Coupon coupon = couponMapper.selectById(exchangeCode.getExchangeTargetId());

            // 获取当前用户ID
            Long userId = UserContext.getUser();

            // 检查并创建用户的优惠券
            checkAndCreateUserCoupon(coupon, userId);

            // 更新兑换码的用户ID和状态
            codeService.lambdaUpdate()
                    .set(ExchangeCode::getUserId, userId)
                    .set(ExchangeCode::getStatus, ExchangeCodeStatus.UNUSED)
                    .eq(ExchangeCode::getId, exchangeCode.getId())
                    .update();
        } catch (Exception e) {  // 在处理过程中捕获任何异常
            // 如果发生异常，将兑换码标记为未兑换
            codeService.updateExchangeMark(serialNum, false);

            // 重新抛出捕获的异常，因为该方法是事务性的，所以任何未处理的异常都会导致事务回滚
            throw e;
        }
    }


    private void saveUserCoupon(Coupon coupon, Long userId) {

        // 创建一个新的UserCoupon对象，用于保存用户领取的优惠券记录
        UserCoupon uc = new UserCoupon();
        uc.setUserId(userId);
        uc.setCouponId(coupon.getId());

        // 获取优惠券的有效期开始和结束时间
        LocalDateTime termBeginTime = coupon.getTermBeginTime();
        LocalDateTime termEndTime = coupon.getTermEndTime();

        // 如果优惠券没有设置有效期开始时间，将其设置为当前时间，并根据优惠券的有效天数计算结束时间
        if (termBeginTime == null) {
            termBeginTime = LocalDateTime.now();
            termEndTime = termBeginTime.plusDays(coupon.getTermDays());
        }

        // 设置UserCoupon的有效期开始和结束时间
        uc.setTermBeginTime(termBeginTime);
        uc.setTermEndTime(termEndTime);

        // 在数据库中保存UserCoupon对象
        save(uc);
    }

}
