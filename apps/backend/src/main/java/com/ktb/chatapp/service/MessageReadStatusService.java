package com.ktb.chatapp.service;

import com.ktb.chatapp.metrics.ChatRoomMetrics;
import com.ktb.chatapp.model.Message;
import io.micrometer.core.instrument.Timer;
import java.time.LocalDateTime;
import java.util.List;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationUpdate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 메시지 읽음 상태 관리 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageReadStatusService {

    private final MongoTemplate mongoTemplate;
    private final ChatRoomMetrics chatRoomMetrics;

    /**
     * 메시지 읽음 상태 업데이트
     *
     * @param messageIds 읽음 상태를 업데이트할 메시지 리스트
     * @param userId 읽은 사용자 ID
     */
    public void updateReadStatus(List<String> messageIds, String userId) {
        if (messageIds.isEmpty()) {
            return;
        }

        Timer.Sample timerSample = chatRoomMetrics.start();
        String metricStatus = "error";

        var readerInfo = Message.MessageReader.builder()
                .userId(userId)
                .readAt(LocalDateTime.now())
                .build();

        try {
            Criteria unreadOrNullReaders = new Criteria().orOperator(
                    Criteria.where("readers").is(null),
                    Criteria.where("readers.userId").ne(userId)
            );
            Query query = Query.query(new Criteria().andOperator(
                    Criteria.where("_id").in(messageIds),
                    unreadOrNullReaders
            ));

            var result = mongoTemplate.updateMulti(
                    query,
                    buildReadStatusUpdate(readerInfo),
                    Message.class
            );

            log.debug("Read status updated for {} of {} messages by user {}",
                    result.getModifiedCount(), messageIds.size(), userId);
            metricStatus = "success";

        } catch (Exception e) {
            log.error("Read status update error for user {}", userId, e);
        } finally {
            chatRoomMetrics.recordReadUpdate(timerSample, metricStatus);
        }
    }

    private AggregationUpdate buildReadStatusUpdate(Message.MessageReader readerInfo) {
        Document readerDocument = new Document("userId", readerInfo.getUserId())
                .append("readAt", readerInfo.getReadAt());

        Document currentReaders = new Document("$ifNull", List.of("$readers", List.of()));
        Document readerUserIds = new Document("$map", new Document("input", "$$currentReaders")
                .append("as", "reader")
                .append("in", "$$reader.userId"));
        Document appendReader = new Document("$concatArrays", List.of(
                "$$currentReaders",
                List.of(readerDocument)
        ));
        Document readersValue = new Document("$let", new Document(
                "vars", new Document("currentReaders", currentReaders)
        ).append("in", new Document("$cond", List.of(
                new Document("$in", List.of(readerInfo.getUserId(), readerUserIds)),
                "$$currentReaders",
                appendReader
        ))));

        AggregationOperation setReaders = context -> new Document(
                "$set", new Document("readers", readersValue)
        );

        return AggregationUpdate.from(List.of(setReaders));
    }
}
