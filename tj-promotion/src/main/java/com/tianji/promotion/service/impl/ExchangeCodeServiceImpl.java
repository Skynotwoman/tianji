package com.tianji.promotion.service.impl;

import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.tianji.promotion.mapper.ExchangeCodeMapper;
import com.tianji.promotion.service.IExchangeCodeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.promotion.utils.CodeUtil;
import org.springframework.data.redis.core.BoundValueOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static com.tianji.promotion.constants.PromotionConstants.COUPON_CODE_MAP_KEY;
import static com.tianji.promotion.constants.PromotionConstants.COUPON_CODE_SERIAL_KEY;
import static com.tianji.promotion.constants.PromotionConstants.COUPON_RANG_KEY;

/**
 * <p>
 * 兑换码 服务实现类
 * </p>
 *
 * @author author
 * @since 2023-08-19
 */
@Service
public class ExchangeCodeServiceImpl extends ServiceImpl<ExchangeCodeMapper, ExchangeCode> implements IExchangeCodeService {

    private final StringRedisTemplate redisTemplate;
    private final BoundValueOperations<String, String> serialOps;
    public ExchangeCodeServiceImpl(StringRedisTemplate redisTemplate){
        this.redisTemplate = redisTemplate;
        this.serialOps = redisTemplate.boundValueOps(COUPON_CODE_SERIAL_KEY);
    }
    // 使用名为 "generateExchangeCodeExecutor" 的异步执行器来异步执行该方法  就是我用管理端生成了优惠券也发放了但是数据库里没有兑换码没有兑换码
    @Async("generateExchangeCodeExecutor")
    @Override
    public void asyncGenerateCode(Coupon coupon) {
        // 获取要生成的优惠券兑换码的总数量
        Integer totalNum = coupon.getTotalNum();

        // 对序列号进行递增，并获取递增后的结果
        Long result = serialOps.increment(totalNum);

        // 如果序列号递增失败，则直接返回
        if (result == null) {
            return;
        }

        // 将递增后的长整型结果转换为整型
        int maxSerialNum = result.intValue();

        // 创建一个列表，用于存储即将生成的兑换码
        List<ExchangeCode> list = new ArrayList<>(totalNum);

        // 为每个优惠券生成一个兑换码
        for (int serialNum = maxSerialNum - totalNum + 1; serialNum <= maxSerialNum; serialNum++) {

            // 根据序列号和优惠券ID生成兑换码
            String code = CodeUtil.generateCode(serialNum, coupon.getId());

            // 创建一个新的兑换码对象并设置其属性
            ExchangeCode e = new ExchangeCode();
            e.setCode(code);  // 设置兑换码
            e.setId(serialNum);  // 设置序列号
            e.setExchangeTargetId(coupon.getId());  // 设置关联的优惠券ID
            e.setExpiredTime(coupon.getIssueEndTime());  // 设置兑换码的过期时间

            // 将新创建的兑换码对象添加到列表中
            list.add(e);
        }

        // 批量保存所有生成的兑换码到数据库
        saveBatch(list);

        // 将优惠券ID和对应的最大序列号添加到Redis的有序集合中
        // 有序集合的键为COUPON_RANG_KEY，优惠券ID作为成员，最大序列号作为分数
        redisTemplate.opsForZSet().add(COUPON_RANG_KEY, coupon.getId().toString(), maxSerialNum);
    }


    @Override
    public boolean updateExchangeMark(long serialNum, boolean mark) {
        Boolean boo = redisTemplate.opsForValue().setBit(COUPON_CODE_MAP_KEY, serialNum, mark);
        return boo != null && boo;
    }
}
