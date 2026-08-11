import React from 'react';
import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import ChatMessages from '../ChatMessages';

const { userMessageRender } = vi.hoisted(() => ({
  userMessageRender: vi.fn(),
}));

vi.mock('../../hooks/useInfiniteScroll', () => ({
  useInfiniteScroll: () => ({ sentinelRef: { current: null } }),
}));

vi.mock('../../hooks/useAutoScroll', () => ({
  useAutoScroll: () => ({
    containerRef: { current: null },
    scrollToBottom: vi.fn(),
    isNearBottom: true,
  }),
}));

vi.mock('../SystemMessage', () => ({
  default: ({ msg }) => React.createElement('div', { 'data-testid': 'message' }, msg.content),
}));

vi.mock('../FileMessage', () => ({
  default: ({ msg }) => React.createElement('div', { 'data-testid': 'message' }, msg.content),
}));

vi.mock('../UserMessage', () => ({
  default: ({ msg }) => {
    userMessageRender(msg._id);
    return React.createElement('div', { 'data-testid': 'message' }, msg.content);
  },
}));

describe('ChatMessages', () => {
  it('renders only the appended row when the message list grows', () => {
    const currentUser = { id: 'me' };
    const firstMessage = {
      _id: 'message-1',
      content: 'first',
      timestamp: '2026-06-20T11:00:00.000Z',
      sender: { _id: 'other' },
    };
    const secondMessage = {
      _id: 'message-2',
      content: 'second',
      timestamp: '2026-06-20T11:01:00.000Z',
      sender: { _id: 'other' },
    };
    const { rerender } = render(
      React.createElement(ChatMessages, {
        messages: [firstMessage],
        currentUser,
        hasMoreMessages: false,
      })
    );

    userMessageRender.mockClear();
    rerender(
      React.createElement(ChatMessages, {
        messages: [firstMessage, secondMessage],
        currentUser,
        hasMoreMessages: false,
      })
    );

    expect(userMessageRender).toHaveBeenCalledTimes(1);
    expect(userMessageRender).toHaveBeenCalledWith('message-2');
  });

  it('renders the ordered message state without copying or re-sorting it', () => {
    const messages = [
      {
        _id: 'early',
        content: 'early message',
        timestamp: '2026-06-20T11:00:00.000Z',
        sender: { _id: 'other' },
      },
      {
        _id: 'late',
        content: 'late message',
        timestamp: '2026-06-20T12:00:00.000Z',
        sender: { _id: 'other' },
      },
    ];
    const originalOrder = messages.map((message) => message._id);

    render(
      React.createElement(ChatMessages, {
        messages,
        currentUser: { id: 'me' },
        hasMoreMessages: false,
      })
    );

    expect(screen.getAllByTestId('message').map((node) => node.textContent)).toEqual([
      'early message',
      'late message',
    ]);
    expect(messages.map((message) => message._id)).toEqual(originalOrder);
  });

  it('keeps optimized message wrappers discoverable in the rendered DOM', () => {
    render(
      React.createElement(ChatMessages, {
        messages: [
          {
            _id: 'message-1',
            content: 'discoverable message',
            timestamp: '2026-06-20T11:00:00.000Z',
            sender: { _id: 'other' },
          },
        ],
        currentUser: { id: 'me' },
        hasMoreMessages: true,
        loadingMessages: true,
      })
    );

    const message = screen.getByText('discoverable message');
    const optimizedWrapper = message.closest('[style]');

    expect(message).toBeInTheDocument();
    expect(optimizedWrapper).toHaveStyle({
      contentVisibility: 'auto',
      containIntrinsicSize: '1px 96px',
    });
    expect(screen.getByText('이전 메시지를 불러오는 중...')).toBeInTheDocument();
  });
});
