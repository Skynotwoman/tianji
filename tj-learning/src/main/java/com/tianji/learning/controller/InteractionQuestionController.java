package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.service.IInteractionQuestionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * <p>
 * 互动提问的问题表 前端控制器
 * </p>
 *
 * @author author
 * @since 2023-08-12
 */
@RestController
@RequestMapping("/questions")
@Api(tags = "互动问答相关接口")
@RequiredArgsConstructor
public class InteractionQuestionController {


    private final IInteractionQuestionService questionService;

    @ApiOperation("新增互动问题")
    @PostMapping
    private void saveQuestion(@Valid @RequestBody QuestionFormDTO questionDTO){
        questionService.saveQuestion(questionDTO);
    }

    @ApiOperation("分页查询互动问题")
    @GetMapping("page")
    public PageDTO<QuestionVO> queryQuestionPage(QuestionPageQuery query){
        return questionService.queryQuestionPage(query);
    }

    @ApiOperation("根据id查询互动问题")
    @GetMapping("{id}")
    public QuestionVO queryQuestionById(@PathVariable("id") Long id){
        return questionService.queryQuestionById(id);

    }
}
