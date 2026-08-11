package com.ktb.chatapp.websocket.socketio.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ktb.chatapp.model.Message;
import com.ktb.chatapp.model.User;
import com.ktb.chatapp.repository.FileRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MessageResponseMapperTest {

    @Test
    void mapToMessageResponse_shouldKeepReaderIdsAndOmitReadAtAndMetadata() throws Exception {
        Message message = Message.builder()
                .id("message-1")
                .roomId("room-1")
                .content("hello")
                .timestamp(LocalDateTime.of(2026, 8, 11, 12, 0))
                .readers(List.of(Message.MessageReader.builder()
                        .userId("user-1")
                        .readAt(LocalDateTime.of(2026, 8, 11, 12, 1))
                        .build()))
                .metadata(Map.of("internal", "value"))
                .reactions(Map.of("👍", Set.of("user-1")))
                .build();

        var response = new MessageResponseMapper(mock(FileRepository.class))
                .mapToMessageResponse(message, User.builder()
                        .id("sender-1")
                        .name("sender")
                        .email("sender@example.com")
                        .build());

        assertThat(response.getReaders()).extracting("userId").containsExactly("user-1");
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(response);
        assertThat(json).contains("userId");
        assertThat(json).doesNotContain("readAt");
        assertThat(json).doesNotContain("metadata");
    }
}
