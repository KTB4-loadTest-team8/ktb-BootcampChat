package com.ktb.chatapp.service;

import com.ktb.chatapp.event.RoomUpdatedEvent;
import com.ktb.chatapp.model.Room;
import com.ktb.chatapp.model.User;
import com.ktb.chatapp.repository.RoomRepository;
import com.ktb.chatapp.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock private RoomRepository roomRepository;
    @Mock private UserRepository userRepository;
    @Mock private RecentMessageCounter recentMessageCounter;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private ApplicationEventPublisher eventPublisher;

    @Test
    void getAllRooms_shouldBatchUsersAndRecentMessageCounts() {
        Room olderRoom = room("room-1", "creator-1", Set.of("creator-1", "user-1"), 1);
        Room newerRoom = room("room-2", "creator-2", Set.of("creator-2", "user-1", "user-2"), 2);

        when(roomRepository.findAll()).thenReturn(List.of(olderRoom, newerRoom));
        when(userRepository.findAllById(Set.of("creator-1", "creator-2", "user-1", "user-2")))
                .thenReturn(List.of(
                        user("creator-1"), user("creator-2"), user("user-1"), user("user-2")
                ));
        when(recentMessageCounter.countRecentMessages(List.of("room-1", "room-2")))
                .thenReturn(Map.of("room-1", 3, "room-2", 7));

        var result = new RoomService(
                roomRepository,
                userRepository,
                recentMessageCounter,
                passwordEncoder,
                eventPublisher
        ).getAllRooms("user@example.com");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).extracting("id").containsExactly("room-2", "room-1");
        assertThat(result.getData()).extracting("recentMessageCount").containsExactly(7, 3);
        verify(userRepository).findAllById(Set.of("creator-1", "creator-2", "user-1", "user-2"));
        verify(recentMessageCounter).countRecentMessages(List.of("room-1", "room-2"));
        verify(userRepository, never()).findById(anyString());
        verify(recentMessageCounter, never()).countRecentMessages(anyString());
    }

    @Test
    void joinRoom_shouldReuseResponseForEventAndHttpCaller() {
        Room room = room("room-1", "creator-1", Set.of("creator-1", "user-1"), 1);
        User creator = user("creator-1");
        User participant = user("user-1");

        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(participant));
        when(userRepository.findAllById(Set.of("creator-1", "user-1")))
                .thenReturn(List.of(creator, participant));
        when(recentMessageCounter.countRecentMessages(List.of("room-1")))
                .thenReturn(Map.of("room-1", 3));

        var result = new RoomService(
                roomRepository,
                userRepository,
                recentMessageCounter,
                passwordEncoder,
                eventPublisher
        ).joinRoom("room-1", null, "user@example.com");

        assertThat(result.getId()).isEqualTo("room-1");
        ArgumentCaptor<RoomUpdatedEvent> eventCaptor = ArgumentCaptor.forClass(RoomUpdatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getRoomResponse()).isSameAs(result);
        verify(userRepository).findAllById(Set.of("creator-1", "user-1"));
        verify(recentMessageCounter).countRecentMessages(List.of("room-1"));
        verify(userRepository, never()).findById(anyString());
        verify(recentMessageCounter, never()).countRecentMessages(anyString());
    }

    @Test
    void joinRoom_shouldAddParticipantAtomicallyWithoutSavingWholeRoom() {
        Room initialRoom = room("room-1", "creator-1", Set.of("creator-1"), 1);
        Room updatedRoom = room("room-1", "creator-1", Set.of("creator-1", "user-1"), 1);
        User creator = user("creator-1");
        User participant = user("user-1");

        when(roomRepository.findById("room-1"))
                .thenReturn(Optional.of(initialRoom));
        when(roomRepository.addParticipantAndReturn("room-1", "user-1"))
                .thenReturn(Optional.of(updatedRoom));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(participant));
        when(userRepository.findAllById(Set.of("creator-1", "user-1")))
                .thenReturn(List.of(creator, participant));
        when(recentMessageCounter.countRecentMessages(List.of("room-1")))
                .thenReturn(Map.of("room-1", 3));

        var result = new RoomService(
                roomRepository,
                userRepository,
                recentMessageCounter,
                passwordEncoder,
                eventPublisher
        ).joinRoom("room-1", null, "user@example.com");

        assertThat(result.getParticipantsCount()).isEqualTo(2);
        verify(roomRepository).addParticipantAndReturn("room-1", "user-1");
        verify(roomRepository).findById("room-1");
        verify(roomRepository, never()).save(any(Room.class));
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
