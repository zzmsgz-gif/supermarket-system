package com.example.supermarket.controller;

import com.example.supermarket.common.ApiResponse;
import com.example.supermarket.service.MemberDayService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 前台：会员日信息。
 *
 * <p>游客也能看（会员中心的「会员日」说明、以及页面上的提示），
 * ⚠️ 所以路径必须加进 {@code SecurityConfig} 的 permitAll，否则 401。
 */
@RestController
@RequestMapping("/member-days")
public class MemberDayController {

    private final MemberDayService memberDayService;

    public MemberDayController(MemberDayService memberDayService) {
        this.memberDayService = memberDayService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> view() {
        return ApiResponse.ok(memberDayService.publicView());
    }

}
