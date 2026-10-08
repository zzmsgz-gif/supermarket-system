package com.example.supermarket.controller;

import com.example.supermarket.common.ApiResponse;
import com.example.supermarket.common.PageResponse;
import com.example.supermarket.dto.AmountRecordResponse;
import com.example.supermarket.dto.AmountSummaryResponse;
import com.example.supermarket.security.CurrentUser;
import com.example.supermarket.service.AmountRecordService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 金额明细（用户端第 7 条）。
 *
 * <p>与 {@code /wallet/transactions} 的区别：那个查的是**钱包余额变动**，
 * 这个查的是**每笔订单的金额构成**（实付 / 券 / 活动 / 会员 / 积分 / 运费 / 退款）。
 * 用户想对账时看的是后者 —— 优惠抵扣不产生余额变动，在余额流水里查不到。
 */
@Validated
@RestController
@RequestMapping("/amount-records")
public class AmountRecordController {

    private final AmountRecordService service;

    public AmountRecordController(AmountRecordService service) {
        this.service = service;
    }

    /**
     * 列表（服务端筛选 + 服务端分页）。
     *
     * <p>group：ALL / PAY / DISCOUNT / REFUND / POINTS，未知值按 ALL 处理。
     * 筛选与分页**都在 SQL 层完成** —— 前端拉一页再本地过滤会让翻页页数对不上
     * （第 2 页拿到的是服务端第 2 页，而不是筛选结果的第 2 页）。
     */
    @GetMapping
    public ApiResponse<PageResponse<AmountRecordResponse>> list(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "ALL") String group
    ) {
        return ApiResponse.ok(service.list(currentUser.getId(), page, size, group));
    }

    /**
     * 三项汇总（总账，**不随筛选变化**）。
     *
     * <p>单独一个接口而不是塞进 list：汇总的含义是「总账」，
     * 跟着 tab 变会让人以为账目错乱（切到「退款」时累计支出不该变成退款额）。
     */
    @GetMapping("/summary")
    public ApiResponse<AmountSummaryResponse> summary(
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        return ApiResponse.ok(service.summarize(currentUser.getId()));
    }

    /** 某个订单的全部流水（订单详情页「金额构成」按时间线展示） */
    @GetMapping("/orders/{orderId}")
    public ApiResponse<List<AmountRecordResponse>> listByOrder(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable("orderId") Long orderId
    ) {
        return ApiResponse.ok(service.listByOrder(currentUser.getId(), orderId));
    }
}
