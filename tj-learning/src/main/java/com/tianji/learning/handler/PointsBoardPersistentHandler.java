package com.tianji.learning.handler;

import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.service.IPointsBoardSeasonService;
import com.tianji.learning.service.IPointsBoardService;
import com.tianji.learning.utils.TableInfoContext;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PointsBoardPersistentHandler {
    private final IPointsBoardSeasonService seasonService;
    private final StringRedisTemplate redisTemplate;

    private final IPointsBoardService pointsBoardService;

    @XxlJob("createTableJob")
    public void createPointsBoardTableOfLastSeason(){
        LocalDateTime time = LocalDateTime.now().minusMonths(1);
        Integer season = seasonService.querySeasonByTime(time);
        if (season == null) {
            return;
        }
        pointsBoardService.createPointsBoardTableBySeason(season);
    }


    @XxlJob("savePointsBoard2DB")
    public void savePointsBoard2DB(){
        LocalDateTime time = LocalDateTime.now().minusMonths(1);
        Integer season = seasonService.querySeasonByTime(time);
        TableInfoContext.setInfo("points_board" + season);

        //拼接key
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + time.format(DateUtils.POINTS_BOARD_SUFFIX_FORMATTER);
        int index = XxlJobHelper.getShardIndex();
        int total = XxlJobHelper.getShardTotal();
        int pageNo = index + 1;
        int pageSize = 1000;
        while (true){
            List<PointsBoard> boardList = pointsBoardService.queryCurrentBoard(key, pageNo, pageSize);
            if (CollUtils.isEmpty(boardList)) {
                break;
            }
            boardList.forEach(b -> {
                b.setId(b.getRank().longValue());
                b.setRank(null);
            });
            pointsBoardService.saveBatch(boardList);
            pageNo+=total;
        }
    }

    @XxlJob("clearPointsBoardFromRedis")
    public void clearPointsBoardFromRedis(){
        LocalDateTime time = LocalDateTime.now().minusMonths(1);
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + time.format(DateUtils.POINTS_BOARD_SUFFIX_FORMATTER);
        redisTemplate.unlink(key);
    }

}
