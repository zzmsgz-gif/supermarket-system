package com.example.supermarket.service;

import com.example.supermarket.dto.BannerRequest;
import com.example.supermarket.dto.BannerResponse;
import com.example.supermarket.entity.Banner;
import com.example.supermarket.exception.ResourceNotFoundException;
import com.example.supermarket.repository.BannerRepository;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 首页轮播位管理：管理员维护，前台只读启用项。 */
@Service
public class BannerService {

    private static final byte ENABLED = 1;
    private static final byte NOT_DELETED = 0;

    private final BannerRepository bannerRepository;

    public BannerService(BannerRepository bannerRepository) {
        this.bannerRepository = bannerRepository;
    }

    @Transactional(readOnly = true)
    public List<BannerResponse> listEnabled() {
        return bannerRepository.findByEnabledAndDeletedOrderBySortOrderAscIdAsc(ENABLED, NOT_DELETED)
                .stream().map(BannerResponse::from).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<BannerResponse> listAll() {
        return bannerRepository.findByDeletedOrderBySortOrderAscIdAsc(NOT_DELETED)
                .stream().map(BannerResponse::from).collect(Collectors.toList());
    }

    @Transactional
    public BannerResponse create(BannerRequest request) {
        Banner entity = new Banner();
        applyRequest(entity, request);
        entity.setDeleted(NOT_DELETED);
        return BannerResponse.from(bannerRepository.save(entity));
    }

    @Transactional
    public BannerResponse update(Long id, BannerRequest request) {
        Banner entity = bannerRepository.findById(id)
                .filter(b -> b.getDeleted() == NOT_DELETED)
                .orElseThrow(() -> new ResourceNotFoundException("轮播位不存在"));
        applyRequest(entity, request);
        return BannerResponse.from(bannerRepository.save(entity));
    }

    @Transactional
    public void delete(Long id) {
        bannerRepository.findById(id)
                .filter(b -> b.getDeleted() == NOT_DELETED)
                .ifPresent(b -> b.setDeleted((byte) 1));
    }

    private void applyRequest(Banner entity, BannerRequest request) {
        entity.setImageUrl(request.getImageUrl().trim());
        entity.setLinkProductId(request.getLinkProductId());
        entity.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        entity.setEnabled((byte) (Boolean.TRUE.equals(request.getEnabled()) ? 1 : 0));
    }
}
