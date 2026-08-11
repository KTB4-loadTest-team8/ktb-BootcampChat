package com.ktb.chatapp.service;

import com.ktb.chatapp.config.MongoTestContainer;
import com.ktb.chatapp.config.RedisTestContainer;
import com.ktb.chatapp.model.Message;
import java.time.Instant;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import({MongoTestContainer.class, RedisTestContainer.class})
@TestPropertySource(properties = {
        "spring.data.mongodb.auto-index-creation=true",
        "socketio.enabled=false"
})
class MessageReadStatusServiceIntegrationTest {

    private static final String ROOM_ID = "read-status-room";
    private static final String USER_ID = "read-status-user";

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private MessageReadStatusService messageReadStatusService;

    @BeforeEach
    void setUp() {
        mongoTemplate.getCollection("messages").deleteMany(new Document());
        mongoTemplate.getCollection("messages").insertMany(List.of(
                message("message-null", null),
                messageWithoutReaders("message-missing"),
                message("message-empty", List.of()),
                message("message-already-read", List.of(reader(USER_ID))),
                message("message-read-by-other", List.of(reader("another-user")))
        ));
    }

    @AfterEach
    void tearDown() {
        mongoTemplate.getCollection("messages").deleteMany(new Document());
    }

    @Test
    void updateReadStatus_handlesNullEmptyAndExistingReadersInOneOperation() {
        List<String> messageIds = List.of(
                "message-null",
                "message-missing",
                "message-empty",
                "message-already-read",
                "message-read-by-other");

        messageReadStatusService.updateReadStatus(messageIds, USER_ID);

        assertThat(readerUserIds("message-null")).containsExactly(USER_ID);
        assertThat(readerUserIds("message-missing")).containsExactly(USER_ID);
        assertThat(readerUserIds("message-empty")).containsExactly(USER_ID);
        assertThat(readerUserIds("message-already-read")).containsExactly(USER_ID);
        assertThat(readerUserIds("message-read-by-other"))
                .containsExactly("another-user", USER_ID);

        messageReadStatusService.updateReadStatus(messageIds, USER_ID);

        assertThat(readerUserIds("message-null")).containsExactly(USER_ID);
        assertThat(readerUserIds("message-missing")).containsExactly(USER_ID);
        assertThat(readerUserIds("message-empty")).containsExactly(USER_ID);
        assertThat(readerUserIds("message-already-read")).containsExactly(USER_ID);
        assertThat(readerUserIds("message-read-by-other"))
                .containsExactly("another-user", USER_ID);
    }

    private Document message(String id, Object readers) {
        return baseMessage(id).append("readers", readers);
    }

    private Document messageWithoutReaders(String id) {
        return baseMessage(id);
    }

    private Document baseMessage(String id) {
        return new Document("_id", id)
                .append("room", ROOM_ID)
                .append("sender", "sender-1")
                .append("content", "content");
    }

    private Document reader(String userId) {
        return new Document("userId", userId)
                .append("readAt", java.util.Date.from(Instant.parse("2026-01-01T00:00:00Z")));
    }

    private List<String> readerUserIds(String messageId) {
        Document message = mongoTemplate.getCollection("messages")
                .find(new Document("_id", messageId))
                .first();
        assertThat(message).isNotNull();

        return message.getList("readers", Document.class).stream()
                .map(reader -> reader.getString("userId"))
                .toList();
    }
}
