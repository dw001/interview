package com.hsbc.interview.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hsbc.interview.entity.Transaction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration
public class CacheConfig {

    @Bean
    public Cache<String, Transaction> mainCache() {
        return Caffeine.newBuilder()
                .maximumSize(10000)
                .build();
    }

    @Bean
    public Cache<String, Set<String>> userIndexCache() {
        return Caffeine.newBuilder()
                .maximumSize(1000)
                .build();
    }

    @Bean
    public Cache<String, Set<String>> merchantIndexCache() {
        return Caffeine.newBuilder()
                .maximumSize(1000)
                .build();
    }
}