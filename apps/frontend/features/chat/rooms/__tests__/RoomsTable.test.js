import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import RoomsTable, { ROOM_ROW_HEIGHT, RoomsTableSkeleton } from '../RoomsTable';
import { CONNECTION_STATUS } from '../useServerConnection';

const createRooms = (count) => Array.from({ length: count }, (_, index) => ({
  _id: `room-${index}`,
  name: `Room ${index}`,
  participants: [],
  recentMessageCount: index,
  createdAt: new Date(2026, 0, 1, 0, index % 60).toISOString(),
}));

const createRoomIndex = (count) => {
  const rooms = createRooms(count);
  return {
    roomOrder: rooms.map((room) => room._id),
    roomsById: new Map(rooms.map((room) => [room._id, room])),
  };
};

describe('RoomsTable', () => {
  it('keeps the loading skeleton at the same height as the room table', () => {
    render(<RoomsTableSkeleton />);

    expect(screen.getByTestId('rooms-table-skeleton').style.height).toBe('430px');
    expect(screen.getByText('채팅방')).toBeTruthy();
    expect(screen.getByText('액션')).toBeTruthy();
  });

  it('renders the first room immediately without mounting every room', () => {
    const onJoinRoom = vi.fn();
    render(
      <RoomsTable
        {...createRoomIndex(1000)}
        roomsRevision={0}
        connectionStatus={CONNECTION_STATUS.CONNECTED}
        onJoinRoom={onJoinRoom}
      />
    );

    expect(screen.getByText('Room 0')).toBeTruthy();
    const joinButtons = screen.getAllByTestId('join-chat-room-button');
    expect(joinButtons.length).toBeLessThan(30);
    expect(screen.queryByText('Room 999')).toBeNull();

    fireEvent.click(joinButtons[0]);
    expect(onJoinRoom).toHaveBeenCalledWith('room-0');
  });

  it('moves the rendered window when the list scrolls', () => {
    const { container } = render(
      <RoomsTable
        {...createRoomIndex(1000)}
        roomsRevision={0}
        connectionStatus={CONNECTION_STATUS.CONNECTED}
        onJoinRoom={vi.fn()}
      />
    );

    const scrollContainer = container.querySelector('.chat-rooms-table');
    Object.defineProperty(scrollContainer, 'scrollTop', {
      configurable: true,
      value: ROOM_ROW_HEIGHT * 500,
    });
    fireEvent.scroll(scrollContainer);

    expect(screen.getByText('Room 500')).toBeTruthy();
    expect(screen.queryByText('Room 0')).toBeNull();
    expect(screen.getAllByTestId('join-chat-room-button').length).toBeLessThan(40);
  });

  it('keeps a valid window when a refreshed list becomes shorter', () => {
    const onJoinRoom = vi.fn();
    const { container, rerender } = render(
      <RoomsTable
        {...createRoomIndex(1000)}
        roomsRevision={0}
        connectionStatus={CONNECTION_STATUS.CONNECTED}
        onJoinRoom={onJoinRoom}
      />
    );

    const scrollContainer = container.querySelector('.chat-rooms-table');
    Object.defineProperty(scrollContainer, 'scrollTop', {
      configurable: true,
      value: ROOM_ROW_HEIGHT * 500,
    });
    fireEvent.scroll(scrollContainer);

    rerender(
      <RoomsTable
        {...createRoomIndex(5)}
        roomsRevision={1}
        connectionStatus={CONNECTION_STATUS.CONNECTED}
        onJoinRoom={onJoinRoom}
      />
    );

    expect(screen.getByText('Room 0')).toBeTruthy();
    expect(screen.getByText('Room 4')).toBeTruthy();
  });
});
