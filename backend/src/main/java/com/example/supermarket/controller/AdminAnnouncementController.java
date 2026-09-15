package com.example.supermarket.controller;

import com.example.supermarket.common.ApiResponse;
import com.example.supermarket.dto.AnnouncementRequest;
import com.example.supermarket.dto.AnnouncementResponse;
import com.example.supermarket.service.AnnouncementService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台公告管理：发布、编辑、启停、删除（软删）。
 */
@RestController
@RequestMapping("/admin/announcements")
public class AdminAnnouncementController {

    private final AnnouncementService announcementService;

    public AdminAnnouncementController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @GetMapping
    public ApiResponse<List<AnnouncementResponse>> list() {
        return ApiResponse.ok(announcementService.listAll());
    }

    @PostMapping
    public ApiResponse<AnnouncementResponse> create(@Valid @RequestBody AnnouncementRequest request) {
        return ApiResponse.ok(announcementService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<AnnouncementResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody AnnouncementRequest request
    ) {
        return ApiResponse.ok(announcementService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        announcementService.delete(id);
        return ApiResponse.ok(null);
    }
}
