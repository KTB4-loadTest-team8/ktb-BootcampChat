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
import org.springframework.scheduling.annotation.Async;
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

    /**
     * 채팅방 입장·메시지 조회 응답과 분리해 읽음 상태를 갱신한다.
     *
     * <p>읽음 상태 저장 실패는 메시지 로딩 실패로 전파되지 않아야 하므로, 이 메서드는
     * bounded executor에서 실행된다. 동기 호출이 필요한 배치·통합 테스트와 별도 운영
     * 경로는 기존 {@link #updateReadStatus(List, String)}를 사용할 수 있다.</p>
     */
    @Async("messageReadStatusTaskExecutor")
    public void updateReadStatusAsync(List<String> messageIds, String userId) {
        updateReadStatus(messageIds, userId);
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
