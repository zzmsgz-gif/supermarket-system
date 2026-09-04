package com.example.supermarket.controller;

import com.example.supermarket.common.ApiResponse;
import com.example.supermarket.dto.DwellRankResponse;
import com.example.supermarket.dto.DwellRequest;
import com.example.supermarket.entity.PageDwell;
import com.example.supermarket.repository.PageDwellRepository;
import com.example.supermarket.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/dwell")
public class DwellController {

    private final PageDwellRepository pageDwellRepository;

    public DwellController(PageDwellRepository pageDwellRepository) {
        this.pageDwellRepository = pageDwellRepository;
    }

    @PostMapping
    public ApiResponse<Void> report(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody DwellRequest request
    ) {
        if (request.getSeconds() == null || request.getSeconds() <= 0 || request.getProductId() == null) {
            return ApiResponse.ok();
        }
        PageDwell dwell = new PageDwell();
        dwell.setProductId(request.getProductId());
        dwell.setUserId(currentUser != null ? currentUser.getId() : null);
        dwell.setSeconds(request.getSeconds());
        dwell.setSource(request.getSource() == null ? "detail" : request.getSource());
        pageDwellRepository.save(dwell);
        return ApiResponse.ok();
    }

    @GetMapping("/rank")
    public ApiResponse<List<DwellRankResponse>> rank(
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit
    ) {
        List<Object[]> rows = pageDwellRepository.findDwellRankRaw(limit);
        List<DwellRankResponse> result = new ArrayList<>();
        for (Object[] row : rows) {
            Long productId = row[0] != null ? ((Number) row[0]).longValue() : null;
            String productName = row[1] != null ? row[1].toString() : "未知商品";
            Long viewCount = row[2] != null ? ((Number) row[2]).longValue() : 0L;
            BigDecimal avgSeconds = row[3] != null ? new BigDecimal(row[3].toString()) : BigDecimal.ZERO;
            String coverUrl = row[4] != null ? row[4].toString() : null;
            result.add(new DwellRankResponse(productId, productName, avgSeconds, viewCount, coverUrl));
        }
        return ApiResponse.ok(result);
    }
}
