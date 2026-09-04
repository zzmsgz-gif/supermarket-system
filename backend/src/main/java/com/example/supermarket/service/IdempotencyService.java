package com.example.supermarket.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 轻量级幂等助手：基于客户端传入的 Idempotency-Key，在 TTL 内重复提交直接返回首次结果，
 * 防止重复下单 / 重复支付。
 *
 * 注意：当前为进程内内存实现（重启或集群多实例下不共享）。生产环境应替换为
 * Redis（SET key value NX EX）以跨实例去重并持久化。
 */
@Component
@EnableScheduling
public class IdempotencyService {

    private final Map<String, Entry> store = new ConcurrentHashMap<>();
    private final long ttlMillis;

    public IdempotencyService(@Value("${app.idempotency.ttl-seconds:300}") long ttlSeconds) {
        this.ttlMillis = ttlSeconds * 1000L;
    }

    @SuppressWarnings("unchecked")
    public <T> T execute(String key, Supplier<T> action) {
        if (key == null || key.isBlank()) {
            return action.get();
        }
        Entry existing = store.get(key);
        if (existing != null && System.currentTimeMillis() < existing.expireAt) {
            return (T) existing.value;
        }
        T result = action.get();
        store.put(key, new Entry(result, System.currentTimeMillis() + ttlMillis));
        return result;
    }

    @Scheduled(fixedDelay = 60000)
    public void cleanup() {
        long now = System.currentTimeMillis();
        store.entrySet().removeIf(e -> now >= e.getValue().expireAt);
    }

    private static final class Entry {
        final Object value;
        final long expireAt;

        Entry(Object value, long expireAt) {
            this.value = value;
            this.expireAt = expireAt;
        }
    }
}
