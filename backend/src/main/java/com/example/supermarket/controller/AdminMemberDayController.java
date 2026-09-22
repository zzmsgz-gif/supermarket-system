package com.example.supermarket.controller;

import com.example.supermarket.common.ApiResponse;
import com.example.supermarket.dto.MemberDayRequest;
import com.example.supermarket.dto.MemberDayResponse;
import com.example.supermarket.service.MemberDayService;
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
 * 后台「会员日」管理：每月几号消费积分翻倍（可配多个日期）。
 *
 * <p>⚠️ 增删改都会顺带把公告栏里那条「会员日…」的标题/正文重写成与配置一致
 * （见 {@code MemberDayService.syncAnnouncement}），避免文案与配置对不上。
 */
@RestController
@RequestMapping("/admin/member-days")
public class AdminMemberDayController {

    private final MemberDayService memberDayService;

    public AdminMemberDayController(MemberDayService memberDayService) {
        this.memberDayService = memberDayService;
    }

    @GetMapping
    public ApiResponse<List<MemberDayResponse>> list() {
        return ApiResponse.ok(memberDayService.listAll());
    }

    @PostMapping
    public ApiResponse<MemberDayResponse> create(@Valid @RequestBody MemberDayRequest request) {
        return ApiResponse.ok(memberDayService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<MemberDayResponse> update(@PathVariable Long id, @Valid @RequestBody MemberDayRequest request) {
        return ApiResponse.ok(memberDayService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        memberDayService.delete(id);
        return ApiResponse.ok(null);
    }

}
