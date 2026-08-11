import {
  deriveUniqueSortedMessages,
  mergeSortedMessageArrays,
} from '../messages/useMessageList';

const INCOMING_MESSAGE_FLUSH_DELAY_MS = 50;

export const processLoadedRoomMessages = ({
  loadedMessages,
  hasMore,
  isInitialLoad = false,
  processedMessageIds,
  setMessages,
  setHasMoreMessages,
  initialLoadCompletedRef,
}) => {
  if (!Array.isArray(loadedMessages)) {
    throw new Error('Invalid messages format');
  }

  const processedSnapshot = new Set(processedMessageIds.current);
  processedMessageIds.current = deriveUniqueSortedMessages(
    [],
    loadedMessages,
    processedSnapshot
  ).processedMessageIds;

  let nextMessages;
  setMessages(prev => {
    nextMessages = deriveUniqueSortedMessages(prev, loadedMessages, processedSnapshot).messages;
    return nextMessages;
  });
  setHasMoreMessages(hasMore);

  if (isInitialLoad) {
    initialLoadCompletedRef.current = true;
  }

  return nextMessages;
};

export const applyReadReceipts = (messages, { userId, messageIds, timestamp }) =>
  messages.map(msg => {
    if (!messageIds.includes(msg._id)) {
      return msg;
    }

    const alreadyRead = msg.readers?.some(reader =>
      reader.userId === userId || reader._id === userId
    );
    if (alreadyRead) {
      return msg;
    }

    return {
      ...msg,
      readers: [...(msg.readers || []), { userId, readAt: timestamp || new Date() }],
    };
  });

export const createRoomEventHandlers = ({
  mountedRef,
  messageProcessingRef,
  processedMessageIds,
  pendingIncomingMessagesRef = { current: new Map() },
  incomingMessageFlushTimeoutRef = { current: null },
  initialLoadCompletedRef,
  processMessages,
  setRoom,
  setMessages,
  setLoadingMessages,
  setError,
  setHasMoreMessages,
  cleanup,
  logout,
  onReplace,
  handleReactionUpdate,
  showRejectedMessage,
}) => {
  const flushIncomingMessages = () => {
    incomingMessageFlushTimeoutRef.current = null;
    if (!mountedRef.current || pendingIncomingMessagesRef.current.size === 0) return;

    const incomingMessages = Array.from(pendingIncomingMessagesRef.current.values())
      .sort((a, b) => new Date(a.timestamp || 0) - new Date(b.timestamp || 0));
    pendingIncomingMessagesRef.current.clear();

    setMessages(prev => mergeSortedMessageArrays(prev, incomingMessages));
  };

  const enqueueIncomingMessage = (incoming) => {
    pendingIncomingMessagesRef.current.set(incoming._id, incoming);
    if (incomingMessageFlushTimeoutRef.current) return;

    incomingMessageFlushTimeoutRef.current = setTimeout(
      flushIncomingMessages,
      INCOMING_MESSAGE_FLUSH_DELAY_MS
    );
  };

  const handlePreviousMessages = (response) => {
    if (!mountedRef.current || messageProcessingRef.current) return;
    try {
      messageProcessingRef.current = true;
      if (!response || typeof response !== 'object') {
        throw new Error('Invalid response format');
      }
      const { messages: loadedMessages = [], hasMore } = response;
      const isInitialLoad = !initialLoadCompletedRef.current;
      processMessages(loadedMessages, hasMore, isInitialLoad);
      setLoadingMessages(false);
    } catch (error) {
      setLoadingMessages(false);
      setError('메시지 처리 중 오류가 발생했습니다.');
      setHasMoreMessages(false);
    } finally {
      messageProcessingRef.current = false;
    }
  };

  return {
    onParticipantsUpdate: (participants) => {
      if (!mountedRef.current) return;
      setRoom(prev => ({ ...prev, participants: participants || [] }));
    },
    onMessagesRead: (payload) => {
      if (!mountedRef.current) return;
      setMessages(prev => applyReadReceipts(prev, payload));
    },
    onMessage: (incoming) => {
      if (!mountedRef.current) return;
      if (!incoming?._id || processedMessageIds.current.has(incoming._id)) return;
      processedMessageIds.current.add(incoming._id);
      enqueueIncomingMessage(incoming);
    },
    onPreviousMessagesLoaded: handlePreviousMessages,
    onMessageReactionUpdate: (data) => {
      if (!mountedRef.current) return;
      handleReactionUpdate(data);
    },
    onSessionEnded: () => {
      if (!mountedRef.current) return;
      cleanup();
      logout();
      onReplace('/?error=session_expired');
    },
    onError: (error) => {
      if (!mountedRef.current) return;
      console.error('Socket error:', error);
      if (error?.code === 'MESSAGE_REJECTED') {
        showRejectedMessage(error.message || '금칙어가 포함되어 메시지를 전송할 수 없습니다.');
        return;
      }
      setError(error.message || '채팅 연결에 문제가 발생했습니다.');
    },
  };
};
