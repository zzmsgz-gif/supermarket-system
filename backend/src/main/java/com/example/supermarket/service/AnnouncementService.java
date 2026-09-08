package com.example.supermarket.service;

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

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<AnnouncementResponse> listEnabled() {
        return announcementRepository
                .findByEnabledAndDeletedOrderBySortOrderAscPublishTimeDesc(ENABLED, NOT_DELETED)
                .stream()
                .map(AnnouncementResponse::from)
                .collect(Collectors.toList());
    }
}
