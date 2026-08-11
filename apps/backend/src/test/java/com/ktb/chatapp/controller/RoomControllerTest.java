package com.ktb.chatapp.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb.chatapp.dto.RoomDetailResponse;
import com.ktb.chatapp.metrics.ChatRoomMetrics;
import com.ktb.chatapp.model.Room;
import com.ktb.chatapp.model.User;
import com.ktb.chatapp.repository.UserRepository;
import com.ktb.chatapp.service.RecentMessageCounter;
import com.ktb.chatapp.service.RoomService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RoomControllerTest {

    @Test
    void getRoomById_shouldUseDetailResponseWithoutRecentMessageCount() throws Exception {
        User creator = User.builder()
                .id("creator-1")
                .name("Creator")
                .email("creator@example.com")
                .build();
        Room room = Room.builder()
                .id("room-1")
                .name("Room 1")
                .creator("creator-1")
                .participantIds(Set.of("creator-1"))
                .createdAt(LocalDateTime.of(2026, 8, 11, 12, 0))
                .build();

        UserRepository userRepository = mock(UserRepository.class);
        RecentMessageCounter recentMessageCounter = mock(RecentMessageCounter.class);
        RoomService roomService = mock(RoomService.class);
        ChatRoomMetrics chatRoomMetrics = mock(ChatRoomMetrics.class);
        when(chatRoomMetrics.start()).thenReturn(
                io.micrometer.core.instrument.Timer.start(new SimpleMeterRegistry())
        );
        when(roomService.findRoomById("room-1")).thenReturn(Optional.of(room));
        when(userRepository.findAllRoomSummariesById(ArgumentMatchers.anySet())).thenReturn(List.of(creator));

        RoomController controller = new RoomController(
                userRepository,
                recentMessageCounter,
                roomService,
                chatRoomMetrics
        );
        Principal principal = () -> "creator@example.com";

        ResponseEntity<?> response = controller.getRoomById("room-1", principal);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isInstanceOf(Map.class);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("data")).isInstanceOf(RoomDetailResponse.class);
        RoomDetailResponse detail = (RoomDetailResponse) body.get("data");
        assertThat(detail.getId()).isEqualTo("room-1");
        assertThat(new ObjectMapper().findAndRegisterModules().writeValueAsString(detail))
                .doesNotContain("recentMessageCount");
        verifyNoInteractions(recentMessageCounter);
        verify(chatRoomMetrics).recordRoomDetail(
                ArgumentMatchers.any(),
                ArgumentMatchers.eq("success")
        );
    }
}
