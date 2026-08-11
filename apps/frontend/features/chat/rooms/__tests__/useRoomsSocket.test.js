import { renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import socketClient from '@/lib/socket/socketClient';
import { useRoomsSocket } from '../useRoomsSocket';

vi.mock('@/lib/socket/socketClient', () => ({
  default: {
    connect: vi.fn(),
  },
}));

const currentUser = {
  token: 'token-1',
  sessionId: 'session-1',
};

const renderRoomsSocket = (socket, overrides = {}) => {
  socketClient.connect.mockResolvedValue(socket);

  return renderHook(() =>
    useRoomsSocket({
      currentUser,
      router: { push: vi.fn() },
      setConnectionStatus: vi.fn(),
      prependRoom: vi.fn(),
      replaceRoom: vi.fn(),
      mergeRoomActivity: vi.fn(),
      ...overrides,
    })
  );
};

const createSocket = () => ({
  connected: true,
  on: vi.fn(),
  off: vi.fn(),
  emit: vi.fn(),
  disconnect: vi.fn(),
});

const handlerFor = (socket, event) =>
  socket.on.mock.calls.find(([registered]) => registered === event)[1];

describe('useRoomsSocket', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('does not emit joinRoomList because the server joins room-list on connect', async () => {
    const socket = createSocket();

    renderRoomsSocket(socket);

    await waitFor(() => {
      expect(socket.on).toHaveBeenCalledWith('connect', expect.any(Function));
    });

    const connectHandler = socket.on.mock.calls.find(([event]) => event === 'connect')[1];
    connectHandler();

    expect(socket.emit).not.toHaveBeenCalledWith('joinRoomList');
  });

  it('does not register roomDeleted without a server-side room delete event', async () => {
    const socket = createSocket();

    renderRoomsSocket(socket);

    await waitFor(() => {
      expect(socket.on).toHaveBeenCalled();
    });

    const registeredEvents = socket.on.mock.calls.map(([event]) => event);
    expect(registeredEvents).not.toContain('roomDeleted');
  });

  it('merges a roomActivity update into the matching room without dropping its other fields', async () => {
    const socket = createSocket();
    const mergeRoomActivity = vi.fn();

    renderRoomsSocket(socket, { mergeRoomActivity });

    await waitFor(() => {
      expect(socket.on).toHaveBeenCalledWith('roomActivity', expect.any(Function));
    });

    handlerFor(socket, 'roomActivity')({ _id: 'room-2', recentMessageCount: 9 });

    expect(mergeRoomActivity).toHaveBeenCalledWith({
      _id: 'room-2',
      recentMessageCount: 9,
    });
  });

  it('ignores a roomActivity payload without a room id', async () => {
    const socket = createSocket();
    const mergeRoomActivity = vi.fn();

    renderRoomsSocket(socket, { mergeRoomActivity });

    await waitFor(() => {
      expect(socket.on).toHaveBeenCalledWith('roomActivity', expect.any(Function));
    });

    handlerFor(socket, 'roomActivity')(undefined);

    expect(mergeRoomActivity).not.toHaveBeenCalled();
  });

  it('routes create and update events to id-based mutations', async () => {
    const socket = createSocket();
    const prependRoom = vi.fn();
    const replaceRoom = vi.fn();

    renderRoomsSocket(socket, { prependRoom, replaceRoom });

    await waitFor(() => {
      expect(socket.on).toHaveBeenCalledWith('roomCreated', expect.any(Function));
    });

    const createdRoom = { _id: 'room-new', name: '새 방' };
    const updatedRoom = { _id: 'room-1', name: '수정된 방' };
    handlerFor(socket, 'roomCreated')(createdRoom);
    handlerFor(socket, 'roomUpdated')(updatedRoom);

    expect(prependRoom).toHaveBeenCalledWith(createdRoom);
    expect(replaceRoom).toHaveBeenCalledWith(updatedRoom);
  });

  it('unsubscribes screen listeners without disconnecting the session socket', async () => {
    const socket = createSocket();
    const { unmount } = renderRoomsSocket(socket);

    await waitFor(() => {
      expect(socket.on).toHaveBeenCalledWith('roomCreated', expect.any(Function));
    });

    unmount();

    expect(socket.off).toHaveBeenCalledWith('roomCreated', expect.any(Function));
    expect(socket.disconnect).not.toHaveBeenCalled();
  });
});
