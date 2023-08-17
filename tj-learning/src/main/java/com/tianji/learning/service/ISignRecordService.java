package com.tianji.learning.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.po.PointsRecord;
import com.tianji.learning.domain.vo.SignResultVO;
import com.tianji.learning.service.impl.SignRecordServiceImpl;

/**
 * <p>
 * 学习积分记录，每个月底清零 服务类
 * </p>
 *
 * @author author
 * @since 2023-08-17
 */
public interface ISignRecordService {

    SignResultVO addSignRecords();
}
