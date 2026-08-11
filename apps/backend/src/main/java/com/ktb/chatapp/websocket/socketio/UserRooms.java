package com.ktb.chatapp.websocket.socketio;

import java.util.Set;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Registry for tracking which rooms each user is currently in.
 *
 * <p>When the Socket.IO Redis store is enabled, the room membership set is
 * stored in Redis so all Socket.IO instances see the same state when traffic
 * is distributed across multiple EC2 nodes. Redis Set commands are used
 * directly instead of a read-modify-write JSON value, which keeps join/leave
 * updates atomic and avoids lost updates between nodes.</p>
 *
 * <p>Users can participate in multiple rooms simultaneously.</p>
 */
@Component
@ConditionalOnProperty(name = "socketio.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class UserRooms {

    private static final String USER_ROOM_KEY_PREFIX = "userroom:roomids:";

    private final StringRedisTemplate redisTemplate;
    private final Map<String, Set<String>> localRooms = new ConcurrentHashMap<>();

    @Value("${socketio.redis.enabled:false}")
    private boolean redisEnabled;

    /**
     * Get all room IDs for a user
     *
     * @param userId the user ID
     * @return the set of room IDs the user is currently in, or empty set if not in any room
     */
    public Set<String> get(String userId) {
        if (!redisEnabled) {
            return Set.copyOf(localRooms.getOrDefault(buildKey(userId), Set.of()));
        }

        Set<String> rooms = redisTemplate.opsForSet().members(buildKey(userId));
        return rooms == null ? Set.of() : Set.copyOf(rooms);
    }

    /**
     * Add a room ID for a user
     *
     * @param userId the user ID
     * @param roomId the room ID to add to the user's room set
     */
    public void add(String userId, String roomId) {
        if (!redisEnabled) {
            localRooms.computeIfAbsent(buildKey(userId), key -> ConcurrentHashMap.newKeySet())
                    .add(roomId);
            return;
        }

        redisTemplate.opsForSet().add(buildKey(userId), roomId);
    }

    /**
     * Remove a specific room ID from a user's room set
     *
     * @param userId the user ID
     * @param roomId the room ID to remove
     */
    public void remove(String userId, String roomId) {
        if (!redisEnabled) {
            Set<String> rooms = localRooms.get(buildKey(userId));
            if (rooms != null) {
                rooms.remove(roomId);
                if (rooms.isEmpty()) {
                    localRooms.remove(buildKey(userId), rooms);
                }
            }
            return;
        }

        redisTemplate.opsForSet().remove(buildKey(userId), roomId);
    }

    /**
     * Remove all room associations for a user
     *
     * @param userId the user ID
     */
    public void clear(String userId) {
        if (!redisEnabled) {
            localRooms.remove(buildKey(userId));
            return;
        }

        redisTemplate.delete(buildKey(userId));
    }

    /**
     * Check if a user is in a specific room
     *
     * @param userId the user ID
     * @param roomId the room ID to check
     * @return true if the user is in the room, false otherwise
     */
    public boolean isInRoom(String userId, String roomId) {
        if (!redisEnabled) {
            return localRooms.getOrDefault(buildKey(userId), Set.of()).contains(roomId);
        }

        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(buildKey(userId), roomId));
    }

    private String buildKey(String userId) {
        return USER_ROOM_KEY_PREFIX + userId;
    }
    
    public void removeAllRooms(String userId) {
        clear(userId);
    }
}
