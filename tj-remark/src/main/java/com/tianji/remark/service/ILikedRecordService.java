package com.tianji.remark.service;

import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.domain.po.LikedRecord;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Set;

/**
 * <p>
 * 点赞记录表 服务类
 * </p>
 *
 * @author author
 * @since 2023-08-14
 */
public interface ILikedRecordService extends IService<LikedRecord> {

    void addLikeRecord(LikeRecordFormDTO recordDTO);

    Set<Long> isBizLiked(List<Long> bizIds);

    void readLikeTimesAndSendMessage(String bizType, int maxBizSize);
}
