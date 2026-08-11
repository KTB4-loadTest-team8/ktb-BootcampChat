import { useCallback, useEffect, useRef } from 'react';
import socketClient from '@/lib/socket/socketClient';

const FLUSH_DELAY_MS = 75;
const MAX_BATCH_SIZE = 100;
const MESSAGE_SELECTOR = '[data-read-message-id]';

const hasReader = (message, currentUserId) => (
  message?.readers?.some(reader => (
    reader?.userId === currentUserId || reader?._id === currentUserId
  )) ?? false
);

export const useReadReceiptBatching = ({
  containerRef,
  messages = [],
  currentUserId,
  socket,
  connected = false,
}) => {
  const observerRef = useRef(null);
  const observedElementsRef = useRef(new Map());
  const readableMessageIdsRef = useRef(new Set());
  const pendingMessageIdsRef = useRef(new Set());
  const sentMessageIdsRef = useRef(new Set());
  const flushTimeoutRef = useRef(null);
  const socketRef = useRef(socket);
  const connectedRef = useRef(connected);
  const scheduleFlushRef = useRef(() => {});

  useEffect(() => {
    socketRef.current = socket;
    connectedRef.current = connected;
  }, [socket, connected]);

  const flush = useCallback(() => {
    flushTimeoutRef.current = null;

    const activeSocket = socketRef.current;
    if (!connectedRef.current || !activeSocket?.connected) {
      return;
    }

    const batch = Array.from(pendingMessageIdsRef.current)
      .filter(messageId => (
        readableMessageIdsRef.current.has(messageId) &&
        !sentMessageIdsRef.current.has(messageId)
      ))
      .slice(0, MAX_BATCH_SIZE);

    if (batch.length === 0) {
      return;
    }

    batch.forEach(messageId => pendingMessageIdsRef.current.delete(messageId));

    try {
      socketClient.markMessagesAsRead(batch, activeSocket);
      batch.forEach(messageId => sentMessageIdsRef.current.add(messageId));
    } catch (error) {
      batch.forEach(messageId => pendingMessageIdsRef.current.add(messageId));
      console.error('Error marking messages as read:', error);
      return;
    }

    if (pendingMessageIdsRef.current.size > 0) {
      scheduleFlushRef.current();
    }
  }, []);

  const scheduleFlush = useCallback(() => {
    if (flushTimeoutRef.current || pendingMessageIdsRef.current.size === 0) {
      return;
    }

    flushTimeoutRef.current = setTimeout(flush, FLUSH_DELAY_MS);
  }, [flush]);

  useEffect(() => {
    scheduleFlushRef.current = scheduleFlush;
  }, [scheduleFlush]);

  useEffect(() => {
    readableMessageIdsRef.current = new Set(
      messages
        .filter(message => (
          message?._id &&
          message.type !== 'system' &&
          !hasReader(message, currentUserId)
        ))
        .map(message => message._id)
    );

    for (const messageId of pendingMessageIdsRef.current) {
      if (!readableMessageIdsRef.current.has(messageId)) {
        pendingMessageIdsRef.current.delete(messageId);
      }
    }
  }, [messages, currentUserId]);

  useEffect(() => {
    sentMessageIdsRef.current.clear();
    pendingMessageIdsRef.current.clear();
  }, [currentUserId]);

  useEffect(() => {
    const container = containerRef.current;
    if (!container || !currentUserId || typeof IntersectionObserver === 'undefined') {
      return undefined;
    }

    observerRef.current = new IntersectionObserver((entries) => {
      for (const entry of entries) {
        if (!entry.isIntersecting || entry.intersectionRatio < 0.5) {
          continue;
        }

        const messageId = entry.target.dataset.readMessageId;
        if (
          !messageId ||
          !readableMessageIdsRef.current.has(messageId) ||
          sentMessageIdsRef.current.has(messageId)
        ) {
          continue;
        }

        pendingMessageIdsRef.current.add(messageId);
      }

      scheduleFlush();
    }, {
      root: container,
      rootMargin: '0px',
      threshold: 0.5,
    });

    return () => {
      observerRef.current?.disconnect();
      observerRef.current = null;
      observedElementsRef.current.clear();
    };
  }, [containerRef, currentUserId, scheduleFlush]);

  useEffect(() => {
    const container = containerRef.current;
    const observer = observerRef.current;
    if (!container || !observer) {
      return;
    }

    const currentElements = new Map();
    container.querySelectorAll(MESSAGE_SELECTOR).forEach((element) => {
      const messageId = element.dataset.readMessageId;
      if (!messageId) return;

      currentElements.set(messageId, element);
      const previousElement = observedElementsRef.current.get(messageId);
      if (previousElement === element) return;

      if (previousElement) {
        observer.unobserve(previousElement);
      }
      observer.observe(element);
    });

    for (const [messageId, element] of observedElementsRef.current) {
      if (!currentElements.has(messageId)) {
        observer.unobserve(element);
      }
    }

    observedElementsRef.current = currentElements;
  }, [containerRef, messages]);

  useEffect(() => {
    if (connected) {
      scheduleFlush();
    }
  }, [connected, scheduleFlush]);

  useEffect(() => () => {
    if (flushTimeoutRef.current) {
      clearTimeout(flushTimeoutRef.current);
      flushTimeoutRef.current = null;
    }
    pendingMessageIdsRef.current.clear();
  }, []);
};

export default useReadReceiptBatching;
