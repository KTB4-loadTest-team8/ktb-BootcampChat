package com.ktb.chatapp.config;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * Shared Redis cache configuration for read-heavy API responses.
 *
 * Socket.IO uses Redisson's store separately; this cache manager is for
 * application-level data cached through Spring's @Cacheable annotations.
 */
@Configuration
@EnableCaching
@Slf4j
public class RedisCacheConfig implements CachingConfigurer {

    @Value("${app.cache.rooms.ttl-seconds:10}")
    private long roomsCacheTtlSeconds;

    @Bean
    public GenericJacksonJsonRedisSerializer redisValueSerializer() {
        return GenericJacksonJsonRedisSerializer.builder()
                .enableUnsafeDefaultTyping()
                .build();
    }

    /**
     * Sessions use the same typed JSON representation as the application cache,
     * while their expiry is managed explicitly by {@code SessionRedisStore}.
     */
    @Bean
    public RedisTemplate<String, Object> sessionRedisTemplate(
            RedisConnectionFactory connectionFactory,
            GenericJacksonJsonRedisSerializer redisValueSerializer
    ) {
        var template = new RedisTemplate<String, Object>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(redisValueSerializer);
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public RedisCacheManager redisCacheManager(
            RedisConnectionFactory connectionFactory,
            GenericJacksonJsonRedisSerializer redisValueSerializer
    ) {
        var cacheConfiguration = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(roomsCacheTtlSeconds))
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(redisValueSerializer));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(cacheConfiguration)
                .build();
    }

    /**
     * A stale cache entry must not turn a read-only API request into a 500.
     * Spring treats a swallowed cache-read failure as a cache miss, so the
     * underlying service reloads the value from MongoDB and stores a fresh one.
     */
    @Override
    @Bean
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Redis cache read failed; reloading from source. cache={}, key={}",
                        cache.getName(), key, exception);
                evictBrokenEntry(cache, key);
            }

            @Override
            public void handleCachePutError(
                    RuntimeException exception, Cache cache, Object key, Object value
            ) {
                log.warn("Redis cache write failed; serving uncached response. cache={}, key={}",
                        cache.getName(), key, exception);
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Redis cache eviction failed. cache={}, key={}", cache.getName(), key, exception);
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("Redis cache clear failed. cache={}", cache.getName(), exception);
            }

            private void evictBrokenEntry(Cache cache, Object key) {
                try {
                    cache.evict(key);
                } catch (RuntimeException evictionException) {
                    log.warn("Could not remove broken Redis cache entry. cache={}, key={}",
                            cache.getName(), key, evictionException);
                }
            }
        };
    }
}
