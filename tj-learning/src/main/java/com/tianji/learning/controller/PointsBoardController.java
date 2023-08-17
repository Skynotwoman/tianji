package com.tianji.learning.controller;


import com.tianji.learning.service.IPointsBoardSeasonService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 学霸天梯榜 前端控制器
 * </p>
 *
 * @author author
 * @since 2023-08-17
 */
@RestController
@RequestMapping("/pointsBoard")
@RequiredArgsConstructor
public class PointsBoardController {
    private final IPointsBoardSeasonService pointsBoardSeasonService;


}
