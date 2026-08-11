import { useCallback, useEffect, useRef } from 'react';
import { Toast } from '@/components/Toast';
import socketClient from '@/lib/socket/socketClient';

export const useReactionHandling = ({ currentUser, messages, setMessages }) => {
  const messagesRef = useRef(messages);
  const pendingReactionsRef = useRef(new Map());
  const currentUserId = currentUser?.id || currentUser?._id;
  useEffect(() => {
    messagesRef.current = messages;
  }, [messages]);

  const updateMessages = useCallback((updater) => {
    setMessages(prevMessages => {
      const nextMessages = updater(prevMessages);
      messagesRef.current = nextMessages;
      return nextMessages;
    });
  }, [setMessages]);

  const handleReactionAdd = useCallback(async (messageId, reaction) => {
    const operationId = Symbol('reaction-add');
    const previousReactions = messagesRef.current.find(
      message => message._id === messageId
    )?.reactions || {};
    pendingReactionsRef.current.set(operationId, previousReactions);

    try {
      if (!socketClient.canSend()) {
        throw new Error('Socket not connected');
      }

      // 낙관적 업데이트
      updateMessages(prevMessages =>
        prevMessages.map(msg => {
          if (msg._id === messageId) {
            const currentReactions = msg.reactions || {};
            const currentUsers = currentReactions[reaction] || [];

            // 중복 추가 방지
            if (!currentUsers.includes(currentUserId)) {
              return {
                ...msg,
                reactions: {
                  ...currentReactions,
                  [reaction]: [...currentUsers, currentUserId]
                }
              };
            }
          }
          return msg;
        })
      );

      await socketClient.sendMessageReaction(messageId, reaction, 'add');
      pendingReactionsRef.current.delete(operationId);

    } catch (error) {
      console.error('Add reaction error:', error);
      Toast.error('리액션 추가에 실패했습니다.');

      // 실패 시 롤백
      const rollbackReactions = pendingReactionsRef.current.get(operationId) || {};
      pendingReactionsRef.current.delete(operationId);
      updateMessages(prevMessages =>
        prevMessages.map(msg =>
          msg._id === messageId ?
          { ...msg, reactions: rollbackReactions } :
          msg
        )
      );
    }
  }, [currentUserId, updateMessages]);

  const handleReactionRemove = useCallback(async (messageId, reaction) => {
    const operationId = Symbol('reaction-remove');
    const previousReactions = messagesRef.current.find(
      message => message._id === messageId
    )?.reactions || {};
    pendingReactionsRef.current.set(operationId, previousReactions);

    try {
      if (!socketClient.canSend()) {
        throw new Error('Socket not connected');
      }

      // 낙관적 업데이트
      updateMessages(prevMessages =>
        prevMessages.map(msg => {
          if (msg._id === messageId) {
            const currentReactions = msg.reactions || {};
            const currentUsers = currentReactions[reaction] || [];
            return {
              ...msg,
              reactions: {
                ...currentReactions,
                [reaction]: currentUsers.filter(id => id !== currentUserId)
              }
            };
          }
          return msg;
        })
      );

      await socketClient.sendMessageReaction(messageId, reaction, 'remove');
      pendingReactionsRef.current.delete(operationId);

    } catch (error) {
      console.error('Remove reaction error:', error);
      Toast.error('리액션 제거에 실패했습니다.');

      // 실패 시 롤백
      const rollbackReactions = pendingReactionsRef.current.get(operationId) || {};
      pendingReactionsRef.current.delete(operationId);
      updateMessages(prevMessages =>
        prevMessages.map(msg =>
          msg._id === messageId ?
          { ...msg, reactions: rollbackReactions } :
          msg
        )
      );
    }
  }, [currentUserId, updateMessages]);

  const handleReactionUpdate = useCallback(({ messageId, reactions }) => {
    updateMessages(prevMessages =>
      prevMessages.map(msg =>
        msg._id === messageId ? { ...msg, reactions } : msg
      )
    );
  }, [updateMessages]);

  return {
    handleReactionAdd,
    handleReactionRemove,
    handleReactionUpdate
  };
};

export default useReactionHandling;
