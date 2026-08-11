package com.ktb.chatapp.service;

import com.ktb.chatapp.dto.RoomResponse;
import com.ktb.chatapp.dto.UserResponse;
import com.ktb.chatapp.model.Room;
import com.ktb.chatapp.model.User;
import com.ktb.chatapp.repository.RoomRepository;
import com.ktb.chatapp.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * 사용자와 무관한 채팅방 목록 스냅샷을 생성한다.
 *
 * <p>방 목록의 방/참여자/최근 메시지 수는 모든 사용자에게 공통이다. 사용자별 캐시 키를
 * 사용하면 새 로그인마다 같은 MongoDB 집계를 반복하므로, 이 서비스에서 단일 Redis 키로
 * 공유한다. 사용자별 필드인 {@code isCreator}는 {@link RoomService}에서 응답 직전에 붙인다.</p>
 */
@Service
@RequiredArgsConstructor
public class RoomListSnapshotService {

    public static final String ROOMS_CACHE = "rooms:v3";

    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final RecentMessageCounter recentMessageCounter;

    @Cacheable(cacheNames = ROOMS_CACHE, key = "'all'", sync = true)
    public List<RoomResponse> getRoomSnapshots() {
        List<Room> rooms = roomRepository.findAll();
        Map<String, User> usersById = findUsersByRoomIds(rooms);
        Map<String, Integer> recentMessageCounts = recentMessageCounter.countRecentMessages(
                rooms.stream().map(Room::getId).toList()
        );

        return rooms.stream()
                .map(room -> mapToSnapshot(room, usersById, recentMessageCounts))
                .sorted(Comparator.comparing(
                        RoomResponse::getCreatedAtDateTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private RoomResponse mapToSnapshot(
            Room room,
            Map<String, User> usersById,
            Map<String, Integer> recentMessageCounts
    ) {
        User creator = usersById.get(room.getCreator());

        List<UserResponse> participants = room.getParticipantIds().stream()
                .map(usersById::get)
                .filter(java.util.Objects::nonNull)
                .map(user -> UserResponse.builder()
                        .id(user.getId())
                        .name(user.getName() != null ? user.getName() : "알 수 없음")
                        .email(user.getEmail() != null ? user.getEmail() : "")
                        .build())
                .collect(Collectors.toList());

        return RoomResponse.builder()
                .id(room.getId())
                .name(room.getName() != null ? room.getName() : "제목 없음")
                .hasPassword(room.isHasPassword())
                .creator(creator != null ? UserResponse.builder()
                        .id(creator.getId())
                        .name(creator.getName() != null ? creator.getName() : "알 수 없음")
                        .email(creator.getEmail() != null ? creator.getEmail() : "")
                        .build() : null)
                .participants(participants)
                .createdAtDateTime(room.getCreatedAt() != null ? room.getCreatedAt() : LocalDateTime.now())
                .isCreator(false)
                .recentMessageCount(recentMessageCounts.getOrDefault(room.getId(), 0))
                .build();
    }

    private Map<String, User> findUsersByRoomIds(List<Room> rooms) {
        Set<String> userIds = new HashSet<>();
        for (Room room : rooms) {
            if (room.getCreator() != null) {
                userIds.add(room.getCreator());
            }
            if (room.getParticipantIds() != null) {
                userIds.addAll(room.getParticipantIds());
            }
        }

        if (userIds.isEmpty()) {
            return Map.of();
        }

        Map<String, User> usersById = new HashMap<>();
        userRepository.findAllRoomSummariesById(userIds)
                .forEach(user -> usersById.put(user.getId(), user));
        return usersById;
    }
}
