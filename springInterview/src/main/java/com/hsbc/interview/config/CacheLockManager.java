package com.hsbc.interview.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class CacheLockManager {

    private final Cache<String, AtomicBoolean> lockCache;

    public CacheLockManager() {
        // 缓存配置（最大1000个锁，1分钟过期）
        lockCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(1, TimeUnit.MINUTES)
                .build();
    }

    /**
     * 尝试获取锁
     *
     * @param lockKey 锁的键
     * @return 如果成功获取锁，返回 true；否则返回 false
     */
    public boolean tryLock(String lockKey) {
        AtomicBoolean lock = lockCache.getIfPresent(lockKey);
        if (lock == null) {
            lock = new AtomicBoolean(false);
            AtomicBoolean existingLock = lockCache.asMap().putIfAbsent(lockKey, lock);
            if (existingLock == null) {
                return lock.compareAndSet(false, true);
            }
        }
        return lock.compareAndSet(false, true);
    }

    /**
     * 释放锁
     *
     * @param lockKey 锁的键
     */
    public void unlock(String lockKey) {
        AtomicBoolean lock = lockCache.getIfPresent(lockKey);
        if (lock != null) {
            lock.set(false);
        }
    }
}
