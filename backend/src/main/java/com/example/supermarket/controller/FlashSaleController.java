package com.example.supermarket.controller;

import com.example.supermarket.common.ApiResponse;
import com.example.supermarket.dto.FlashSaleResponse;
import com.example.supermarket.security.CurrentUser;
import com.example.supermarket.service.FlashSaleService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 前台秒杀：不需要登录即可浏览（加入购物车/下单仍需登录）。
 *
 * <p>虽然公开，但仍然接收可选的登录态 —— JWT 过滤器对所有路径生效，带了 token 时
 * {@code currentUser} 就有值。这样才能把「我已占用几件 / 还能买几件 / 有没有未付款订单占着名额」
 * 一并返回，让前端在购物车里提前禁用按钮，而不是等用户点下单才报错。
 */
@RestController
@RequestMapping("/flash-sales")
public class FlashSaleController {

    private final FlashSaleService flashSaleService;

    public FlashSaleController(FlashSaleService flashSaleService) {
        this.flashSaleService = flashSaleService;
    }

    @GetMapping
    public ApiResponse<List<FlashSaleResponse>> list(@AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(flashSaleService.listPublic(currentUser == null ? null : currentUser.getId()));
    }
}
