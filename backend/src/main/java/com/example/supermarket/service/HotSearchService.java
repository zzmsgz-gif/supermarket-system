package com.example.supermarket.service;

import com.example.supermarket.dto.HotSearchRequest;
import com.example.supermarket.dto.HotSearchResponse;
import com.example.supermarket.entity.HotSearch;
import com.example.supermarket.exception.BusinessException;
import com.example.supermarket.exception.ResourceNotFoundException;
import com.example.supermarket.repository.HotSearchRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 首页头部「热搜」词条：运营在后台维护，前台 {@code GET /hot-searches} 只读启用的。
 */
@Service
public class HotSearchService {

    private static final byte ENABLED = 1;
    private static final byte NOT_DELETED = 0;

    private final HotSearchRepository hotSearchRepository;

    public HotSearchService(HotSearchRepository hotSearchRepository) {
        this.hotSearchRepository = hotSearchRepository;
    }

    /* ===== 前台 ===== */

    @Transactional(readOnly = true)
    public List<HotSearchResponse> listEnabled() {
        return hotSearchRepository
                .findByEnabledAndDeletedOrderBySortOrderAscIdAsc(ENABLED, NOT_DELETED)
                .stream()
                .map(HotSearchResponse::from)
                .toList();
    }

    /* ===== 后台 ===== */

    @Transactional(readOnly = true)
    public List<HotSearchResponse> listAll() {
        return hotSearchRepository.findByDeletedOrderBySortOrderAscIdAsc(NOT_DELETED)
                .stream()
                .map(HotSearchResponse::from)
                .toList();
    }

    @Transactional
    public HotSearchResponse create(HotSearchRequest request) {
        String keyword = normalize(request.getKeyword());
        ensureKeywordAvailable(keyword, null);
        HotSearch entity = new HotSearch();
        applyRequest(entity, request, keyword);
        entity.setDeleted(NOT_DELETED);
        return HotSearchResponse.from(hotSearchRepository.save(entity));
    }

    @Transactional
    public HotSearchResponse update(Long id, HotSearchRequest request) {
        HotSearch entity = hotSearchRepository.findById(id)
                .filter(h -> h.getDeleted() == NOT_DELETED)
                .orElseThrow(() -> new ResourceNotFoundException("热搜词不存在"));
        String keyword = normalize(request.getKeyword());
        ensureKeywordAvailable(keyword, id);
        applyRequest(entity, request, keyword);
        return HotSearchResponse.from(hotSearchRepository.save(entity));
    }

    /**
     * 软删（不是物理删）：种子脚本用固定 id + INSERT IGNORE 播默认词，
     * 物理删会让它在下次重启被重新插回来（"删了又复活"）。
     */
    @Transactional
    public void delete(Long id) {
        hotSearchRepository.findById(id)
                .filter(h -> h.getDeleted() == NOT_DELETED)
                .ifPresent(h -> h.setDeleted((byte) 1));
    }

    private void applyRequest(HotSearch entity, HotSearchRequest request, String keyword) {
        entity.setKeyword(keyword);
        String label = request.getLabel() == null ? null : request.getLabel().trim();
        entity.setLabel(label == null || label.isEmpty() ? null : label);
        entity.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        entity.setEnabled((byte) (Boolean.TRUE.equals(request.getEnabled()) ? 1 : 0));
    }

    private String normalize(String raw) {
        return raw == null ? "" : raw.trim();
    }

    /** 同一个词出现两次在首页同一排里很扎眼，直接拒绝（编辑时排除自己） */
    private void ensureKeywordAvailable(String keyword, Long selfId) {
        boolean duplicated = hotSearchRepository.findByKeywordAndDeleted(keyword, NOT_DELETED).stream()
                .anyMatch(h -> selfId == null || !selfId.equals(h.getId()));
        if (duplicated) {
            throw new BusinessException(400, "该搜索词已存在");
        }
    }
}
