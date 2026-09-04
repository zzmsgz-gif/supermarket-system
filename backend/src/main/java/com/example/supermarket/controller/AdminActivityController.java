package com.example.supermarket.controller;

import com.example.supermarket.common.ApiResponse;
import com.example.supermarket.common.PageResponse;
import com.example.supermarket.dto.ActivityResponse;
import com.example.supermarket.dto.AdminActivityRequest;
import com.example.supermarket.service.ActivityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/admin/activities")
public class AdminActivityController {

    private final ActivityService activityService;

    public AdminActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @GetMapping
    public ApiResponse<PageResponse<ActivityResponse>> listActivities(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.ok(activityService.listActivities(page, size, keyword));
    }

    @PostMapping
    public ApiResponse<ActivityResponse> createActivity(@Valid @RequestBody AdminActivityRequest request) {
        return ApiResponse.ok(activityService.createActivity(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ActivityResponse> updateActivity(
            @PathVariable Long id,
            @Valid @RequestBody AdminActivityRequest request
    ) {
        return ApiResponse.ok(activityService.updateActivity(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteActivity(@PathVariable Long id) {
        activityService.deleteActivity(id);
        return ApiResponse.ok();
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<ActivityResponse> updateStatus(
            @PathVariable Long id,
            @RequestBody AdminActivityRequest request
    ) {
        return ApiResponse.ok(activityService.updateStatus(id, request.getStatus()));
    }
}
