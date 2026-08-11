import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import socketClient from '@/lib/socket/socketClient';
import { useReadReceiptBatching } from '../useReadReceiptBatching';

vi.mock('@/lib/socket/socketClient', () => ({
  default: {
    markMessagesAsRead: vi.fn(),
  },
}));

class MockIntersectionObserver {
  static instances = [];

  constructor(callback, options) {
    this.callback = callback;
    this.options = options;
    this.observed = new Set();
    MockIntersectionObserver.instances.push(this);
  }

  observe(element) {
    this.observed.add(element);
  }

  unobserve(element) {
    this.observed.delete(element);
  }

  disconnect() {
    this.observed.clear();
  }
}

describe('useReadReceiptBatching', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.clearAllMocks();
    MockIntersectionObserver.instances = [];
    global.IntersectionObserver = MockIntersectionObserver;
  });

  afterEach(() => {
    vi.useRealTimers();
    delete global.IntersectionObserver;
  });

  it('uses one observer and sends visible unread messages in one batch', () => {
    const container = document.createElement('div');
    const unreadOne = document.createElement('div');
    const unreadTwo = document.createElement('div');
    const alreadyRead = document.createElement('div');
    unreadOne.dataset.readMessageId = 'message-1';
    unreadTwo.dataset.readMessageId = 'message-2';
    alreadyRead.dataset.readMessageId = 'message-3';
    container.append(unreadOne, unreadTwo, alreadyRead);

    const socket = { connected: true };
    const messages = [
      { _id: 'message-1', readers: [] },
      { _id: 'message-2', readers: [] },
      { _id: 'message-3', readers: [{ userId: 'user-1' }] },
    ];

    renderHook(() => useReadReceiptBatching({
      containerRef: { current: container },
      messages,
      currentUserId: 'user-1',
      socket,
      connected: true,
    }));

    expect(MockIntersectionObserver.instances).toHaveLength(1);
    const observer = MockIntersectionObserver.instances[0];
    expect(observer.options.root).toBe(container);
    expect(observer.observed).toEqual(new Set([unreadOne, unreadTwo, alreadyRead]));

    act(() => {
      observer.callback([
        { target: unreadOne, isIntersecting: true, intersectionRatio: 1 },
        { target: unreadTwo, isIntersecting: true, intersectionRatio: 0.5 },
        { target: alreadyRead, isIntersecting: true, intersectionRatio: 1 },
      ]);
      vi.advanceTimersByTime(75);
    });

    expect(socketClient.markMessagesAsRead).toHaveBeenCalledTimes(1);
    expect(socketClient.markMessagesAsRead).toHaveBeenCalledWith(
      ['message-1', 'message-2'],
      socket
    );
  });

  it('keeps pending ids while disconnected and flushes after reconnect', () => {
    const container = document.createElement('div');
    const messageElement = document.createElement('div');
    messageElement.dataset.readMessageId = 'message-1';
    container.append(messageElement);
    const socket = { connected: false };

    const { rerender } = renderHook(
      ({ connected }) => useReadReceiptBatching({
        containerRef: { current: container },
        messages: [{ _id: 'message-1', readers: [] }],
        currentUserId: 'user-1',
        socket,
        connected,
      }),
      { initialProps: { connected: false } }
    );

    const observer = MockIntersectionObserver.instances[0];
    act(() => {
      observer.callback([
        { target: messageElement, isIntersecting: true, intersectionRatio: 1 },
      ]);
      vi.advanceTimersByTime(75);
    });
    expect(socketClient.markMessagesAsRead).not.toHaveBeenCalled();

    socket.connected = true;
    rerender({ connected: true });
    act(() => {
      vi.advanceTimersByTime(75);
    });

    expect(socketClient.markMessagesAsRead).toHaveBeenCalledWith(['message-1'], socket);
  });
});
