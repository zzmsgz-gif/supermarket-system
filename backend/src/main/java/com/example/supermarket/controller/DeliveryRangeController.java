package com.example.supermarket.controller;

import com.example.supermarket.common.ApiResponse;
import com.example.supermarket.dto.DeliveryRangeResponse;
import com.example.supermarket.service.DeliveryRangeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 即时配送范围查询。
 *
 * <p>给结算页用：选定地址后就地判断能否即时配送，**在点提交之前**提示，
 * 而不是让用户填完一切再被 409 打回（这与购物车失效行体检是同一条原则）。
 * 真正的强制仍然在下单侧（{@code OrderService.createOrder}）。
 */
@RestController
@RequestMapping("/delivery-range")
public class DeliveryRangeController {

    private final DeliveryRangeService deliveryRangeService;

    public DeliveryRangeController(DeliveryRangeService deliveryRangeService) {
        this.deliveryRangeService = deliveryRangeService;
    }

    @GetMapping
    public ApiResponse<DeliveryRangeResponse> check(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String district
    ) {
        boolean deliverable = deliveryRangeService.deliverable(city, district);
        return ApiResponse.ok(new DeliveryRangeResponse(deliverable, deliveryRangeService.coverageLabel()));
    }
}
