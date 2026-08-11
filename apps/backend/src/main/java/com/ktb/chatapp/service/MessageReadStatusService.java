package com.ktb.chatapp.service;

import com.ktb.chatapp.model.Message;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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

        var readerInfo = Message.MessageReader.builder()
                .userId(userId)
                .readAt(LocalDateTime.now())
                .build();

        try {
            // 기존 데이터 중 readers가 null인 문서도 기존 로직처럼 빈 배열로 초기화
            Query nullReadersQuery = Query.query(
                    Criteria.where("_id")
                            .in(messageIds)
                            .and("readers").is(null)
            );

            mongoTemplate.updateMulti(
                    nullReadersQuery,
                    new Update().set("readers", new ArrayList<>()),
                    Message.class
            );

            // 해당 사용자가 아직 읽지 않은 메시지에만 읽음 정보 추가
            Query unreadMessagesQuery = Query.query(
                    Criteria.where("_id")
                            .in(messageIds)
                            .and("readers.userId").ne(userId)
            );

            mongoTemplate.updateMulti(
                    unreadMessagesQuery,
                    new Update().push("readers", readerInfo),
                    Message.class
            );

            log.debug("Read status updated for {} messages by user {}",
                    messageIds.size(), userId);

        } catch (Exception e) {
            log.error("Read status update error for user {}", userId, e);
        }
    }
}
