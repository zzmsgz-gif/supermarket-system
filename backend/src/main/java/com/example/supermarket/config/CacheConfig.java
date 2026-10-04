package com.example.supermarket.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 进程内 Caffeine 缓存。默认开启（无需 Redis 等外部进程），热点只读数据
 * （分类/轮播/公告/活动/热搜/门店/配送时段/协议/会员日）走 @Cacheable，TTL 默认 120s。
 *
 * <p>缓存只挡「读多写少、可容忍短暂过期」的展示型数据；商品库存/价格等强一致字段
 * 不进缓存，仍走数据库带条件 UPDATE 保证一致性。TTL 兜底保证后台改完最多 2 分钟生效，
 * 对商超类低频运营内容完全可接受。若需即时生效，可在对应写方法加 @CacheEvict。
 *
 * <p>设置 {@code app.cache.enabled=false} 可整体关闭缓存。
 */
@Configuration
@EnableCaching
@ConditionalOnProperty(name = "app.cache.enabled", havingValue = "true", matchIfMissing = true)
public class CacheConfig {

    /** 缓存名集中登记，确保都以同一 TTL 规格预创建 */
    private static final java.util.Set<String> CACHE_NAMES = java.util.Set.of(
            "categories", "banners", "announcements", "activities",
            "hotSearches", "stores", "deliverySlots", "legalDocs", "memberDay",
            "productDetail", "hotProducts", "newProducts");

    @Bean
    public CacheManager cacheManager(
            @Value("${app.cache.ttl-seconds:120}") long ttlSeconds) {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(ttlSeconds, TimeUnit.SECONDS)
                .maximumSize(1000));
        manager.setCacheNames(CACHE_NAMES);
        return manager;
    }
}
