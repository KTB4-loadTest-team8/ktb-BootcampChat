package com.ktb.chatapp.service;

import com.ktb.chatapp.model.Message;
import com.ktb.chatapp.repository.MessageRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageReadStatusServiceTest {

    @Mock
    private MessageRepository messageRepository;

    @Test
    void updateReadStatus_usesSingleBulkUpdate() {
        List<String> messageIds = List.of("message-1", "message-2");
        String userId = "user-1";
        when(messageRepository.addReaderToMessages(
                eq(messageIds), eq(userId), any(Message.MessageReader.class)))
                .thenReturn(2L);

        new MessageReadStatusService(messageRepository).updateReadStatus(messageIds, userId);

        ArgumentCaptor<Message.MessageReader> readerCaptor =
                ArgumentCaptor.forClass(Message.MessageReader.class);
        verify(messageRepository).addReaderToMessages(eq(messageIds), eq(userId), readerCaptor.capture());
        assertThat(readerCaptor.getValue().getUserId()).isEqualTo(userId);
        assertThat(readerCaptor.getValue().getReadAt()).isBeforeOrEqualTo(LocalDateTime.now());
        verify(messageRepository, never()).findById(anyString());
        verify(messageRepository, never()).save(any(Message.class));
    }

    @Test
    void updateReadStatus_withEmptyMessageIds_doesNothing() {
        new MessageReadStatusService(messageRepository).updateReadStatus(List.of(), "user-1");

        verifyNoInteractions(messageRepository);
    }
}
