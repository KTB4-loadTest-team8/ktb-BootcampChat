import { act, renderHook } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import axiosInstance from '@/services/axios';
import { useRoomList } from '../useRoomList';
import { CONNECTION_STATUS } from '../useServerConnection';

vi.mock('@/services/axios', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

const roomsResponse = (rooms) => ({ data: { data: rooms } });

const renderRoomList = (overrides = {}) => {
  const dependencies = {
    currentUser: { token: 'token-1' },
    router: { push: vi.fn() },
    connectionStatus: CONNECTION_STATUS.CONNECTED,
    setConnectionStatus: vi.fn(),
    retryCount: 0,
    setRetryCount: vi.fn(),
    isRetrying: false,
    setIsRetrying: vi.fn(),
    getRetryDelay: vi.fn(() => 1000),
    attemptConnection: vi.fn(() => Promise.resolve(true)),
    ...overrides,
  };

  return {
    ...renderHook(() => useRoomList(dependencies)),
    dependencies,
  };
};

describe('useRoomList', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('hydrates the room store from server data without entering a loading state', () => {
    const initialRooms = [
      { _id: 'room-1', name: '서버 렌더링 방' },
      { _id: 'room-2', name: '두 번째 방' },
    ];
    const { result } = renderRoomList({ initialRooms, hasInitialRooms: true });

    expect(result.current.loading).toBe(false);
    expect(result.current.roomOrder).toEqual(['room-1', 'room-2']);
    expect(result.current.roomsById.get('room-1')).toEqual(initialRooms[0]);
    expect(axiosInstance.get).not.toHaveBeenCalled();
  });

  it('starts loading rooms immediately but waits for the health check before exposing them', async () => {
    let resolveHealth;
    const attemptConnection = vi.fn(
      () => new Promise((resolve) => {
        resolveHealth = resolve;
      })
    );
    axiosInstance.get.mockResolvedValue(roomsResponse([{ _id: 'room-1' }]));

    const { result } = renderRoomList({ attemptConnection });
    let fetchPromise;

    act(() => {
      fetchPromise = result.current.fetchRooms();
    });

    expect(axiosInstance.get).toHaveBeenCalledWith('/api/rooms');
    expect(attemptConnection).toHaveBeenCalledTimes(1);
    expect(result.current.roomOrder).toEqual([]);

    await act(async () => {
      resolveHealth(true);
      await fetchPromise;
    });

    expect(result.current.roomOrder).toEqual(['room-1']);
    expect(result.current.roomsById.get('room-1')).toEqual({ _id: 'room-1' });
  });

  it('replaces the list on refresh without leaving the refreshing flag on', async () => {
    axiosInstance.get.mockResolvedValue(roomsResponse([{ _id: 'room-1' }]));

    const { result } = renderRoomList();

    await act(async () => {
      await result.current.refreshRooms();
    });

    expect(result.current.roomOrder).toEqual(['room-1']);
    expect(result.current.roomsById.get('room-1')).toEqual({ _id: 'room-1' });
    expect(result.current.refreshing).toBe(false);
  });

  it('keeps the current list and stays quiet when a silent refresh fails', async () => {
    axiosInstance.get.mockResolvedValueOnce(roomsResponse([{ _id: 'room-1' }]));

    const { result } = renderRoomList();

    await act(async () => {
      await result.current.fetchRooms();
    });

    axiosInstance.get.mockRejectedValueOnce(new Error('SERVER_UNREACHABLE'));

    await act(async () => {
      await result.current.refreshRooms({ silent: true });
    });

    expect(result.current.roomOrder).toEqual(['room-1']);
    expect(result.current.roomsById.get('room-1')).toEqual({ _id: 'room-1' });
    expect(result.current.error).toBeNull();
    expect(result.current.loading).toBe(false);
  });

  it('surfaces a refresh failure when the user asked for it', async () => {
    axiosInstance.get.mockRejectedValue(new Error('SERVER_UNREACHABLE'));

    const { result } = renderRoomList();

    await act(async () => {
      await result.current.refreshRooms();
    });

    expect(result.current.error).toMatchObject({
      title: '채팅방 목록 갱신 실패',
      showRetry: false,
    });
  });

  it('clears a previous error once a refresh succeeds', async () => {
    axiosInstance.get.mockRejectedValueOnce(new Error('SERVER_UNREACHABLE'));

    const { result } = renderRoomList();

    await act(async () => {
      await result.current.refreshRooms();
    });

    expect(result.current.error).not.toBeNull();

    axiosInstance.get.mockResolvedValueOnce(roomsResponse([{ _id: 'room-1' }]));

    await act(async () => {
      await result.current.refreshRooms();
    });

    expect(result.current.error).toBeNull();
    expect(result.current.roomOrder).toEqual(['room-1']);
    expect(result.current.roomsById.get('room-1')).toEqual({ _id: 'room-1' });
  });

  it('updates room events by id without rebuilding the room order', async () => {
    axiosInstance.get.mockResolvedValue(roomsResponse([
      { _id: 'room-1', name: '방1', recentMessageCount: 1 },
      { _id: 'room-2', name: '방2', recentMessageCount: 2 },
    ]));

    const { result } = renderRoomList();

    await act(async () => {
      await result.current.fetchRooms();
    });

    const initialOrder = result.current.roomOrder;
    const untouchedRoom = result.current.roomsById.get('room-1');

    act(() => {
      result.current.mergeRoomActivity({ _id: 'room-2', recentMessageCount: 9 });
    });

    expect(result.current.roomOrder).toBe(initialOrder);
    expect(result.current.roomsById.get('room-1')).toBe(untouchedRoom);
    expect(result.current.roomsById.get('room-2')).toEqual({
      _id: 'room-2',
      name: '방2',
      recentMessageCount: 9,
    });
  });
});
