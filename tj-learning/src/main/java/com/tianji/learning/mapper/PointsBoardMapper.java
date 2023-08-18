package com.tianji.learning.mapper;

import com.tianji.learning.domain.po.PointsBoard;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import feign.Param;

/**
 * <p>
 * 学霸天梯榜 Mapper 接口
 * </p>
 *
 * @author author
 * @since 2023-08-17
 */
public interface PointsBoardMapper extends BaseMapper<PointsBoard> {

    void createPointsBoardTable(@Param("tableName") String tableName);
}
