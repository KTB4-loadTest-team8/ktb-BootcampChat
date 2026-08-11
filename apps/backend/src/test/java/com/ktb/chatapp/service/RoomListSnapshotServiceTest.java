package com.ktb.chatapp.service;

import com.ktb.chatapp.model.Room;
import com.ktb.chatapp.model.User;
import com.ktb.chatapp.repository.RoomRepository;
import com.ktb.chatapp.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomListSnapshotServiceTest {

    @Mock private RoomRepository roomRepository;
    @Mock private UserRepository userRepository;
    @Mock private RecentMessageCounter recentMessageCounter;

    @Test
    void getRoomSnapshots_shouldBatchUserSummariesAndRecentMessageCounts() {
        Room olderRoom = room("room-1", "creator-1", Set.of("creator-1", "user-1"), 1);
        Room newerRoom = room("room-2", "creator-2", Set.of("creator-2", "user-1", "user-2"), 2);

        when(roomRepository.findAll()).thenReturn(List.of(olderRoom, newerRoom));
        when(userRepository.findAllRoomSummariesById(Set.of("creator-1", "creator-2", "user-1", "user-2")))
                .thenReturn(List.of(
                        user("creator-1"), user("creator-2"), user("user-1"), user("user-2")
                ));
        when(recentMessageCounter.countRecentMessages(List.of("room-1", "room-2")))
                .thenReturn(Map.of("room-1", 3, "room-2", 7));

        var result = new RoomListSnapshotService(roomRepository, userRepository, recentMessageCounter)
                .getRoomSnapshots();

        assertThat(result).extracting("id").containsExactly("room-2", "room-1");
        assertThat(result).extracting("recentMessageCount").containsExactly(7, 3);
        verify(userRepository).findAllRoomSummariesById(Set.of("creator-1", "creator-2", "user-1", "user-2"));
        verify(recentMessageCounter).countRecentMessages(List.of("room-1", "room-2"));
    }

    private static Room room(String id, String creator, Set<String> participants, int createdAtHour) {
        return Room.builder()
                .id(id)
                .name(id)
                .creator(creator)
                .participantIds(participants)
                .createdAt(LocalDateTime.of(2026, 8, 10, createdAtHour, 0))
                .build();
    }

    private static User user(String id) {
        return User.builder()
                .id(id)
                .name(id)
                .email(id + "@example.com")
                .build();
    }
}
