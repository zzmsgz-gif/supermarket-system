package com.example.supermarket.service;

import com.example.supermarket.common.PageResponse;
import com.example.supermarket.dto.MessageResponse;
import com.example.supermarket.entity.UserMessage;
import com.example.supermarket.repository.UserMessageRepository;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 站内消息中心：注册欢迎、订单动态、会员升级等统一落在这里 */
@Service
public class MessageService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_TITLE = 120;
    private static final int MAX_CONTENT = 500;

    private final UserMessageRepository messageRepository;

    public MessageService(UserMessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    /**
     * 推送一条站内消息。dedupeKey 非空时幂等（同用户同 key 只落一条），
     * 用于支付/发货这类可能被重试或重复触发的动作。
     */
    @Transactional
    public void push(Long userId, String type, String title, String content,
                     String linkView, String linkRef, String dedupeKey) {
        if (userId == null || !StringUtils.hasText(title)) {
            return;
        }
        if (StringUtils.hasText(dedupeKey) && messageRepository.existsByUserIdAndDedupeKey(userId, dedupeKey)) {
            return;
        }
        UserMessage message = new UserMessage();
        message.setUserId(userId);
        message.setType(StringUtils.hasText(type) ? type : UserMessage.TYPE_SYSTEM);
        message.setTitle(truncate(title, MAX_TITLE));
        message.setContent(truncate(content, MAX_CONTENT));
        message.setLinkView(StringUtils.hasText(linkView) ? linkView : null);
        message.setLinkRef(StringUtils.hasText(linkRef) ? linkRef : null);
        message.setDedupeKey(StringUtils.hasText(dedupeKey) ? dedupeKey : null);
        message.setIsRead(UserMessage.UNREAD);
        messageRepository.save(message);
    }

    @Transactional(readOnly = true)
    public PageResponse<MessageResponse> list(Long userId, String type, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<UserMessage> pageData = StringUtils.hasText(type)
                ? messageRepository.findByUserIdAndTypeOrderByCreatedAtDesc(userId, type, pageable)
                : messageRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        List<MessageResponse> items = pageData.getContent().stream()
                .map(MessageResponse::from)
                .collect(Collectors.toList());
        return PageResponse.of(items, safePage, safeSize, pageData.getTotalElements());
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return messageRepository.countByUserIdAndIsRead(userId, UserMessage.UNREAD);
    }

    @Transactional
    public int markAllRead(Long userId) {
        List<UserMessage> unread = messageRepository.findByUserIdAndIsRead(userId, UserMessage.UNREAD);
        unread.forEach(message -> message.setIsRead(UserMessage.READ));
        messageRepository.saveAll(unread);
        return unread.size();
    }

    /** 单条已读（点开消息时调用）；返回是否命中 */
    @Transactional
    public boolean markRead(Long userId, Long messageId) {
        return messageRepository.findById(messageId)
                .filter(message -> message.getUserId().equals(userId))
                .map(message -> {
                    if (message.getIsRead() == null || message.getIsRead() != UserMessage.READ) {
                        message.setIsRead(UserMessage.READ);
                        messageRepository.save(message);
                    }
                    return true;
                })
                .orElse(false);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
