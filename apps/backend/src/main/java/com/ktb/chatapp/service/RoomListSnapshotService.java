package com.ktb.chatapp.service;

import com.ktb.chatapp.dto.RoomResponse;
import com.ktb.chatapp.dto.UserResponse;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 사용자와 무관한 채팅방 목록 스냅샷을 생성한다.
 *
 * <p>변경 빈도가 낮은 방 기본 정보와 입장/퇴장 때 자주 바뀌는 참가자 목록을 서로 다른
 * Redis 키로 조합한다. 사용자별 필드인 {@code isCreator}는 {@link RoomService}에서
 * 응답 직전에 붙인다.</p>
 */
@Service
@RequiredArgsConstructor
public class RoomListSnapshotService {

    private final RoomBaseSnapshotService roomBaseSnapshotService;
    private final RoomParticipantSnapshotService roomParticipantSnapshotService;

    public List<RoomResponse> getRoomSnapshots() {
        Map<String, List<UserResponse>> participantsByRoomId =
                roomParticipantSnapshotService.getParticipantSnapshots();

        return roomBaseSnapshotService.getRoomBases().stream()
                .map(base -> copyWithParticipants(
                        base,
                        participantsByRoomId.getOrDefault(base.getId(), List.of())
                ))
                .sorted(Comparator.comparing(
                        RoomResponse::getCreatedAtDateTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    /** 방 생성 시 두 캐시 키를 모두 무효화한다. */
    public void evictAllSnapshots() {
        roomBaseSnapshotService.evictRoomBases();
        roomParticipantSnapshotService.evictParticipantSnapshots();
    }

    /** 입장/퇴장 시 자주 바뀌는 참가자 캐시 키만 무효화한다. */
    public void evictParticipantSnapshots() {
        roomParticipantSnapshotService.evictParticipantSnapshots();
    }

    private RoomResponse copyWithParticipants(RoomResponse base, List<UserResponse> participants) {
        return RoomResponse.builder()
                .id(base.getId())
                .name(base.getName())
                .hasPassword(base.isHasPassword())
                .creator(base.getCreator())
                .participants(participants)
                .createdAtDateTime(base.getCreatedAtDateTime())
                .isCreator(false)
                .recentMessageCount(base.getRecentMessageCount())
                .build();
    }
}
