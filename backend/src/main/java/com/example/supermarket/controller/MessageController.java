package com.example.supermarket.controller;

import com.example.supermarket.common.ApiResponse;
import com.example.supermarket.common.PageResponse;
import com.example.supermarket.dto.MessageResponse;
import com.example.supermarket.security.CurrentUser;
import com.example.supermarket.service.MessageService;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 消息中心 */
@RestController
@RequestMapping("/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping
    public ApiResponse<PageResponse<MessageResponse>> list(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size
    ) {
        return ApiResponse.ok(messageService.list(currentUser.getId(), type, page, size));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Object>> unreadCount(@AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(Map.of("count", messageService.unreadCount(currentUser.getId())));
    }

    @PostMapping("/read")
    public ApiResponse<Map<String, Object>> markAllRead(@AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(Map.of("updated", messageService.markAllRead(currentUser.getId())));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<Map<String, Object>> markRead(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(Map.of("read", messageService.markRead(currentUser.getId(), id)));
    }
}
