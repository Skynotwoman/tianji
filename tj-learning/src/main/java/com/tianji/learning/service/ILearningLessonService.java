package com.tianji.learning.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.PageDto;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.learning.domain.po.LearningLesson;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;

import java.util.List;

/**
 * <p>
 * 学生课程表 服务类
 * </p>
 *
 * @author author
 *  * @since 2023-08-06
 */
public interface ILearningLessonService extends IService<LearningLesson> {

    void addUserLessons(Long userId, List<Long> courseIds);

    PageDTO<LearningLessonVO> queryMyLessons(PageQuery query);

    LearningLessonVO queryLessonByCourseId(Long courseId);

    LearningLesson queryByUserIdAndCourseId(Long userId, Long courseId);

    void createLearningPlan(Long courseId, Integer freq);

    LearningPlanPageVO queryMyPlans(PageQuery query);
}
