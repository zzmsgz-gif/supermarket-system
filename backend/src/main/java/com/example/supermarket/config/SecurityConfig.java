package com.example.supermarket.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.example.supermarket.security.CustomUserDetailsService;
import com.example.supermarket.security.JwtAuthenticationFilter;
import com.example.supermarket.security.MustChangePasswordFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final MustChangePasswordFilter mustChangePasswordFilter;
    private final CustomUserDetailsService userDetailsService;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            MustChangePasswordFilter mustChangePasswordFilter,
            CustomUserDetailsService userDetailsService
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.mustChangePasswordFilter = mustChangePasswordFilter;
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/categories").permitAll()
                        .requestMatchers(HttpMethod.GET, "/products", "/products/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/reviews/products/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/uploads/**", "/api/uploads/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/activities", "/activities/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/announcements", "/announcements/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/banners", "/banners/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/recommendations", "/recommendations/**").permitAll()
                        // 履约选项（自提门店列表 / 可选配送时段）：结算页需要在登录前的落地页可读
                        .requestMatchers(HttpMethod.GET, "/stores", "/stores/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/delivery-slots", "/delivery-slots/**").permitAll()
                        // 限时秒杀 / 协议与隐私正文：游客也要能看到
                        .requestMatchers(HttpMethod.GET, "/flash-sales", "/flash-sales/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/legal-docs", "/legal-docs/**").permitAll()
                        // 首页头部「热搜」词条：游客也要能看到（原先这几条是前端写死的，现已后台可管）
                        .requestMatchers(HttpMethod.GET, "/hot-searches", "/hot-searches/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/dwell", "/dwell/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/dwell").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/index.html", "/swagger-ui/**",
                                "/v3/api-docs", "/v3/api-docs/", "/v3/api-docs/**",
                                "/swagger-resources", "/swagger-resources/**", "/webjars/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/register", "/auth/login").permitAll()
                        // 忘记密码申请：登录不进去的人才用它，必须放行
                        .requestMatchers(HttpMethod.POST, "/auth/password-reset-request").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        // 未登录 / token 失效 → 401（前端据此清 token 并弹登录）
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"code\":40101,\"message\":\"登录已过期，请重新登录\"}");
                        })
                        // 已登录但角色不足 → 403。此前缺这个 handler 会落到 401，
                        // 让前端把「没权限」误判成「登录过期」而把用户登出。
                        .accessDeniedHandler((request, response, deniedException) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"code\":40301,\"message\":\"当前账号没有该操作权限\"}");
                        }))
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // 强制改密必须在 JWT 之后（要先有 SecurityContext 才能判断是谁），拦截 see MustChangePasswordFilter
                .addFilterAfter(mustChangePasswordFilter, JwtAuthenticationFilter.class)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }
}
