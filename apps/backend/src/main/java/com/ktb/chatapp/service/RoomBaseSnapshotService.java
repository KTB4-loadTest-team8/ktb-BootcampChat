package com.ktb.chatapp.service;

import com.ktb.chatapp.dto.RoomResponse;
import com.ktb.chatapp.dto.UserResponse;
import com.ktb.chatapp.model.Room;
import com.ktb.chatapp.model.User;
import com.ktb.chatapp.repository.RoomRepository;
import com.ktb.chatapp.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * 참가자 목록과 분리해 변경 빈도가 낮은 방 기본 정보를 캐시한다.
 *
 * <p>참가자 입장/퇴장은 이 캐시를 무효화하지 않으므로, 동시 입장 부하가 방 목록의
 * MongoDB 조회와 최근 메시지 집계를 반복해서 발생시키지 않는다.</p>
 */
@Service
@RequiredArgsConstructor
public class RoomBaseSnapshotService {

    public static final String CACHE_NAME = "rooms:base:v1";
    private static final String CACHE_KEY = "all";

    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final RecentMessageCounter recentMessageCounter;

    @Cacheable(cacheNames = CACHE_NAME, key = "'" + CACHE_KEY + "'", sync = true)
    public List<RoomResponse> getRoomBases() {
        List<Room> rooms = roomRepository.findAll();
        Map<String, User> creatorsById = findCreators(rooms);
        Map<String, Integer> recentMessageCounts = recentMessageCounter.countRecentMessages(
                rooms.stream().map(Room::getId).toList()
        );

        return rooms.stream()
                .map(room -> toBaseResponse(room, creatorsById, recentMessageCounts))
                .toList();
    }

    /** 한 개의 Redis 키만 삭제해 클러스터에서 전체 캐시 스캔을 피한다. */
    @CacheEvict(cacheNames = CACHE_NAME, key = "'" + CACHE_KEY + "'")
    public void evictRoomBases() {
        // Annotation-driven eviction only.
    }

    private RoomResponse toBaseResponse(
            Room room,
            Map<String, User> creatorsById,
            Map<String, Integer> recentMessageCounts
    ) {
        User creator = creatorsById.get(room.getCreator());

        return RoomResponse.builder()
                .id(room.getId())
                .name(room.getName() != null ? room.getName() : "제목 없음")
                .hasPassword(room.isHasPassword())
                .creator(toUserResponse(creator))
                .participants(List.of())
                .createdAtDateTime(
                        room.getCreatedAt() != null ? room.getCreatedAt() : LocalDateTime.now()
                )
                .isCreator(false)
                .recentMessageCount(recentMessageCounts.getOrDefault(room.getId(), 0))
                .build();
    }

    private Map<String, User> findCreators(List<Room> rooms) {
        Set<String> creatorIds = new HashSet<>();
        rooms.stream()
                .map(Room::getCreator)
                .filter(java.util.Objects::nonNull)
                .forEach(creatorIds::add);

        if (creatorIds.isEmpty()) {
            return Map.of();
        }

        Map<String, User> creatorsById = new HashMap<>();
        userRepository.findAllRoomSummariesById(creatorIds)
                .forEach(user -> creatorsById.put(user.getId(), user));
        return creatorsById;
    }

    private UserResponse toUserResponse(User user) {
        if (user == null) {
            return null;
        }
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName() != null ? user.getName() : "알 수 없음")
                .email(user.getEmail() != null ? user.getEmail() : "")
                .build();
    }
}
