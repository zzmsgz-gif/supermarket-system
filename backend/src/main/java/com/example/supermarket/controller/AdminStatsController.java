package com.example.supermarket.controller;

import com.example.supermarket.common.ApiResponse;
import com.example.supermarket.dto.AdminDashboardStats;
import com.example.supermarket.dto.AdminStatsOverview;
import com.example.supermarket.service.AdminDashboardService;
import com.example.supermarket.service.AdminStatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/stats")
public class AdminStatsController {

    private final AdminStatsService adminStatsService;
    private final AdminDashboardService adminDashboardService;

    public AdminStatsController(AdminStatsService adminStatsService,
                                AdminDashboardService adminDashboardService) {
        this.adminStatsService = adminStatsService;
        this.adminDashboardService = adminDashboardService;
    }

    @GetMapping("/overview")
    public ApiResponse<AdminStatsOverview> overview() {
        return ApiResponse.ok(adminStatsService.overview());
    }

    /** 经营看板：range 取 today / 7d / 30d / 90d，默认 7d */
    @GetMapping("/dashboard")
    public ApiResponse<AdminDashboardStats> dashboard(@RequestParam(defaultValue = "7d") String range) {
        return ApiResponse.ok(adminDashboardService.dashboard(range));
    }
}
