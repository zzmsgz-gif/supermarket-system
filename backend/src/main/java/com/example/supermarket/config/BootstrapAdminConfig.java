package com.example.supermarket.config;

import com.example.supermarket.entity.SysUser;
import com.example.supermarket.repository.SysUserRepository;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 云端空库首次部署时，通过环境变量引导出第一个管理员账号。
 *
 * <p>背景：注册接口固定把新用户角色设为 USER，而把用户提升为 ADMIN 又需要管理员权限，
 * 空库场景下会形成死锁。本 Runner 在应用启动后按环境变量把指定账号提升/创建为 ADMIN。
 *
 * <ul>
 *   <li>{@code APP_BOOTSTRAP_ADMIN_USERNAME}：要成为管理员的用户名（不设置则什么都不做）。</li>
 *   <li>{@code APP_BOOTSTRAP_ADMIN_PASSWORD}：仅当该用户名不存在时需要，用于创建账号；
 *       若账号已存在则只提升角色，不改动其密码。</li>
 * </ul>
 */
@Configuration
public class BootstrapAdminConfig {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminConfig.class);

    private static final String ROLE_ADMIN = "ADMIN";
    private static final Byte ENABLED = 1;
    private static final Byte NOT_DELETED = 0;

    @Bean
    public CommandLineRunner bootstrapAdmin(
            SysUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.bootstrap.admin-username:}") String adminUsername,
            @Value("${app.bootstrap.admin-password:}") String adminPassword
    ) {
        return args -> {
            String username = adminUsername == null ? "" : adminUsername.trim();
            if (username.isEmpty()) {
                return;
            }

            SysUser existing = userRepository.findByUsernameAndDeleted(username, NOT_DELETED).orElse(null);
            if (existing != null) {
                if (!ROLE_ADMIN.equals(existing.getRole())) {
                    existing.setRole(ROLE_ADMIN);
                    userRepository.save(existing);
                    log.info("[bootstrap-admin] 已把已存在用户 '{}' 提升为 ADMIN", username);
                }
                return;
            }

            if (adminPassword == null || adminPassword.isBlank()) {
                log.warn(
                        "[bootstrap-admin] 用户 '{}' 尚不存在，且未设置 APP_BOOTSTRAP_ADMIN_PASSWORD，跳过创建。"
                                + "请先在前端注册该账号，或直接提供密码由本程序创建。",
                        username
                );
                return;
            }

            SysUser admin = new SysUser();
            admin.setUsername(username);
            admin.setPasswordHash(passwordEncoder.encode(adminPassword));
            admin.setNickname(username);
            admin.setRole(ROLE_ADMIN);
            admin.setStatus(ENABLED);
            admin.setBalance(BigDecimal.ZERO);
            admin.setDeleted(NOT_DELETED);
            userRepository.save(admin);
            log.info("[bootstrap-admin] 已创建初始管理员账号 '{}'", username);
        };
    }
}
