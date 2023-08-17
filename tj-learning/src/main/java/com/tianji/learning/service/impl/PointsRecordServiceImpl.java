package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.po.PointsRecord;
import com.tianji.learning.domain.vo.PointsStatisticsVO;
import com.tianji.learning.enums.PointsRecordType;
import com.tianji.learning.mapper.PointsRecordMapper;
import com.tianji.learning.service.IPointsRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
@Service
public class PointsRecordServiceImpl extends ServiceImpl<PointsRecordMapper, PointsRecord> implements IPointsRecordService {

    @Override
    public void addPointsRecord(Long userId, int points, PointsRecordType type) {
        int maxPoints = type.getMaxPoints();
        //判断有没有上线
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime begin = DateUtils.getDayStartTime(now);
        LocalDateTime end = DateUtils.getDayEndTime(now);
        int realPoints = points;
        if (maxPoints > 0){
            //查询今天获取的
            int currentPoints = queryUserPointsByTypeAndDate(userId, type, begin, end);
            //判断上线
            if (currentPoints >= maxPoints) {
                return;
            }
            //没超过
            if (currentPoints + points >= maxPoints) {
                realPoints = maxPoints - currentPoints;
            }
        }
        //没有 保存
        PointsRecord p = new PointsRecord();
        p.setUserId(userId);
        p.setType(type);
        p.setPoints(points);
        save(p);

    }

    @Override
    public List<PointsStatisticsVO> queryMyPointsToday() {
        Long userId = UserContext.getUser();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime begin = DateUtils.getDayStartTime(now);
        LocalDateTime end = DateUtils.getDayEndTime(now);
        QueryWrapper<PointsRecord> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .eq(PointsRecord::getUserId, userId)
                .between(PointsRecord::getCreateTime, begin, end);
        //查询
        List<PointsRecord> list = getBaseMapper().queryUserPointsByDate(wrapper);
        if (CollUtils.isEmpty(list)) {
            return CollUtils.emptyList();
        }
        List<PointsStatisticsVO> vos = new ArrayList<>(list.size());
        for (PointsRecord p : list) {
            PointsStatisticsVO vo = new PointsStatisticsVO();
            vo.setType(p.getType().getDesc());
            vo.setMaxPoints(p.getPoints());
            vo.setPoints(p.getPoints());
            vos.add(vo);
        }
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
