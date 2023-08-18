package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsRecord;
import com.tianji.learning.domain.vo.PointsStatisticsVO;
import com.tianji.learning.enums.PointsRecordType;
import com.tianji.learning.mapper.PointsRecordMapper;
import com.tianji.learning.service.IPointsRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.checkerframework.checker.units.qual.A;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.validation.Valid;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 学习积分记录，每个月底清零 服务实现类
 * </p>
 *
 * @author author
 * @since 2023-08-17
 */
@RequiredArgsConstructor
@Service
public class PointsRecordServiceImpl extends ServiceImpl<PointsRecordMapper, PointsRecord> implements IPointsRecordService {

    private final StringRedisTemplate redisTemplate;
    @Override
    /**
     * 为用户添加积分记录
     * @param    userId 用户的ID
     * @param points 要添加的积分值
     * @param type 积分记录的类型
     */
    public void addPointsRecord(Long userId, int points, PointsRecordType type) {
        // 获取该类型的积分上限
        int maxPoints = type.getMaxPoints();
        // 获取当前时间
        LocalDateTime now = LocalDateTime.now();

        // 设定要添加的实际积分为传入的积分值
        int realPoints = points;

        // 如果此积分类型有上限
        if (maxPoints > 0) {
            // 获取当天的开始和结束时间
            LocalDateTime begin = DateUtils.getDayStartTime(now);
            LocalDateTime end = DateUtils.getDayEndTime(now);

            // 查询用户当天已获得的此类型的积分总数
            int currentPoints = queryUserPointsByTypeAndDate(userId, type, begin, end);

            // 如果用户当天已获得的积分已达到上限，则直接返回
            if (currentPoints >= maxPoints) {
                return;
            }

            // 如果用户将要获得的积分加上当天已获得的积分超过上限，则调整实际添加的积分值
            if (currentPoints + points >= maxPoints) {
                realPoints = maxPoints - currentPoints;
            }
        }

        // 创建并设置新的积分记录
        PointsRecord p = new PointsRecord();
        p.setUserId(userId);
        p.setType(type);
        p.setPoints(points);
        // 保存新的积分记录到数据库
        save(p);

        // 更新Redis中的用户积分榜，增加用户的实际积分
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + now.format(DateUtils.POINTS_BOARD_SUFFIX_FORMATTER);
        redisTemplate.opsForZSet().incrementScore(key, userId.toString(), realPoints);
    }


    @Override
/**
 * 查询当前用户当天的积分记录
 * @return 返回当天的积分记录视图模型列表
 */
    public List<PointsStatisticsVO> queryMyPointsToday() {
        // 从用户上下文中获取当前用户的ID
        Long userId = UserContext.getUser();

        // 获取当前日期和时间
        LocalDateTime now = LocalDateTime.now();
        // 获取当天的开始时间
        LocalDateTime begin = DateUtils.getDayStartTime(now);
        // 获取当天的结束时间
        LocalDateTime end = DateUtils.getDayEndTime(now);

        // 初始化查询条件
        QueryWrapper<PointsRecord> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .eq(PointsRecord::getUserId, userId) // 用户ID必须与当前用户ID匹配
                .between(PointsRecord::getCreateTime, begin, end); // 创建时间必须在当天范围内

        // 根据查询条件从数据库中获取积分记录
        List<PointsRecord> list = getBaseMapper().queryUserPointsByDate(wrapper);

        // 如果查询结果为空，则返回一个空列表
        if (CollUtils.isEmpty(list)) {
            return CollUtils.emptyList();
        }

        // 初始化一个视图模型列表
        List<PointsStatisticsVO> vos = new ArrayList<>(list.size());

        // 遍历每一条积分记录
        for (PointsRecord p : list) {
            // 创建一个新的视图模型对象
            PointsStatisticsVO vo = new PointsStatisticsVO();
            // 设置类型描述
            vo.setType(p.getType().getDesc());
            // 设置最大积分（此处与下方设置的积分相同，可能需要根据实际需求进行调整）
            vo.setMaxPoints(p.getPoints());
            // 设置积分
            vo.setPoints(p.getPoints());

            // 将视图模型添加到列表中
            vos.add(vo);
        }
        // 返回视图模型列表
        return vos;
    }

    private int queryUserPointsByTypeAndDate(
            Long userId, PointsRecordType type, LocalDateTime begin, LocalDateTime end) {
        QueryWrapper<PointsRecord> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .eq(PointsRecord::getUserId, userId)
                .eq(type != null, PointsRecord::getType, type)
                .between(PointsRecord::getCreateTime, begin, end);
        Integer points = getBaseMapper().queryUserPointsByTypeAndDate(wrapper);
        //判断
        return points == null ? 0 : points;
    }
}
