import React, { useCallback, useMemo, useState } from 'react';
import { LockIcon, GroupIcon } from '@vapor-ui/icons';
import { Button, Text, VStack, HStack } from '@vapor-ui/core';
import * as Table from '@/components/Table';
import { CONNECTION_STATUS } from './useServerConnection';

export const ROOMS_TABLE_HEIGHT = 430;
export const ROOM_TABLE_HEADER_HEIGHT = 44;
export const ROOM_ROW_HEIGHT = 72;
export const ROOM_ROW_OVERSCAN = 10;

const TABLE_COLUMN_WIDTHS = ['40%', '12%', '12%', '21%', '15%'];
const TABLE_HEADINGS = ['채팅방', '참여자', '최근 메시지', '생성일', '액션'];

const TableColumns = () => (
  <Table.ColumnGroup>
    {TABLE_COLUMN_WIDTHS.map((width, index) => (
      <Table.Column key={`${width}-${index}`} style={{ width }} />
    ))}
  </Table.ColumnGroup>
);

const TableHeader = () => (
  <Table.Header>
    <Table.Row style={{ height: `${ROOM_TABLE_HEADER_HEIGHT}px` }}>
      {TABLE_HEADINGS.map((heading) => (
        <Table.Heading key={heading}>{heading}</Table.Heading>
      ))}
    </Table.Row>
  </Table.Header>
);

const SkeletonBar = ({ width }) => (
  <span
    aria-hidden="true"
    style={{
      display: 'block',
      width,
      maxWidth: '100%',
      height: '14px',
      borderRadius: '999px',
      backgroundColor: 'var(--vapor-color-border-normal)',
      opacity: 0.65,
    }}
  />
);

export const RoomsTableSkeleton = () => {
  const visibleRowCount = Math.ceil(
    (ROOMS_TABLE_HEIGHT - ROOM_TABLE_HEADER_HEIGHT) / ROOM_ROW_HEIGHT
  );
  const skeletonWidths = ['70%', '42%', '36%', '78%', '56%'];

  return (
    <div
      className="chat-rooms-table"
      data-testid="rooms-table-skeleton"
      aria-label="채팅방 목록을 불러오는 중..."
      aria-busy="true"
      style={{
        height: `${ROOMS_TABLE_HEIGHT}px`,
        overflow: 'hidden',
        position: 'relative',
        borderRadius: '0.5rem',
        backgroundColor: 'var(--background-normal)',
        border: '1px solid var(--border-color)',
      }}
    >
      <Table.Root style={{ width: '100%', tableLayout: 'fixed' }}>
        <TableColumns />
        <TableHeader />
        <Table.Body>
          {Array.from({ length: visibleRowCount }, (_, rowIndex) => (
            <Table.Row key={rowIndex} style={{ height: `${ROOM_ROW_HEIGHT}px` }}>
              {skeletonWidths.map((width, columnIndex) => (
                <Table.Cell key={columnIndex}>
                  <SkeletonBar width={width} />
                </Table.Cell>
              ))}
            </Table.Row>
          ))}
        </Table.Body>
      </Table.Root>
    </div>
  );
};

const DATE_FORMATTER = new Intl.DateTimeFormat('ko-KR', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
});
const formattedDateCache = new Map();

const getFormattedCreatedAt = (createdAt) => {
  if (!formattedDateCache.has(createdAt)) {
    formattedDateCache.set(createdAt, DATE_FORMATTER.format(new Date(createdAt)));
  }

  return formattedDateCache.get(createdAt);
};

