package com.example.supermarket.service;

import com.example.supermarket.common.PageResponse;
import com.example.supermarket.dto.PasswordResetItemResponse;
import com.example.supermarket.dto.PasswordResetResultResponse;
import com.example.supermarket.dto.PasswordResetSubmitRequest;
import com.example.supermarket.dto.PasswordResetSubmitResponse;
import com.example.supermarket.entity.PasswordResetRequest;
import com.example.supermarket.entity.SysUser;
import com.example.supermarket.exception.BusinessException;
import com.example.supermarket.repository.PasswordResetRequestRepository;
import com.example.supermarket.repository.SysUserRepository;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 「忘记密码」= 提交申请 → 管理员核对身份 → 生成一次性临时密码 → 用户首次登录后强制改密。
 *
 * <p>设计取舍（本项目没有邮件 / 短信通道）：
 * <ul>
 *   <li>不做自助重置。任何"填个手机号就能重置"的实现，在没有验证码校验时等于把账号送人。</li>
 *   <li>临时密码只在重置响应里返回一次，库里只存 BCrypt 哈希，管理员事后也查不到。</li>
 *   <li>重置后把 sys_user.must_change_password 置 1，前端在登录后强制弹改密（改完清 0）。</li>
 * </ul>
 */
@Service
public class PasswordResetService {

    private static final byte NOT_DELETED = 0;
    private static final byte MUST_CHANGE = 1;
    private static final int MAX_PAGE_SIZE = 100;

    /** 防账号枚举：这个文案对「账号存在」与「账号不存在」必须一模一样。 */
    private static final String GENERIC_MESSAGE =
            "申请已提交。客服会在 1 个工作日内核对身份，并通过你留下的联系方式告知临时密码。";

    /** 临时密码字符集：剔除 0/O/1/l/I 等易混字符，方便客服口头转述。 */
    private static final char[] PASSWORD_ALPHABET =
            "ABCDEFGHJKMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789".toCharArray();
    private static final int TEMP_PASSWORD_LENGTH = 10;

    private final SecureRandom secureRandom = new SecureRandom();

    private final PasswordResetRequestRepository resetRequestRepository;
    private final SysUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public PasswordResetService(
            PasswordResetRequestRepository resetRequestRepository,
            SysUserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.resetRequestRepository = resetRequestRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 提交找回密码申请。**永远返回同样的成功结果**（防账号枚举）；
     * 账号不存在时不落库，已有未处理申请时也不重复落库。
     */
    @Transactional
    public PasswordResetSubmitResponse submit(PasswordResetSubmitRequest request) {
        String username = request.getUsername() == null ? "" : request.getUsername().trim();
        String contact = request.getContact() == null ? "" : request.getContact().trim();

        userRepository.findByUsernameAndDeleted(username, NOT_DELETED).ifPresent(user -> {
            boolean alreadyPending = resetRequestRepository
                    .existsByUserIdAndStatus(user.getId(), PasswordResetRequest.STATUS_PENDING);
            if (alreadyPending) {
                return;
            }
            PasswordResetRequest entity = new PasswordResetRequest();
            entity.setUserId(user.getId());
            entity.setUsername(user.getUsername());
            entity.setContact(contact);
            entity.setStatus(PasswordResetRequest.STATUS_PENDING);
            resetRequestRepository.save(entity);
        });

        return new PasswordResetSubmitResponse(true, GENERIC_MESSAGE);
    }

    @Transactional(readOnly = true)
    public PageResponse<PasswordResetItemResponse> list(String status, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PasswordResetRequest> pageData = (status == null || status.isBlank())
                ? resetRequestRepository.findAllByOrderByCreatedAtDesc(pageable)
                : resetRequestRepository.findByStatusOrderByCreatedAtDesc(status.trim().toUpperCase(), pageable);

        List<Long> userIds = pageData.getContent().stream()
                .map(PasswordResetRequest::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, SysUser> users = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (SysUser user : userRepository.findAllById(userIds)) {
                users.put(user.getId(), user);
            }
        }

        List<PasswordResetItemResponse> items = pageData.getContent().stream()
                .map(request -> {
                    SysUser user = request.getUserId() == null ? null : users.get(request.getUserId());
                    return PasswordResetItemResponse.from(request,
                            user == null ? null : user.getNickname(),
                            user == null ? null : user.getPhone());
                })
                .collect(Collectors.toList());
        return PageResponse.of(items, safePage, safeSize, pageData.getTotalElements());
    }

    /** 待处理数量：后台菜单红点用。 */
    @Transactional(readOnly = true)
    public long pendingCount() {
        return resetRequestRepository.countByStatus(PasswordResetRequest.STATUS_PENDING);
    }

    /**
     * 管理员重置密码：生成临时密码、写哈希、置强制改密标记、把申请置为已处理。
     * 返回体里的 tempPassword 只此一次，不落库。
     */
    @Transactional
    public PasswordResetResultResponse reset(Long requestId, Long adminId) {
        PasswordResetRequest request = resetRequestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException(404, "申请不存在"));
        if (!PasswordResetRequest.STATUS_PENDING.equals(request.getStatus())) {
            throw new BusinessException(400, "该申请已处理过，不能重复重置");
        }
        if (request.getUserId() == null) {
            throw new BusinessException(400, "该申请未匹配到账号，无法重置");
        }
        SysUser user = userRepository.findById(request.getUserId())
                .filter(u -> !Byte.valueOf((byte) 1).equals(u.getDeleted()))
                .orElseThrow(() -> new BusinessException(400, "账号已不存在或已注销，无法重置"));

        String tempPassword = generateTempPassword();
        user.setPasswordHash(passwordEncoder.encode(tempPassword));
        user.setMustChangePassword(MUST_CHANGE);
        userRepository.save(user);

        request.setStatus(PasswordResetRequest.STATUS_DONE);
        request.setHandledBy(adminId);
        request.setHandledAt(LocalDateTime.now());
        request.setRemark("已重置为临时密码");
        resetRequestRepository.save(request);

        return new PasswordResetResultResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getPhone(),
                tempPassword,
                "临时密码仅显示这一次，请立即通过电话告知用户；用户首次登录后必须修改密码。");
    }

    /** 驳回申请（身份核对不通过 / 重复提交）。 */
    @Transactional
    public PasswordResetItemResponse reject(Long requestId, Long adminId, String remark) {
        PasswordResetRequest request = resetRequestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException(404, "申请不存在"));
        if (!PasswordResetRequest.STATUS_PENDING.equals(request.getStatus())) {
            throw new BusinessException(400, "该申请已处理过");
        }
        request.setStatus(PasswordResetRequest.STATUS_REJECTED);
        request.setHandledBy(adminId);
        request.setHandledAt(LocalDateTime.now());
        request.setRemark(remark == null || remark.isBlank() ? "身份核对未通过" : remark.trim());
        PasswordResetRequest saved = resetRequestRepository.save(request);
        SysUser user = saved.getUserId() == null ? null : userRepository.findById(saved.getUserId()).orElse(null);
        return PasswordResetItemResponse.from(saved,
                user == null ? null : user.getNickname(),
                user == null ? null : user.getPhone());
    }

    private String generateTempPassword() {
        StringBuilder builder = new StringBuilder(TEMP_PASSWORD_LENGTH);
        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            builder.append(PASSWORD_ALPHABET[secureRandom.nextInt(PASSWORD_ALPHABET.length)]);
        }
        return builder.toString();
    }
}
