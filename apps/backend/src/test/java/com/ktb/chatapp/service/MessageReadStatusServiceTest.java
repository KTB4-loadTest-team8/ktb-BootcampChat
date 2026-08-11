package com.ktb.chatapp.service;

import com.ktb.chatapp.model.Message;
import com.mongodb.client.result.UpdateResult;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AggregationUpdate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageReadStatusServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Test
    void updateReadStatus_usesOneAggregationUpdate() {
        List<String> messageIds = List.of("message-1", "message-2");
        String userId = "user-1";
        when(mongoTemplate.updateMulti(
                any(Query.class), any(UpdateDefinition.class), eq(Message.class)))
                .thenReturn(UpdateResult.acknowledged(2L, 2L, null));

        new MessageReadStatusService(mongoTemplate).updateReadStatus(messageIds, userId);

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<UpdateDefinition> updateCaptor = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongoTemplate, times(1)).updateMulti(
                queryCaptor.capture(), updateCaptor.capture(), eq(Message.class));

        assertThat(queryCaptor.getValue().getQueryObject().toJson())
                .contains("message-1", "message-2", "user-1", "$or");
        assertThat(updateCaptor.getValue()).isInstanceOf(AggregationUpdate.class);
        assertThat(updateCaptor.getValue().getUpdateObject().toString())
                .contains("$set", "$ifNull", "$map", "$in", "$concatArrays");
    }

    @Test
    void updateReadStatus_withEmptyMessageIds_doesNothing() {
        new MessageReadStatusService(mongoTemplate).updateReadStatus(List.of(), "user-1");

        verifyNoInteractions(mongoTemplate);
    }
}
