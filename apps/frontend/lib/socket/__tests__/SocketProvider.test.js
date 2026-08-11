import React from 'react';
import { act, render } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { SocketProvider } from '../SocketProvider';

const renderProvider = (client, session = null) =>
  render(
    <SocketProvider client={client} session={session}>
      <div>child</div>
    </SocketProvider>
  );

describe('SocketProvider', () => {
  it('does not reconnect online without an authenticated session', () => {
    const client = {
      connect: vi.fn(),
      disconnect: vi.fn(),
      isConnected: vi.fn(() => false),
    };

    renderProvider(client);

    act(() => {
      window.dispatchEvent(new Event('online'));
    });

    expect(client.connect).not.toHaveBeenCalled();
  });

  it('reconnects online with auth when a session exists', () => {
    const client = {
      connect: vi.fn().mockResolvedValue({ id: 'socket-1' }),
      disconnect: vi.fn(),
      isConnected: vi.fn(() => false),
    };

    renderProvider(client, {
      token: 'token-1',
      sessionId: 'session-1',
    });

    expect(client.connect).toHaveBeenCalledTimes(1);

    act(() => {
      window.dispatchEvent(new Event('online'));
    });

    expect(client.connect).toHaveBeenCalledTimes(2);
    expect(client.connect).toHaveBeenCalledWith({
      auth: {
        token: 'token-1',
        sessionId: 'session-1',
      },
    });
  });

  it('connects once as soon as an authenticated session is available', () => {
    const client = {
      connect: vi.fn().mockResolvedValue({ id: 'socket-1' }),
      disconnect: vi.fn(),
      isConnected: vi.fn(() => true),
    };

    renderProvider(client, {
      token: 'token-1',
      sessionId: 'session-1',
    });

    expect(client.connect).toHaveBeenCalledTimes(1);
    expect(client.disconnect).not.toHaveBeenCalled();
  });

  it('disconnects when the authenticated session ends', () => {
    const client = {
      connect: vi.fn().mockResolvedValue({ id: 'socket-1' }),
      disconnect: vi.fn(),
      isConnected: vi.fn(() => true),
    };
    const { rerender } = renderProvider(client, {
      token: 'token-1',
      sessionId: 'session-1',
    });

    rerender(
      <SocketProvider client={client} session={null}>
        <div>child</div>
      </SocketProvider>
    );

    expect(client.disconnect).toHaveBeenCalledTimes(1);
  });

  it('does not disconnect merely because the provider unmounts', () => {
    const client = {
      connect: vi.fn().mockResolvedValue({ id: 'socket-1' }),
      disconnect: vi.fn(),
      isConnected: vi.fn(() => true),
    };
    const { unmount } = renderProvider(client, {
      token: 'token-1',
      sessionId: 'session-1',
    });

    unmount();

    expect(client.disconnect).not.toHaveBeenCalled();
  });
});
