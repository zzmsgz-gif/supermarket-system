package com.example.supermarket.service;

import java.util.List;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * 商品读缓存的统一失效入口。
 *
 * <p><b>为什么需要它</b>：{@code productDetail / hotProducts / newProducts} 三个缓存域
 * （TTL 120s，见 {@code CacheConfig.CACHE_NAMES}）装的是**含库存与销量**的商品读模型。
 * 而下单（扣库存 + 加销量）、取消 / 超时关单（还库存）都会立刻改动这两个字段。
 * 不失效缓存的话，用户买完回到列表页看到的还是旧值 —— 而且前端怎么重拉接口都没用，
 * 因为数据源本身就是缓存的（2026-10-06 用户反馈「购买后库存跟销量没正确显示，还是之前的值」）。
 *
 * <p><b>为什么用注入 CacheManager 而不写 {@code @CacheEvict} 注解</b>：库存变动发生在
 * {@code OrderService.deductStocks / returnStocks} 与 {@code OrderCloseService} 这些
 * 私有方法/非代理调用路径里，自调用不走 Spring 代理，注解静默不生效 —— 这类"写了但没生效"
 * 比不写更难查。显式调用没有这个坑。
 *
 * <p><b>为什么清 hotProducts / newProducts</b>：它们是按销量排序的榜单，
 * 销量一变榜单就变了，属于同一份派生数据。
 */
@Component
public class ProductCacheEvictor {

    /** 含库存 / 销量的商品读缓存域。改动 product.stock 或 product.sales 后必须全部失效。 */
    private static final List<String> PRODUCT_READ_CACHES =
            List.of("productDetail", "hotProducts", "newProducts");

    private final CacheManager cacheManager;

    public ProductCacheEvictor(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /**
     * 清空全部商品读缓存。
     *
     * <p>缓存功能被关闭时（{@code app.cache.enabled=false}）{@code getCache} 会返回 null，
     * 这里逐个判空跳过，不让"没开缓存"变成启动或运行期故障。
     */
    public void evictProductReads() {
        for (String name : PRODUCT_READ_CACHES) {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        }
    }
}