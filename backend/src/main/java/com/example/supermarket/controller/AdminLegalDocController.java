package com.example.supermarket.controller;

import com.example.supermarket.common.ApiResponse;
import com.example.supermarket.dto.LegalDocRequest;
import com.example.supermarket.dto.LegalDocResponse;
import com.example.supermarket.service.LegalDocService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 后台编辑协议/隐私正文（/admin/** 已在 SecurityConfig 限定 ADMIN） */
@RestController
@RequestMapping("/admin/legal-docs")
public class AdminLegalDocController {

    private final LegalDocService legalDocService;

    public AdminLegalDocController(LegalDocService legalDocService) {
        this.legalDocService = legalDocService;
    }

    @GetMapping
    public ApiResponse<List<LegalDocResponse>> list() {
        return ApiResponse.ok(legalDocService.listAdmin());
    }

    @PutMapping("/{docKey}")
    public ApiResponse<LegalDocResponse> update(@PathVariable String docKey,
                                                @RequestBody LegalDocRequest request) {
        return ApiResponse.ok(legalDocService.update(docKey, request));
    }
}
