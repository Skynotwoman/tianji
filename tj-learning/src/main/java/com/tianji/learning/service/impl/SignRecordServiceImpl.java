package com.tianji.learning.service.impl;

import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BooleanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.vo.SignResultVO;
import com.tianji.learning.mq.message.SignInMessage;
import com.tianji.learning.service.ISignRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * <p>
 * 学习积分记录，每个月底清零 服务实现类
 * </p>
 *
 * @author author
 * @since 2023-08-17
 */
@Service
@RequiredArgsConstructor
public class SignRecordServiceImpl implements ISignRecordService {
    private final RabbitMqHelper mqHelper;
    private final StringRedisTemplate redisTemplate;
    @Override
    public SignResultVO addSignRecords() {
        //签到
        Long userId = UserContext.getUser();
        LocalDate now = LocalDate.now();
        String key = RedisConstants.SING_RECORD_KEY_PREFIX
                + userId
                + now.format(DateUtils.SIGN_DATE_SUFFIX_FORMATTER);
        //计算offset 当前日期-1
        int offset = now.getDayOfMonth() - 1;
        //保存签到信息
        Boolean exits = redisTemplate.opsForValue().setBit(key, offset, true);
        if (BooleanUtils.isTrue(exits)) {
            throw new BizIllegalException("不允许重复签到！");
        }
        //计算连续签到天数
        int signDays = countSignDays(key, now.getDayOfMonth());
        //计算签到积分
        int rewardPoints = 0;
        switch (signDays){
            case 7:
                rewardPoints = 10;
                break;
            case 14:
                rewardPoints = 20;
                break;
            case 28:
                rewardPoints = 40;
                break;
        }
        //保存积分明细
        mqHelper.send(
                MqConstants.Exchange.LEARNING_EXCHANGE,
                MqConstants.Key.SIGN_IN,
                SignInMessage.of(userId, rewardPoints + 1));

        //分装返回vo
        SignResultVO vo = new SignResultVO();
        vo.setSignDays(signDays);
        vo.setRewardPoints(rewardPoints);
        return vo;
    }

    @Override
    public Byte[] querySignRecord() {
        // 从用户上下文中获取当前用户的ID
        Long userId = UserContext.getUser();

        // 获取当前日期
        LocalDate now = LocalDate.now();

        // 获取当前月份的天数
        int dayOfMonth = now.getDayOfMonth();

        // 根据用户ID和日期生成Redis的键名
        String key = RedisConstants.SING_RECORD_KEY_PREFIX
                + userId
                + now.format(DateUtils.SIGN_DATE_SUFFIX_FORMATTER);

        // 从Redis中按位查询指定键名下的签到数据
        // 使用bitfield命令检索指定的位字段
        List<Long> result = redisTemplate.opsForValue()
                .bitField(key, BitFieldSubCommands.create().get(
                        BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));

        // 如果结果为空或不存在，则返回一个空的字节数组
        if (CollUtils.isEmpty(result)) {
            return new Byte[0];
        }

        // 将结果转化为整数
        int num = result.get(0).intValue();

        // 初始化一个Byte数组，其长度与当前月份的天数相同
        Byte[] arr = new Byte[dayOfMonth];

        // 从结果的最后一个位开始，逐个解析每一位，将其存储在Byte数组中
        int pos = dayOfMonth - 1;
        while (pos >= 0) {
            // 通过&操作获取当前位的值（0或1）
            arr[pos--] = (byte)(num & 1);

            // 右移一位，准备检查下一个位
            num >>>= 1;
        }

        // 返回存储签到记录的Byte数组
        return arr;
    }



    private int countSignDays(String key, int len) {
        List<Long> result = redisTemplate.opsForValue()
                .bitField(key, BitFieldSubCommands.create().get(
                        BitFieldSubCommands.BitFieldType.unsigned(len)).valueAt(0));
        if (CollUtils.isEmpty(result)) {
            return 0;
        }
        int num = result.get(0).intValue();
        int count = 0;
        //循环，与1做计算，得到最后一个bit，判断是否为0，为0则终止
        while ((num & 1) == 1){
            count++;
            num >>>= 1;
        }
        return count;
    }
}
