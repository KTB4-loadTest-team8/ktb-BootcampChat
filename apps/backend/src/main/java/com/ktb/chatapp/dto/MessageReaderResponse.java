package com.ktb.chatapp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 메시지를 읽은 사용자 식별자 응답.
 *
 * <p>읽음 여부 화면에는 사용자 ID만 필요하므로 저장 모델의 readAt을 응답에 포함하지 않는다.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageReaderResponse {
    private String userId;
}
