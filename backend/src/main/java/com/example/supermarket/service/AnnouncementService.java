package com.example.supermarket.service;

import com.example.supermarket.dto.AnnouncementRequest;
import com.example.supermarket.dto.AnnouncementResponse;
import com.example.supermarket.entity.Announcement;
import com.example.supermarket.repository.AnnouncementRepository;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class AnnouncementService {

    private static final byte ENABLED = 1;
    private static final byte NOT_DELETED = 0;

    private final AnnouncementRepository announcementRepository;

    public AnnouncementService(AnnouncementRepository announcementRepository) {
        this.announcementRepository = announcementRepository;
    }

    /* ===== 后台管理 ===== */

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<AnnouncementResponse> listAll() {
        return announcementRepository.findByDeletedOrderBySortOrderAscPublishTimeDesc(NOT_DELETED)
                .stream()
                .map(AnnouncementResponse::from)
                .collect(Collectors.toList());
    }

    @org.springframework.transaction.annotation.Transactional
    public AnnouncementResponse create(AnnouncementRequest request) {
        Announcement entity = new Announcement();
        applyRequest(entity, request);
        entity.setDeleted(NOT_DELETED);
        entity.setPublishTime(java.time.LocalDateTime.now());
        return AnnouncementResponse.from(announcementRepository.save(entity));
    }

    @org.springframework.transaction.annotation.Transactional
    public AnnouncementResponse update(Long id, AnnouncementRequest request) {
        Announcement entity = announcementRepository.findById(id)
                .filter(a -> a.getDeleted() == NOT_DELETED)
                .orElseThrow(() -> new com.example.supermarket.exception.ResourceNotFoundException("公告不存在"));
        applyRequest(entity, request);
        return AnnouncementResponse.from(announcementRepository.save(entity));
    }

    @org.springframework.transaction.annotation.Transactional
    public void delete(Long id) {
        announcementRepository.findById(id)
                .filter(a -> a.getDeleted() == NOT_DELETED)
                .ifPresent(a -> a.setDeleted((byte) 1));
    }

    private void applyRequest(Announcement entity, AnnouncementRequest request) {
        entity.setTitle(request.getTitle().trim());
        entity.setContent(request.getContent().trim());
        String type = request.getType() == null ? "NOTICE" : request.getType().trim();
        if (!List.of("NOTICE", "ACTIVITY", "SERVICE", "WARNING").contains(type)) type = "NOTICE";
        entity.setType(type);
        entity.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        entity.setEnabled((byte) (Boolean.TRUE.equals(request.getEnabled()) ? 1 : 0));
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<AnnouncementResponse> listEnabled() {
        return announcementRepository
                .findByEnabledAndDeletedOrderBySortOrderAscPublishTimeDesc(ENABLED, NOT_DELETED)
                .stream()
                .map(AnnouncementResponse::from)
                .collect(Collectors.toList());
    }
}
