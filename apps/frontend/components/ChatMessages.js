import React from 'react';
import { Spinner, Text, VStack } from '@vapor-ui/core';
import SystemMessage from './SystemMessage';
import FileMessage from './FileMessage';
import UserMessage from './UserMessage';
import { useInfiniteScroll } from '../hooks/useInfiniteScroll';
import { useAutoScroll } from '../hooks/useAutoScroll';

const NOOP = () => {};
const MESSAGE_WRAPPER_STYLE = {
  contentVisibility: 'auto',
  containIntrinsicSize: '1px 96px',
};

const MessageRow = React.memo(({
  msg,
  currentUser,
  currentUserId,
  room,
  onReactionAdd,
  onReactionRemove,
}) => {
  if (!msg) return null;

  const MessageComponent = {
    system: SystemMessage,
    file: FileMessage,
  }[msg.type] || UserMessage;
  const senderId = msg.sender?._id || msg.sender?.id || msg.sender;
  const isMine = msg.type !== 'system' && Boolean(
    currentUserId && senderId === currentUserId
  );

  return (
    <div
      style={MESSAGE_WRAPPER_STYLE}
    >
      <MessageComponent
        currentUser={currentUser}
        room={room}
        onReactionAdd={onReactionAdd}
        onReactionRemove={onReactionRemove}
        msg={msg}
        content={msg.content}
        isMine={msg.type !== 'system' ? isMine : undefined}
        isStreaming={msg.type === 'ai' ? (msg.isStreaming || false) : undefined}
      />
    </div>
  );
});
MessageRow.displayName = 'MessageRow';

const LoadingIndicator = React.memo(() => (
  <div className="loading-messages">
    <Spinner size="md" colorPalette="primary" aria-label="이전 메시지 로딩 중" />
    <span className="text-secondary text-sm">이전 메시지를 불러오는 중...</span>
  </div>
));
LoadingIndicator.displayName = 'LoadingIndicator';

const MessageHistoryEnd = React.memo(() => (
  <div className="text-center p-2 mb-4" data-testid="message-history-end">
    <Text typography="body2" foreground="hint-100">더 이상 불러올 메시지가 없습니다.</Text>
  </div>
));
MessageHistoryEnd.displayName = 'MessageHistoryEnd';

const EmptyMessages = React.memo(() => (
  <div className="empty-messages">
    <Text typography="body1">아직 메시지가 없습니다.</Text>
    <Text typography="body2" foreground="hint-100">첫 메시지를 보내보세요!</Text>
  </div>
));
EmptyMessages.displayName = 'EmptyMessages';

const ChatMessages = ({
  messages = [],
  currentUser = null,
  room = null,
  loadingMessages = false,
  hasMoreMessages = true,
  onReactionAdd = NOOP,
  onReactionRemove = NOOP,
  onLoadMore = NOOP,
}) => {
  // 무한 스크롤 훅
  const { sentinelRef } = useInfiniteScroll(
    onLoadMore,
    hasMoreMessages,
    loadingMessages
  );

  // 자동 스크롤 훅 (스크롤 복원 기능 포함)
  const { containerRef } = useAutoScroll(
    messages,
    currentUser?._id || currentUser?.id,
    loadingMessages,
    100 // 하단 100px 이내면 자동 스크롤
  );
  const currentUserId = currentUser?._id || currentUser?.id;

  const allMessages = Array.isArray(messages) ? messages : [];

  return (
    <VStack
      ref={containerRef}
      className="h-full overflow-y-auto overflow-x-hidden scroll-smooth [overflow-scrolling:touch]"
      $css={{
        gap: '$200',
        padding: '$300',
      }}
      role="log"
      aria-live="polite"
      aria-atomic="false"
      data-testid="chat-messages-container"
    >
      {/* Sentinel 요소 - 스크롤 맨 위에 배치하여 위로 스크롤 시 이전 메시지 로드 */}
      {hasMoreMessages && (
        <div
          ref={sentinelRef}
          style={{
            height: '20px',
            margin: '10px 0',
            display: 'flex',
            justifyContent: 'center',
            alignItems: 'center'
          }}
        >
          {loadingMessages && <LoadingIndicator />}
        </div>
      )}

      {!hasMoreMessages && messages.length > 0 && (
        <MessageHistoryEnd />
      )}

      {allMessages.length === 0 ? (
        <EmptyMessages />
      ) : (
        allMessages.map((msg, idx) => (
          <MessageRow
            key={msg?._id || `msg-${idx}`}
            msg={msg}
            currentUser={currentUser}
            currentUserId={currentUserId}
            room={room}
            onReactionAdd={onReactionAdd}
            onReactionRemove={onReactionRemove}
          />
        ))
      )}
    </VStack>
  );
};

ChatMessages.displayName = 'ChatMessages';

export default React.memo(ChatMessages);
