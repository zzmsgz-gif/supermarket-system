package com.example.supermarket.controller;

import com.example.supermarket.common.ApiResponse;
import com.example.supermarket.common.PageResponse;
import com.example.supermarket.dto.AmountRecordResponse;
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

    @GetMapping
    public ApiResponse<PageResponse<AmountRecordResponse>> list(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.ok(service.list(currentUser.getId(), page, size));
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
