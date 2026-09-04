package com.example.supermarket.controller;

import com.example.supermarket.common.ApiResponse;
import com.example.supermarket.common.PageResponse;
import com.example.supermarket.dto.StockAlertResponse;
import com.example.supermarket.dto.StockLogResponse;
import com.example.supermarket.service.AdminStockService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/admin")
public class AdminStockController {

    private final AdminStockService adminStockService;

    public AdminStockController(AdminStockService adminStockService) {
        this.adminStockService = adminStockService;
    }

    @GetMapping("/stock-logs")
    public ApiResponse<PageResponse<StockLogResponse>> listStockLogs(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String bizType
    ) {
        return ApiResponse.ok(adminStockService.listStockLogs(page, size, productId, bizType));
    }

    @GetMapping("/stock-alerts")
    public ApiResponse<List<StockAlertResponse>> listLowStockProducts() {
        return ApiResponse.ok(adminStockService.listLowStockProducts());
    }
}
