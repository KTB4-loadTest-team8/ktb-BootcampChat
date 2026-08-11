package com.ktb.chatapp.service;

import com.ktb.chatapp.model.User;
import com.ktb.chatapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Socket.IO handshake에 필요한 최소 사용자 정보를 짧게 캐시한다.
 *
 * <p>JWT와 활성 세션 검증은 매 연결마다 계속 수행한다. 따라서 캐시는 MongoDB의
 * 사용자 존재/이름 조회만 줄이며 인증을 우회하지 않는다.</p>
 */
@Service
@RequiredArgsConstructor
public class SocketUserLookupService {

    public static final String SOCKET_USER_CACHE = "socket-user:v1";

    private final UserRepository userRepository;

    /**
     * 같은 사용자로 동시에 여러 핸드셰이크가 들어와도 캐시 miss 동안 MongoDB를
     * 한 번만 조회한다. RedisCacheConfig에서 null 캐시를 비활성화하므로 별도의
     * unless 조건은 필요하지 않다.
     */
    @Cacheable(cacheNames = SOCKET_USER_CACHE, key = "#userId", sync = true)
    public SocketUserIdentity findById(String userId) {
        return userRepository.findById(userId)
                .map(this::toIdentity)
                .orElse(null);
    }

    @CacheEvict(cacheNames = SOCKET_USER_CACHE, key = "#userId")
    public void evict(String userId) {
        // Annotation-driven eviction only.
    }

    private SocketUserIdentity toIdentity(User user) {
        return new SocketUserIdentity(user.getId(), user.getName());
    }

    public record SocketUserIdentity(String id, String name) {
    }
}
