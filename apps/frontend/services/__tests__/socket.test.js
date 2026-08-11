import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SocketService } from '../socket';
import { io } from 'socket.io-client';

vi.mock('socket.io-client', () => ({
  io: vi.fn(),
}));

const createSocket = ({ connected = false } = {}) => ({
  connected,
  active: false,
  auth: null,
  emit: vi.fn(),
  on: vi.fn(),
  off: vi.fn(),
  connect: vi.fn(),
  disconnect: vi.fn(),
  io: {
    on: vi.fn(),
    off: vi.fn(),
    opts: {},
  },
});

const flushPromises = async () => {
  await Promise.resolve();
  await Promise.resolve();
  await Promise.resolve();
};

const getSocketHandler = (socket, event) =>
  socket.on.mock.calls.find(([registeredEvent]) => registeredEvent === event)?.[1];

const getManagerHandler = (socket, event) =>
  socket.io.on.mock.calls.find(([registeredEvent]) => registeredEvent === event)?.[1];

const emitSocketEvent = (socket, event, payload) => {
  if (event === 'connect') socket.connected = true;
  for (const [, handler] of socket.on.mock.calls.filter(([registered]) => registered === event)) {
    handler(payload);
  }
};

describe('socketService', () => {
  let service;

  beforeEach(() => {
    vi.useFakeTimers();
    vi.clearAllMocks();
    process.env.NEXT_PUBLIC_SOCKET_URL = 'http://localhost:5002';
    service = new SocketService();
  });

  afterEach(() => {
    service.disconnect();
    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('rejects a pending connection immediately when disconnected', async () => {
    io.mockReturnValue(createSocket());

    service.connect().catch(() => {});
    const pendingConnection = service.connectionPromise;
    const settledConnection = pendingConnection.then(
      () => 'resolved',
      error => error.message
    );

    service.disconnect();
    await flushPromises();

    await expect(settledConnection).resolves.toBe('Connection disconnected');
    await flushPromises();

    expect(service.connectionPromise).toBeNull();
    expect(service.connectionReject).toBeNull();
    expect(service.connectionTimeout).toBeNull();
  });

  it('registers reconnect lifecycle handlers on the Socket.IO manager', () => {
    const socket = createSocket();
    io.mockReturnValue(socket);

    service.connect().catch(() => {});

    expect(socket.io.on).toHaveBeenCalledWith('reconnect', expect.any(Function));
    expect(socket.io.on).toHaveBeenCalledWith('reconnect_failed', expect.any(Function));
    expect(socket.on).not.toHaveBeenCalledWith('reconnect', expect.any(Function));
    expect(socket.on).not.toHaveBeenCalledWith('reconnect_failed', expect.any(Function));
  });

  it('does not let a stale manager reconnect failure clear a newer socket', async () => {
    const failedSocket = createSocket();
    const liveSocket = createSocket({ connected: true });
    io.mockReturnValueOnce(failedSocket).mockReturnValueOnce(liveSocket);

    const failedConnection = service.connect().catch(error => error.message);
    getSocketHandler(failedSocket, 'connect_error')(new Error('Invalid session'));
    await flushPromises();

    await expect(failedConnection).resolves.toBe('Invalid session');

    const liveConnection = service.connect();
    getSocketHandler(liveSocket, 'connect')();
    await expect(liveConnection).resolves.toBe(liveSocket);

    getManagerHandler(failedSocket, 'reconnect_failed')();
    await flushPromises();

    expect(service.socket).toBe(liveSocket);
    expect(service.connected).toBe(true);
    expect(liveSocket.disconnect).not.toHaveBeenCalled();
  });

  it('keeps the session socket available for retry when connection times out', async () => {
    const socket = createSocket();
    io.mockReturnValue(socket);

    const connection = service.connect().catch(error => error.message);

    await vi.advanceTimersByTimeAsync(30000);
    await flushPromises();

    await expect(connection).resolves.toBe('Connection timeout');
    expect(socket.disconnect).not.toHaveBeenCalled();
    expect(service.socket).toBe(socket);
    expect(service.connected).toBe(false);
  });

  it('reuses the same socket when reconnect is requested during a pending connection', async () => {
    const pendingSocket = createSocket();
    io.mockReturnValueOnce(pendingSocket);

    service.connect().catch(() => {});
    const pendingConnection = service.connectionPromise;
    const settledPendingConnection = pendingConnection.then(
      () => 'resolved',
      error => error.message
    );

    const reconnectAttempt = service.reconnect();
    const settledReconnect = reconnectAttempt.then(
      () => 'resolved',
      error => error.message
    );
    await flushPromises();

    await expect(
      Promise.race([
        settledPendingConnection,
        Promise.resolve('pending'),
      ])
    ).resolves.toBe('Connection disconnected');
    expect(service.connectionPromise).toBeNull();
    expect(service.connectionReject).toBeNull();
    expect(service.connectionTimeout).toBeNull();
    expect(pendingSocket.disconnect).not.toHaveBeenCalled();

    await vi.advanceTimersByTimeAsync(1000);
    await flushPromises();

    expect(io).toHaveBeenCalledTimes(1);
    expect(service.socket).toBe(pendingSocket);
    expect(pendingSocket.connect).toHaveBeenCalledTimes(1);

    emitSocketEvent(pendingSocket, 'connect');
    await expect(settledReconnect).resolves.toBe('resolved');
    expect(service.isReconnecting).toBe(false);
    expect(service.connected).toBe(true);
  });

  it('shares one connection promise across simultaneous callers', async () => {
    const socket = createSocket();
    io.mockReturnValue(socket);

    const first = service.connect({ auth: { token: 'token-1', sessionId: 'session-1' } });
    const second = service.connect({ auth: { token: 'token-1', sessionId: 'session-1' } });

    expect(service.connectionPromise).toBeTruthy();
    expect(io).toHaveBeenCalledTimes(1);

    emitSocketEvent(socket, 'connect');

    await expect(Promise.all([first, second])).resolves.toEqual([socket, socket]);
    expect(io).toHaveBeenCalledTimes(1);
  });

  it('returns the connected session socket without creating a new one', async () => {
    const socket = createSocket();
    io.mockReturnValue(socket);

    const first = service.connect({ auth: { token: 'token-1', sessionId: 'session-1' } });
    emitSocketEvent(socket, 'connect');
    await first;

    await expect(
      service.connect({ auth: { token: 'token-2', sessionId: 'session-1' } })
    ).resolves.toBe(socket);

    expect(io).toHaveBeenCalledTimes(1);
    expect(socket.auth).toEqual({ token: 'token-2', sessionId: 'session-1' });
  });

  it('disconnects the old socket only when the authenticated session changes', async () => {
    const firstSocket = createSocket();
    const secondSocket = createSocket();
    io.mockReturnValueOnce(firstSocket).mockReturnValueOnce(secondSocket);

    const first = service.connect({ auth: { token: 'token-1', sessionId: 'session-1' } });
    emitSocketEvent(firstSocket, 'connect');
    await first;

    const second = service.connect({ auth: { token: 'token-2', sessionId: 'session-2' } });
    emitSocketEvent(secondSocket, 'connect');
    await expect(second).resolves.toBe(secondSocket);

    expect(firstSocket.disconnect).toHaveBeenCalledTimes(1);
    expect(io).toHaveBeenCalledTimes(2);
  });

  it('does not leave transport error reconnect rejections unhandled', async () => {
    const originalReconnect = service.reconnect;
    const consoleLog = vi.spyOn(console, 'log').mockImplementation(() => {});
    service.reconnect = vi.fn(() => Promise.reject(new Error('Reconnect failed')));

    service.handleSocketError({ type: 'TransportError' });
    await flushPromises();

    expect(service.reconnect).toHaveBeenCalledTimes(1);
    expect(consoleLog).toHaveBeenCalledWith('Socket reconnect failed:', 'Reconnect failed');

    service.reconnect = originalReconnect;
    consoleLog.mockRestore();
  });

  it('throws when sending through a disconnected target socket', () => {
    const socket = createSocket({ connected: false });

    expect(() => service.sendOn(socket, 'leaveRoom', 'room-1')).toThrow(
      'Socket is not connected'
    );
    expect(socket.emit).not.toHaveBeenCalled();
  });

  it.each([undefined, null])(
    'throws when sending through a missing target socket: %s',
    (socket) => {
      expect(() => service.sendOn(socket, 'leaveRoom', 'room-1')).toThrow(
        'Socket is not connected'
      );
    }
  );

  it('returns false when trying to send through a disconnected target socket', () => {
    const socket = createSocket({ connected: false });

    expect(service.trySendOn(socket, 'leaveRoom', 'room-1')).toBe(false);
    expect(socket.emit).not.toHaveBeenCalled();
  });

  it.each([undefined, null])(
    'returns false when trying to send through a missing target socket: %s',
    (socket) => {
      expect(service.trySendOn(socket, 'leaveRoom', 'room-1')).toBe(false);
    }
  );

  it('sends through a connected target socket', () => {
    const socket = createSocket({ connected: true });

    service.sendOn(socket, 'leaveRoom', 'room-1');

    expect(socket.emit).toHaveBeenCalledWith('leaveRoom', 'room-1');
    expect(service.trySendOn(socket, 'leaveRoom', 'room-1')).toBe(true);
    expect(socket.emit).toHaveBeenCalledTimes(2);
  });
});
