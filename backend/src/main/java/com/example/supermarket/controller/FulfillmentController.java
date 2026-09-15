package com.example.supermarket.controller;

import com.example.supermarket.common.ApiResponse;
import com.example.supermarket.dto.StoreResponse;
import com.example.supermarket.service.DeliverySlotService;
import com.example.supermarket.service.StoreService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 履约选项（公开）：自提门店列表 + 可选配送时段，结算页用它渲染「送货上门 / 门店自提」 */
@RestController
public class FulfillmentController {

    private final StoreService storeService;
    private final DeliverySlotService deliverySlotService;

    public FulfillmentController(StoreService storeService, DeliverySlotService deliverySlotService) {
        this.storeService = storeService;
        this.deliverySlotService = deliverySlotService;
    }

    @GetMapping("/stores")
    public ApiResponse<List<StoreResponse>> stores() {
        return ApiResponse.ok(storeService.listOpen());
    }

    @GetMapping("/delivery-slots")
    public ApiResponse<List<Map<String, Object>>> deliverySlots() {
        return ApiResponse.ok(deliverySlotService.slots());
    }
}
