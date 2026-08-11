# 채팅방 응답 payload 경량화 전달사항

4단계 백엔드 변경으로 채팅 화면의 응답 크기와 MongoDB 조회 필드를 줄였습니다. 프론트 코드는 이번 작업에서 수정하지 않았습니다.

## 메시지 응답

- `metadata` 필드는 더 이상 `message`와 `previousMessagesLoaded` 응답에 포함되지 않습니다.
- 파일 메시지는 기존처럼 `file` 객체의 `filename`, `originalname`, `mimetype`, `size`를 사용합니다.
- `readers` 항목은 `{ userId }` 목록으로 유지하며, 저장 모델의 `readAt`은 응답에서 제외됩니다. 현재 프론트 읽음 표시 로직은 `userId`만 사용합니다.
- 값이 없거나 비어 있는 `sender`, `file`, `aiType`, `readers`, `reactions` 등의 필드는 응답에서 생략될 수 있습니다. 프론트에서는 기존처럼 optional chaining 또는 기본값을 사용해야 합니다.

## 방·참가자 응답

- REST 방 목록의 `participants` 배열은 기존 필드명을 유지하지만 목록 화면에 필요한 `id`만 포함합니다. 따라서 현재처럼 `participants.length`로 참가자 수를 표시하면 됩니다.
- REST 방 상세 조회와 Socket.IO 참가자 갱신에서는 기존과 동일하게 `id`, `name`, `email`, `profileImage`가 제공됩니다.
- 방 비밀번호, 암호화 이메일, 활동 시간 등 내부 사용자 필드는 조회·응답하지 않습니다.

## 확인이 필요한 후속 작업

- 별도 클라이언트가 메시지 `metadata` 또는 독자의 `readAt`을 사용한다면 해당 사용처를 `file` 또는 `userId` 기반으로 전환해야 합니다.
