package com.example.supermarket.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 强制改密拦截器 —— 管理员重置过密码（must_change_password=1）的用户，**在后端也必须被拦住**。
 *
 * 背景：这个功能原先只有前端拦截（登录后弹一个不可关闭的改密弹窗）。但前端拦不住的场景很明显：
 * 拿到临时密码的人只要不经过前端、直接调 API，就能不改成正常使用 —— 安全要求等于没落地。
 *
 * 放行白名单只留「唯一出路 + 读自己」：
 *   - POST /auth/change-password  改密（唯一出路）
 *   - GET  /auth/me               前端要靠它渲染、并知道当前处于待改密状态
 *   - POST /auth/logout           允许退出登录
 * 其余请求一律 403 且 code=40302，前端收到该 code 会把不可关闭的改密弹窗拉起来。
 *
 * ⚠️ 判定用的是**每次请求现查的库值**，不是 JWT 里的快照：`JwtAuthenticationFilter` 每个请求都会经
 * `CustomUserDetailsService` 重新载入用户，所以这里的 `CurrentUser` 始终是最新的 ——
 * 改完密码**立刻**恢复可用，不需要重新登录（这条有测试守着，别改成读 JWT 声明）。
 */
@Component
public class MustChangePasswordFilter extends OncePerRequestFilter {

    /** 前端据此识别「待改密」并拉起不可关闭的改密弹窗（见 api/client.js）。 */
    public static final int CODE = 40302;

    private static final Set<String> ALLOWED_PATHS = Set.of(
            "/auth/change-password",
            "/auth/me",
            "/auth/logout"
    );

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (isPendingPasswordChange() && !ALLOWED_PATHS.contains(request.getServletPath())) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":" + CODE
                    + ",\"message\":\"管理员已重置你的密码，请先修改初始密码再继续操作\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isPendingPasswordChange() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUser currentUser)) {
            return false;
        }
        return Byte.valueOf((byte) 1).equals(currentUser.getUser().getMustChangePassword());
    }

}
