package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.api.dto.leanring.LearningRecordDTO;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.LearningRecordFormDTO;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.SectionType;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.tianji.learning.service.ILearningRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import groovyjarjarasm.asm.Handle;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * <p>
 * 学习记录表 服务实现类
 * </p>
 *
 * @author author
 * @since 2023-08-07
 */
@Service
@RequiredArgsConstructor
public class LearningRecordServiceImpl extends ServiceImpl<LearningRecordMapper, LearningRecord> implements ILearningRecordService {

    private final ILearningLessonService lessonService;


    private final CourseClient courseClient;
    @Override
    public LearningLessonDTO queryLearningRecordByCourse(Long courseId) {
        //获取登录用户
        Long userId = UserContext.getUser();
        //查询课表
        LearningLesson lesson = lessonService.queryByUserIdAndCourseId(userId, courseId);
        if (lesson == null){
            return null;
        }
        //查询学习记录 selcet * from
        List<LearningRecord> records = lambdaQuery()
                .eq(LearningRecord::getLessonId, lesson.getId())
                .list();
        //封装结果
        LearningLessonDTO dto = new LearningLessonDTO();
        dto.setId(lesson.getId());
        dto.setLatestSectionId(lesson.getLatestSectionId());
        dto.setRecords(BeanUtils.copyList(records, LearningRecordDTO.class));
        return dto;
    }

    @Override
    @Transactional
    public void addLearningRecord(LearningRecordFormDTO recordDTO) {
        //获取登录用户ID
        Long userId = UserContext.getUser();
        //2.处理学习记录
        boolean finished = false;
        if (recordDTO.getSectionType() == SectionType.VIDEO){
            //2.1处理视频
            finished = handleVideoRecord(userId, recordDTO);
        }else {
            //2.2处理考试
            finished = handleExamRecord(userId, recordDTO);
        }

        //处理课表数据
        handleLearningLessonsChanges(recordDTO, finished);
    }

    private void handleLearningLessonsChanges(LearningRecordFormDTO recordDTO, boolean finished) {
       //查询课表
        LearningLesson lesson = lessonService.getById(recordDTO.getLessonId());
        if (lesson == null){
            throw new BizIllegalException("课程不存在，无法更新数据!");
        }
        //判断是否有新的完成小节
        boolean allLearned = false;
        if (finished){
            CourseFullInfoDTO cInfo = courseClient.getCourseInfoById(lesson.getCourseId(), false, false);
            if (cInfo == null){
                throw new BizIllegalException("课程不存在，无法更新数据!");
            }
            //如果有新完成，查询课程数据
            allLearned = lesson.getLearnedSections() + 1 >= cInfo.getSectionNum();
        }
        //更新课表
        lessonService.lambdaUpdate()
                .set(lesson.getLearnedSections() == 0, LearningLesson::getStatus, LessonStatus.LEARNING.getValue())
                .set(allLearned, LearningLesson::getStatus, LessonStatus.FINISHED.getValue())
                .set(!finished, LearningLesson::getLatestSectionId, recordDTO.getSectionId())
                .set(!finished, LearningLesson::getLatestLearnTime, recordDTO.getCommitTime())
                .setSql(finished, "learned_sections = learnedd_sections + 1 ")
                .eq(LearningLesson::getId,lesson.getId())
                .update();
        //比较课程是否已经学完


    }

    private boolean handleVideoRecord(Long userId, LearningRecordFormDTO recordDTO) {
        //查询旧的学习记录
        LearningRecord old = lambdaQuery()
                .eq(LearningRecord::getLessonId, recordDTO.getLessonId())
                .eq(LearningRecord::getSectionId, recordDTO.getSectionId())
                .one();
        //判断是否存在
        if (old == null){
            //不存在，新增
            //转换PO
            LearningRecord record = BeanUtils.copyBean(recordDTO, LearningRecord.class);
            record.setUserId(userId);
            //写入数据库
            boolean success = save(record);
            if (!success){
                throw new DbException("新增学习记录失败！");
            }
            return false;
        }
        //存在，更新
        //判断是否第一次完成
        boolean finished = !old.getFinished() && recordDTO.getMoment() * 2 >= recordDTO.getDuration();
        //更新数据
        boolean success = lambdaUpdate()
                .set(LearningRecord::getMoment, recordDTO.getMoment())
                .set(finished, LearningRecord::getFinished, true)
                .set(finished, LearningRecord::getFinishTime, recordDTO.getCommitTime())
                .eq(LearningRecord::getId, old.getId())
                .update();
        if (!success){
            throw new DbException("更新考试记录失败！");
        }
        return finished;
    }

    private boolean handleExamRecord(Long userId, LearningRecordFormDTO recordDTO) {
        //转换DTO
        LearningRecord record = BeanUtils.copyBean(recordDTO, LearningRecord.class);
        record.setUserId(userId);
        record.setFinished(true);
        record.setFinishTime(recordDTO.getCommitTime());

        //写入数据库
        boolean success = save(record);
        if (!success){
            throw new DbException("新增考试记录失败！");
        }
        return false;
    }
}
