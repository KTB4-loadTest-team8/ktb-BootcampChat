package com.ktb.chatapp.service.session;

import com.ktb.chatapp.model.Session;
import com.ktb.chatapp.repository.SessionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis-backed session store.
 *
 * <p>Keeping the active session in Redis removes MongoDB reads and writes from
 * authentication and Socket.IO message handling. The Redis key TTL is the
 * authoritative inactivity timeout. A legacy Mongo session is read only when
 * a Redis key is absent, then moved to Redis once so rollout does not log out
 * currently active users.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "session.redis.enabled", havingValue = "true", matchIfMissing = true)
public class SessionRedisStore implements SessionStore {

    static final String KEY_PREFIX = "session:v1:";

    private final RedisTemplate<String, Object> sessionRedisTemplate;
    private final SessionRepository legacySessionRepository;

    @Override
    public Optional<Session> findByUserId(String userId) {
        Object cached = sessionRedisTemplate.opsForValue().get(key(userId));
        if (cached instanceof Session session) {
            return Optional.of(session);
        }

        if (cached != null) {
            log.warn("Ignoring unexpected Redis session value type for userId: {}", userId);
        }

        return migrateLegacySession(userId);
    }

    @Override
    public Session save(Session session) {
        Duration ttl = remainingTtl(session);
        if (ttl.isZero() || ttl.isNegative()) {
            sessionRedisTemplate.delete(key(session.getUserId()));
            return session;
        }

        sessionRedisTemplate.opsForValue().set(key(session.getUserId()), session, ttl);
        return session;
    }

    @Override
    public void delete(String userId, String sessionId) {
        Session cached = cachedSession(userId);
        if (cached != null) {
            if (sessionId.equals(cached.getSessionId())) {
                deleteEverywhere(userId);
            }
            return;
        }

        legacySessionRepository.findByUserId(userId)
                .filter(session -> sessionId.equals(session.getSessionId()))
                .ifPresent(session -> deleteEverywhere(userId));
    }

    @Override
    public void deleteAll(String userId) {
        deleteEverywhere(userId);
    }

    private Optional<Session> migrateLegacySession(String userId) {
        return legacySessionRepository.findByUserId(userId)
                .filter(this::isStillActive)
                .map(session -> {
                    save(session);
                    legacySessionRepository.delete(session);
                    log.debug("Migrated active session from MongoDB to Redis for userId: {}", userId);
                    return session;
                });
    }

    private boolean isStillActive(Session session) {
        return session.getExpiresAt() != null && session.getExpiresAt().isAfter(Instant.now());
    }

    private Duration remainingTtl(Session session) {
        if (session.getExpiresAt() == null) {
            throw new IllegalArgumentException("Session expiresAt must not be null");
        }
        return Duration.between(Instant.now(), session.getExpiresAt());
    }

    private Session cachedSession(String userId) {
        Object cached = sessionRedisTemplate.opsForValue().get(key(userId));
        return cached instanceof Session session ? session : null;
    }

    private void deleteEverywhere(String userId) {
        sessionRedisTemplate.delete(key(userId));
        // Remove the legacy copy too, otherwise it could be migrated again after logout.
        legacySessionRepository.deleteByUserId(userId);
    }

    private String key(String userId) {
        return KEY_PREFIX + userId;
    }
}
