package com.ktb.chatapp.config;

import com.corundumstudio.socketio.SocketIOServer;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(properties = {
        "socketio.enabled=true",
        "socketio.redis.enabled=true",
        "socketio.server.port=0",
        "spring.data.redis.password="
})
@Import(MongoTestContainer.class)
@Testcontainers
class RedisSocketIOConfigTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8.8.0-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private SocketIOServer socketIOServer;

    @Autowired
    private RedissonClient socketIORedissonClient;

    @Test
    void shouldCreateRedisBackedSocketIoServer() {
        assertNotNull(socketIOServer);
        assertFalse(socketIORedissonClient.isShutdown());
    }
}
