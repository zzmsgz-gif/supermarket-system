package com.example.supermarket.controller;

import com.example.supermarket.common.ApiResponse;
import com.example.supermarket.dto.ActivityResponse;
import com.example.supermarket.service.ActivityService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/activities")
public class ActivityController {

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @GetMapping("/active")
    public ApiResponse<List<ActivityResponse>> active() {
        return ApiResponse.ok(activityService.listPublicActive());
    }

    // 别名：/activities/public 与 /activities/active 等价，避免旧前端/外部调用打到不存在的端点导致 500
    @GetMapping("/public")
    public ApiResponse<List<ActivityResponse>> publicActive() {
        return ApiResponse.ok(activityService.listPublicActive());
    }
}
