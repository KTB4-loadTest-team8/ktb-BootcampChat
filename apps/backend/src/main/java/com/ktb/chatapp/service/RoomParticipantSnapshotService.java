package com.ktb.chatapp.service;

import com.ktb.chatapp.dto.UserResponse;
import com.ktb.chatapp.model.Room;
import com.ktb.chatapp.model.User;
import com.ktb.chatapp.repository.RoomRepository;
import com.ktb.chatapp.repository.UserRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/** 방 참가자처럼 변경 빈도가 높은 목록을 기본 방 정보와 별도 Redis 키로 관리한다. */
@Service
@RequiredArgsConstructor
public class RoomParticipantSnapshotService {

    public static final String CACHE_NAME = "rooms:participants:v1";
    private static final String CACHE_KEY = "all";

    private final RoomRepository roomRepository;
    private final UserRepository userRepository;

    @Cacheable(cacheNames = CACHE_NAME, key = "'" + CACHE_KEY + "'", sync = true)
    public Map<String, List<UserResponse>> getParticipantSnapshots() {
        List<Room> rooms = roomRepository.findAll();
        Set<String> participantIds = rooms.stream()
                .filter(room -> room.getParticipantIds() != null)
                .flatMap(room -> room.getParticipantIds().stream())
                .collect(java.util.stream.Collectors.toSet());

        Map<String, User> usersById = findUsers(participantIds);
        Map<String, List<UserResponse>> participantsByRoomId = new HashMap<>();
        for (Room room : rooms) {
            List<UserResponse> participants = new ArrayList<>();
            if (room.getParticipantIds() != null) {
                room.getParticipantIds().stream()
                        .map(usersById::get)
                        .filter(java.util.Objects::nonNull)
                        .map(this::toUserResponse)
                        .forEach(participants::add);
            }
            participantsByRoomId.put(room.getId(), List.copyOf(participants));
        }
        return Map.copyOf(participantsByRoomId);
    }

    /** 참가자 변경 시 참가자 목록 캐시의 단일 키만 삭제한다. */
    @CacheEvict(cacheNames = CACHE_NAME, key = "'" + CACHE_KEY + "'")
    public void evictParticipantSnapshots() {
        // Annotation-driven eviction only.
    }

    private Map<String, User> findUsers(Set<String> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }

        Map<String, User> usersById = new HashMap<>();
        userRepository.findAllRoomSummariesById(userIds)
                .forEach(user -> usersById.put(user.getId(), user));
        return usersById;
    }

    private UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName() != null ? user.getName() : "알 수 없음")
                .email(user.getEmail() != null ? user.getEmail() : "")
                .build();
    }
}
