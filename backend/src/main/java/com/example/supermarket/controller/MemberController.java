package com.example.supermarket.controller;

import com.example.supermarket.common.ApiResponse;
import com.example.supermarket.common.PageResponse;
import com.example.supermarket.dto.PointLedgerResponse;
import com.example.supermarket.entity.PointLedger;
import com.example.supermarket.security.CurrentUser;
import com.example.supermarket.service.MemberService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/member")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping("/profile")
    public ApiResponse<Map<String, Object>> profile(@AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(memberService.profile(currentUser.getId()));
    }

    @GetMapping("/ledger")
    public ApiResponse<PageResponse<PointLedgerResponse>> ledger(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<PointLedger> pageData = memberService.listLedger(currentUser.getId(), page, size);
        PageResponse<PointLedgerResponse> response = PageResponse.of(
                pageData.getContent().stream().map(PointLedgerResponse::from).collect(Collectors.toList()),
                page, size, pageData.getTotalElements());
        return ApiResponse.ok(response);
    }

    @GetMapping("/levels")
    public ApiResponse<List<Map<String, Object>>> levels() {
        List<Map<String, Object>> list = memberService.allTiers().stream().map(tier -> {
            Map<String, Object> map = new HashMap<>();
            map.put("level", tier.level());
            map.put("name", tier.name());
            map.put("threshold", tier.threshold());
            map.put("rate", tier.rate());
            map.put("nextThreshold", tier.nextThreshold());
            map.put("nextName", tier.nextName());
            return map;
        }).collect(Collectors.toList());
        return ApiResponse.ok(list);
    }
}
