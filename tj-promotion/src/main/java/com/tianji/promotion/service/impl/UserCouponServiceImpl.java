package com.tianji.promotion.service.impl;

import cn.hutool.core.bean.copier.CopyOptions;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.autoconfigure.redisson.annotations.Lock;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.constants.PromotionConstants;
import com.tianji.promotion.domain.dto.UserCouponDTO;
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
import com.tianji.promotion.utils.MyLock;
import com.tianji.promotion.utils.RedisLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
@Slf4j
public class UserCouponServiceImpl extends ServiceImpl<UserCouponMapper, UserCoupon> implements IUserCouponService {

    private final CouponMapper couponMapper;

    private final IExchangeCodeService codeService;
    private final RedissonClient redissonClient;
    private final StringRedisTemplate redisTemplate;
    private final RabbitMqHelper mqHelper;

    @Override
    @Lock(name = "lock:coupon:#{couponId}")
    public void receiveCoupon(Long couponId) {

        // 从数据库中通过ID查询优惠券
        Coupon coupon = queryCouponByCache(couponId);

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
        if (coupon.getIssueNum() <= 0) {
            throw new BadRequestException("优惠券库存不足！");
        }

        // 从上下文中获取当前用户的ID
        Long userId = UserContext.getUser();

        // 查询该用户已领取该优惠券的数量
        //String key = "lock:coupon:uid:" + userId;

        String key = PromotionConstants.USER_COUPON_CACHE_KEY_PREFIX + couponId;
        Long count = redisTemplate.opsForHash().increment(key, userId.toString(), 1);

        if (count > coupon.getUserLimit()){
            throw new BadRequestException("超出领取数量！");
        }
        redisTemplate.opsForHash().increment(
                PromotionConstants.COUPON_CACHE_KEY_PREFIX + couponId, "totalNum", -1);

        UserCouponDTO uc = new UserCouponDTO();
        uc.setUserId(userId);
        uc.setCouponId(couponId);
        mqHelper.send(MqConstants.Exchange.PROMOTION_EXCHANGE,MqConstants.Key.COUPON_RECEIVE, uc);


        /*IUserCouponService userCouponService = (IUserCouponService) AopContext.currentProxy();
        userCouponService.checkAndCreateUserCoupon(coupon, userId);*/


       /* RLock lock = redissonClient.getLock(key);
        boolean isLock = lock.tryLock();
        if (!isLock) {
            throw new BizIllegalException("请求太频繁！");
        }
        try {
            IUserCouponService userCouponService = (IUserCouponService) AopContext.currentProxy();
            userCouponService.checkAndCreateUserCoupon(coupon, userId);
        } finally {
            lock.unlock();
        }*/

    }

    private Coupon queryCouponByCache(Long couponId) {
        String key = PromotionConstants.COUPON_CACHE_KEY_PREFIX + couponId;
        Map<Object, Object> objMap = redisTemplate.opsForHash().entries(key);
        if (objMap.isEmpty()) {
            return null;
        }
        return BeanUtils.mapToBean(objMap, Coupon.class, false, CopyOptions.create());
    }


    @Transactional
    @Override
    public void checkAndCreateUserCoupon(UserCouponDTO uc) {
        Coupon coupon = couponMapper.selectById(uc.getCouponId());
        if (coupon == null) {
            throw new BizIllegalException("优惠券不存在");
        }

        // 增加该优惠券的已发放数量 +
        int r = couponMapper.incrIssueNum(coupon.getId());
        if (r == 0) {

                throw new BizIllegalException("优惠券库存在不足！");
        }

            // 保存用户领取的优惠券记录
        saveUserCoupon(coupon, uc.getUserId());

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
            throw new BizIllegalException("兑换码已经被兑换！");
        }

        try {
            // 通过序列号查询兑换码详情
            ExchangeCode exchangeCode = codeService.getById(serialNum);

            // 如果兑换码不存在，抛出异常
            if (exchangeCode == null) {
                throw new BizIllegalException("兑换码不存在！");
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
            //checkAndCreateUserCoupon(coupon, userId);

            // 更新兑换码的用户ID和状态
            codeService.lambdaUpdate()
                    .set(ExchangeCode::getUserId, userId)
                    .set(ExchangeCode::getStatus, ExchangeCodeStatus.USED)
                    .eq(ExchangeCode::getId, exchangeCode.getId())
                    .update();
        } catch (Exception e) {  // 在处理过程中捕获任何异常
            // 如果发生异常，将兑换码标记为未兑换
            codeService.updateExchangeMark(serialNum, false);

            // 重新抛出捕获的异常，因为该方法是事务性的，所以任何未处理的异常都会导致事务回滚
            throw e;
        }
        log.info("兑换成功。");
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
