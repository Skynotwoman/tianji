package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.IdAndNumDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.*;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import com.tianji.learning.domain.vo.LearningPlanVO;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.PlanStatus;
import com.tianji.learning.mapper.LearningLessonMapper;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.codehaus.groovy.classgen.asm.LambdaWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 学生课程表 服务实现类
 * </p>
 *
 * @author author
 * @since 2023-08-06
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LearningLessonServiceImpl extends ServiceImpl<LearningLessonMapper, LearningLesson> implements ILearningLessonService {


    private final CourseClient courseClient;
    private final CatalogueClient catalogueClient;
    private final LearningRecordMapper recordMapper;
    @Override
    @Transactional
    public void addUserLessons(Long userId, List<Long> courseIds) {
        //查询课程有效期
        List<CourseSimpleInfoDTO> cInfoList = courseClient.getSimpleInfoList(courseIds);
        if (CollUtils.isEmpty(cInfoList)){
            log.error("课程信息不存在，无法添加到课表");
            return;
        }
        //循环遍历
        List<LearningLesson> list = new ArrayList<>(cInfoList.size());
        for (CourseSimpleInfoDTO cInfo : cInfoList){
            LearningLesson lesson =new LearningLesson();
            //获取过期时间
            Integer validDuration = cInfo.getValidDuration();
            if (validDuration != null && validDuration > 0){
                LocalDateTime now = LocalDateTime.now();
                lesson.setCreateTime(now);
                lesson.setExpireTime(now.plusMonths(validDuration));
            }
            //填充id
            lesson.setUserId(userId);
            lesson.setCourseId(cInfo.getId());
            list.add(lesson);
            //批量新增
            saveBatch(list);

        }
    }

    @Override
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery query) {
        //获取当前用户
        Long userId = UserContext.getUser();

        //分页查询
        //select * from learning_lesson where user_id = #{userId} order by latest_time limit 0,5
        Page<LearningLesson> page = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .page(query.toMpPage("latest_learn_time", false));
        List<LearningLesson> records = page.getRecords();
        if (CollUtils.isEmpty(records)){
            return PageDTO.empty(page);
        }
        //查询课程信息
        Map<Long, CourseSimpleInfoDTO> cMap = queryCourseSimpleInfoList(records);

        //封装VO返回
        //循环遍历
        List<LearningLessonVO> list = new ArrayList<>(records.size());
        for (LearningLesson r : records) {
            //拷贝基础属性
            LearningLessonVO vo = BeanUtils.copyBean(r, LearningLessonVO.class);
            //获取课程信息 填充到vo
            CourseSimpleInfoDTO cInfo = cMap.get(r.getCourseId());
            vo.setCourseName(cInfo.getName());
            vo.setCourseCoverUrl(cInfo.getCoverUrl());
            vo.setSections(cInfo.getSectionNum());
            list.add(vo);
        }
        return PageDTO.of(page, list);
    }

    private Map<Long, CourseSimpleInfoDTO> queryCourseSimpleInfoList(List<LearningLesson> records) {
        //获取课程id
        Set<Long> cIds = records.stream().map(LearningLesson::getCourseId).collect(Collectors.toSet());
        //查询课程信息
        List<CourseSimpleInfoDTO> cInfoList = courseClient.getSimpleInfoList(cIds);
        if (CollUtils.isEmpty(cInfoList)){
            throw new BadRequestException("课程信息不存在");
        }
        //课程集合处理成map key是courseId
        Map<Long, CourseSimpleInfoDTO> cMap =
                cInfoList.stream().collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));
        return cMap;
    }

    @Override
    public LearningLessonVO queryLessonByCourseId(Long courseId){
        //获取当前用户id
        Long userId = UserContext.getUser();
        //查询课程信息
        LearningLesson lesson = getOne(buildUserIdAndCourseIdWrapper(userId, courseId));
        if (lesson == null){
            return null;
        }
        return BeanUtils.copyBean(lesson, LearningLessonVO.class);
    }

    @Override
    public LearningLesson queryByUserIdAndCourseId(Long userId, Long courseId) {
        return getOne(buildUserIdAndCourseIdWrapper(userId,courseId));
    }

    @Override
    public void createLearningPlan(Long courseId, Integer freq) {
        //查询课表指定数据
        //获取用户id
        Long userId = UserContext.getUser();
        //查询课表id
        LearningLesson lesson = queryByUserIdAndCourseId(userId, courseId);
        /*if (lesson == null) {
            throw new BadRequestException("课程信息不存在");
        }*/
        AssertUtils.isNotNull(lesson,"课程信息不存在");

        LearningLesson l = new LearningLesson();
        l.setId(lesson.getId());
        l.setWeekFreq(freq);
        if (lesson.getPlanStatus() == PlanStatus.NO_PLAN){
            l.setPlanStatus(PlanStatus.PLAN_RUNNING);
        }
        updateById(l);
    }

    /*查询我的学习计划*/
    @Override
    public LearningPlanPageVO queryMyPlans(PageQuery query) {
        LearningPlanPageVO result = new LearningPlanPageVO();
        //查询学习计划
        //获取用户id
        Long userId = UserContext.getUser();
        //获取本周起始时间
        LocalDate now = LocalDate.now();
        LocalDateTime begin = DateUtils.getWeekBeginTime(now);
        LocalDateTime end = DateUtils.getWeekEndTime(now);
        //查询总的统计数据
        //本周总的已经学习数量
        Integer weekFinished = recordMapper.selectCount(new LambdaQueryWrapper<LearningRecord>()
                .eq(LearningRecord::getUserId, userId)
                .eq(LearningRecord::getFinished, true)
                .gt(LearningRecord::getFinishTime, begin)
                .lt(LearningRecord::getFinishTime, end)
        );
        result.setWeekFinished(weekFinished);
        //本周计划的学习计划数量
        Integer weekTotalPlan = getBaseMapper().queryTotalPlan(userId);
        result.setWeekTotalPlan(weekTotalPlan);
        //TODO 学习积分

        //分页数据
        //分页查询课表
        Page<LearningLesson> p = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getPlanStatus, PlanStatus.PLAN_RUNNING)
                .in(LearningLesson::getStatus, LessonStatus.NOT_BEGIN, LessonStatus.LEARNING)
                .page(query.toMpPage("latest_learn_time", false));
        List<LearningLesson> records = p.getRecords();
        if (CollUtils.isEmpty(records)) {
            return result.emptyPage(p);
        }
        //课表对应的课程信息
        Map<Long, CourseSimpleInfoDTO> cMap = queryCourseSimpleInfoList(records);
        //每一个课程的已经学习小节数量
        List<IdAndNumDTO> list = recordMapper.countLearnedSections(userId,begin,end);
        Map<Long, Integer> countMap = IdAndNumDTO.toMap(list);
        //组装数据vo
        List<LearningPlanVO> voList = new ArrayList<>(records.size());
        for (LearningLesson r : records) {
            LearningPlanVO vo = BeanUtils.copyBean(r, LearningPlanVO.class);
            //填充课程信息
            CourseSimpleInfoDTO cInfo = cMap.get(r.getCourseId());
            if (cInfo == null) {
                vo.setCourseName(cInfo.getName());
                vo.setSections(cInfo.getSectionNum());
            }
            vo.setWeekLearnedSections(countMap.getOrDefault(r.getId(),0));
            voList.add(vo);
        }
        return result.pageInfo(p.getTotal(),p.getPages(),voList);
    }

    /**
     * 基于用户ID和课程ID构建查询条件。
     *
     * @param userId    用户ID
     * @param courseId  课程ID
     * @return 返回构建好的Lambda查询条件
     */
    private LambdaQueryWrapper<LearningLesson> buildUserIdAndCourseIdWrapper(Long userId, Long courseId){
        // 创建一个新的QueryWrapper，并转换为Lambda查询格式
        LambdaQueryWrapper<LearningLesson> queryWrapper = new QueryWrapper<LearningLesson>()
                .lambda()
                // 添加等于userId的查询条件
                .eq(LearningLesson::getUserId, userId)
                // 添加等于courseId的查询条件
                .eq(LearningLesson::getCourseId,courseId);

        // 返回构建好的查询条件
        return queryWrapper;
    }

}
