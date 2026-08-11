package com.ktb.chatapp.repository;

import com.ktb.chatapp.config.MongoTestContainer;
import com.ktb.chatapp.config.RedisTestContainer;
import com.ktb.chatapp.model.Message;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import({MongoTestContainer.class, RedisTestContainer.class})
@TestPropertySource(properties = {
        "spring.data.mongodb.auto-index-creation=true",
        "socketio.enabled=false"
})
class MessageReadStatusRepositoryIntegrationTest {

    private static final String ROOM_ID = "room-read-status-test";
    private static final String USER_ID = "user-read-status-test";

    @Autowired
    private MessageRepository messageRepository;

    @AfterEach
    void tearDown() {
        messageRepository.deleteAll();
    }

    @Test
    void addReaderToMessages_updatesUnreadMessagesAndAvoidsDuplicates() {
        Message unreadMessage = saveMessage(List.of());
        Message alreadyReadMessage = saveMessage(List.of(reader(USER_ID)));
        Message readByAnotherUserMessage = saveMessage(List.of(reader("another-user")));
        List<String> messageIds = List.of(
                unreadMessage.getId(),
                alreadyReadMessage.getId(),
                readByAnotherUserMessage.getId());

        Message.MessageReader newReader = reader(USER_ID);
        long updatedCount = messageRepository.addReaderToMessages(messageIds, USER_ID, newReader);

        assertThat(updatedCount).isEqualTo(2);
        assertThat(readersOf(unreadMessage.getId()))
                .extracting(Message.MessageReader::getUserId)
                .containsExactly(USER_ID);
        assertThat(readersOf(alreadyReadMessage.getId()))
                .extracting(Message.MessageReader::getUserId)
                .containsExactly(USER_ID);
        assertThat(readersOf(readByAnotherUserMessage.getId()))
                .extracting(Message.MessageReader::getUserId)
                .containsExactlyInAnyOrder("another-user", USER_ID);

        long repeatedUpdateCount = messageRepository.addReaderToMessages(messageIds, USER_ID, reader(USER_ID));

        assertThat(repeatedUpdateCount).isZero();
        assertThat(readersOf(unreadMessage.getId())).hasSize(1);
        assertThat(readersOf(readByAnotherUserMessage.getId())).hasSize(2);
    }

    private Message saveMessage(List<Message.MessageReader> readers) {
        return messageRepository.save(Message.builder()
                .roomId(ROOM_ID)
                .senderId("sender-1")
                .content("content")
                .readers(readers)
                .build());
    }

    private List<Message.MessageReader> readersOf(String messageId) {
        return messageRepository.findById(messageId).orElseThrow().getReaders();
    }

    private Message.MessageReader reader(String userId) {
        return Message.MessageReader.builder()
                .userId(userId)
                .readAt(java.time.LocalDateTime.now())
                .build();
    }
}
