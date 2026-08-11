package com.ktb.chatapp.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
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
public class RedisCacheConfig {

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
}
