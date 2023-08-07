package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.plugins.pagination.PageDto;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.mapper.LearningLessonMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
