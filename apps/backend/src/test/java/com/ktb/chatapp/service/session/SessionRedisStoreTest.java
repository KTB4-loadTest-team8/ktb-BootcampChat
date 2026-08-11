package com.ktb.chatapp.service.session;

import com.ktb.chatapp.model.Session;
import com.ktb.chatapp.repository.SessionRepository;
import com.ktb.chatapp.service.SessionMetadata;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Redis 세션 저장소")
class SessionRedisStoreTest {

    private static final String USER_ID = "user-1";
    private static final String SESSION_ID = "session-1";

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private SessionRepository sessionRepository;

    private SessionRedisStore sessionStore;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        sessionStore = new SessionRedisStore(redisTemplate, sessionRepository);
    }

    @Test
    @DisplayName("활성 세션은 남은 만료 시간으로 Redis에 저장한다")
    void save_StoresSessionWithRemainingTtl() {
        Session session = activeSession();
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);

        Session saved = sessionStore.save(session);

        verify(valueOperations).set(eq("session:v1:" + USER_ID), eq(session), ttlCaptor.capture());
        assertThat(saved).isSameAs(session);
        assertThat(ttlCaptor.getValue()).isBetween(Duration.ofMinutes(29), Duration.ofMinutes(30));
        verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Redis에 있는 세션 조회는 MongoDB를 조회하지 않는다")
    void findByUserId_RedisHit_DoesNotQueryMongo() {
        Session session = activeSession();
        when(valueOperations.get("session:v1:" + USER_ID)).thenReturn(session);

        Optional<Session> found = sessionStore.findByUserId(USER_ID);

        assertThat(found).containsSame(session);
        verify(sessionRepository, never()).findByUserId(any());
    }

    @Test
    @DisplayName("Redis에 없는 기존 MongoDB 세션은 한 번만 Redis로 옮긴다")
    void findByUserId_LegacyMongoSession_MigratesToRedis() {
        Session legacySession = activeSession();
        when(valueOperations.get("session:v1:" + USER_ID)).thenReturn(null);
        when(sessionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(legacySession));

        Optional<Session> found = sessionStore.findByUserId(USER_ID);

        assertThat(found).containsSame(legacySession);
        verify(valueOperations).set(eq("session:v1:" + USER_ID), eq(legacySession), any(Duration.class));
        verify(sessionRepository).delete(legacySession);
    }

    @Test
    @DisplayName("로그아웃은 Redis와 남아 있을 수 있는 MongoDB 세션을 함께 삭제한다")
    void delete_MatchingSession_DeletesRedisAndLegacyMongoCopy() {
        Session session = activeSession();
        when(valueOperations.get("session:v1:" + USER_ID)).thenReturn(session);

        sessionStore.delete(USER_ID, SESSION_ID);

        verify(redisTemplate).delete("session:v1:" + USER_ID);
        verify(sessionRepository).deleteByUserId(USER_ID);
    }

    @Test
    @DisplayName("Redis 직렬화는 세션의 만료 시각과 메타데이터를 보존한다")
    void redisSerializer_RoundTripsSession() {
        Session session = activeSession();
        var serializer = GenericJacksonJsonRedisSerializer.builder()
                .enableUnsafeDefaultTyping()
                .build();

        Object restored = serializer.deserialize(serializer.serialize(session));

        assertThat(restored).isInstanceOfSatisfying(Session.class, decoded -> {
            assertThat(decoded.getUserId()).isEqualTo(session.getUserId());
            assertThat(decoded.getSessionId()).isEqualTo(session.getSessionId());
            assertThat(decoded.getExpiresAt()).isEqualTo(session.getExpiresAt());
            assertThat(decoded.getMetadata()).isEqualTo(session.getMetadata());
        });
    }

    private Session activeSession() {
        long now = Instant.now().toEpochMilli();
        return Session.builder()
                .userId(USER_ID)
                .sessionId(SESSION_ID)
                .createdAt(now)
                .lastActivity(now)
                .metadata(new SessionMetadata("agent", "127.0.0.1", "device"))
                .expiresAt(Instant.now().plusSeconds(1800))
                .build();
    }
}