const RoomRow = React.memo(({ room, connectionStatus, onJoinRoom }) => (
  <Table.Row style={{ height: `${ROOM_ROW_HEIGHT}px` }}>
    <Table.Cell>
      <VStack $css={{ gap: '$050', alignItems: 'flex-start' }}>
        <Text
          style={{
            fontWeight: 500,
            maxWidth: '100%',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          {room.name}
        </Text>
        {room.hasPassword && (
          <HStack $css={{ gap: '$050', alignItems: 'center', color: '$warning-100' }}>
            <LockIcon size={16} />
            <Text typography="body3" foreground="warning-100">
              비밀번호 필요
            </Text>
          </HStack>
        )}
      </VStack>
    </Table.Cell>
    <Table.Cell>
      <HStack $css={{ gap: '$050', alignItems: 'center' }}>
        <GroupIcon />
        <Text typography="body2">{room.participants?.length || 0}</Text>
      </HStack>
    </Table.Cell>
    <Table.Cell>
      {room.recentMessageCount > 0 ? room.recentMessageCount : '-'}
    </Table.Cell>
    <Table.Cell>
      <time dateTime={new Date(room.createdAt).toISOString()}>
        {getFormattedCreatedAt(room.createdAt)}
      </time>
    </Table.Cell>
    <Table.Cell>
      <Button
        colorPalette="primary"
        size="md"
        onClick={() => onJoinRoom(room._id)}
        disabled={connectionStatus !== CONNECTION_STATUS.CONNECTED}
        data-testid="join-chat-room-button"
      >
        입장
      </Button>
    </Table.Cell>
  </Table.Row>
), (previous, next) => (
  previous.connectionStatus === next.connectionStatus &&
  previous.onJoinRoom === next.onJoinRoom &&
  previous.room._id === next.room._id &&
  previous.room.name === next.room.name &&
  previous.room.hasPassword === next.room.hasPassword &&
  previous.room.createdAt === next.room.createdAt &&
  previous.room.recentMessageCount === next.room.recentMessageCount &&
  previous.room.participants?.length === next.room.participants?.length
));
RoomRow.displayName = 'RoomRow';

const SpacerRow = ({ height }) => {
  if (height <= 0) return null;

  return (
    <tr aria-hidden="true">
      <td
        colSpan={5}
        style={{ height: `${height}px`, padding: 0, border: 0 }}
      />
    </tr>
  );
};

const RoomsTable = ({
  roomOrder,
  roomsById,
  roomsRevision,
  connectionStatus,
  onJoinRoom,
}) => {
  const [scrollTop, setScrollTop] = useState(0);

  const handleScroll = useCallback((event) => {
    setScrollTop(event.currentTarget.scrollTop);
  }, []);

  const { visibleRooms, startIndex, endIndex } = useMemo(() => {
    const visibleRowCount = Math.ceil(ROOMS_TABLE_HEIGHT / ROOM_ROW_HEIGHT);
    const roomCount = roomOrder?.length || 0;
    const maxFirstVisibleIndex = Math.max(0, roomCount - visibleRowCount);
    const firstVisibleIndex = Math.min(
      Math.floor(scrollTop / ROOM_ROW_HEIGHT),
      maxFirstVisibleIndex
    );
    const nextStartIndex = Math.max(0, firstVisibleIndex - ROOM_ROW_OVERSCAN);
    const nextEndIndex = Math.min(
      roomCount,
      firstVisibleIndex + visibleRowCount + ROOM_ROW_OVERSCAN
    );

    return {
      visibleRooms: (roomOrder?.slice(nextStartIndex, nextEndIndex) || [])
        .map((roomId) => roomsById.get(roomId))
        .filter(Boolean),
      startIndex: nextStartIndex,
      endIndex: nextEndIndex,
    };
  }, [roomOrder, roomsById, roomsRevision, scrollTop]);

  if (!roomOrder || roomOrder.length === 0) return null;

  return (
    <div
      className="chat-rooms-table"
      onScroll={handleScroll}
      style={{
        height: `${ROOMS_TABLE_HEIGHT}px`,
        overflowY: 'auto',
        position: 'relative',
        borderRadius: '0.5rem',
        backgroundColor: 'var(--background-normal)',
        border: '1px solid var(--border-color)',
        scrollBehavior: 'smooth',
        WebkitOverflowScrolling: 'touch',
      }}
    >
      <Table.Root style={{ width: '100%', tableLayout: 'fixed' }}>
        <TableColumns />
        <TableHeader />

        <Table.Body>
          <SpacerRow height={startIndex * ROOM_ROW_HEIGHT} />
          {visibleRooms.map((room) => (
            <RoomRow
              key={room._id}
              room={room}
              connectionStatus={connectionStatus}
              onJoinRoom={onJoinRoom}
            />
          ))}
          <SpacerRow height={(roomOrder.length - endIndex) * ROOM_ROW_HEIGHT} />
        </Table.Body>
      </Table.Root>
    </div>
  );
};

export default RoomsTable;
