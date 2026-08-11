import React, { useEffect, useRef } from 'react';
import { ErrorCircleIcon, NetworkIcon, RefreshOutlineIcon } from '@vapor-ui/icons';
import { Button, Text, Badge, Callout, Box, VStack, HStack } from '@vapor-ui/core';
import { useAuth } from '@/contexts/AuthContext';
import { useRoomsSocket } from './useRoomsSocket';
import {
  useServerConnection,
  CONNECTION_STATUS,
} from './useServerConnection';
import { useRoomList } from './useRoomList';
import RoomsTable, { ROOMS_TABLE_HEIGHT, RoomsTableSkeleton } from './RoomsTable';
import ConnectionErrorBanner from '@/components/ConnectionErrorBanner';

const STATUS_CONFIG = {
  [CONNECTION_STATUS.CHECKING]: { label: "연결 확인 중...", color: "warning" },
  [CONNECTION_STATUS.CONNECTING]: { label: "연결 중...", color: "warning" },
  [CONNECTION_STATUS.CONNECTED]: { label: "연결됨", color: "success" },
  [CONNECTION_STATUS.DISCONNECTED]: { label: "연결 끊김", color: "danger" },
  [CONNECTION_STATUS.ERROR]: { label: "연결 오류", color: "danger" },
};

const ROOM_LIST_REFRESH_INTERVAL = 30000;
const STATUS_BADGE_WIDTH = 112;
const HEADER_ACTION_WIDTH = 104;

export default function ChatRoomsView({
  router,
  initialRooms = [],
  hasInitialRooms = false,
  initialConnectionStatus = CONNECTION_STATUS.CHECKING,
}) {
  const { user: currentUser } = useAuth();
  const currentUserKey = currentUser?.id || currentUser?._id || currentUser?.email || currentUser?.token;

  const {
    connectionStatus,
    setConnectionStatus,
    isRetrying,
    attemptConnection,
  } = useServerConnection(initialConnectionStatus);

  const {
    roomOrder,
    roomsById,
    roomsRevision,
    prependRoom,
    replaceRoom,
    mergeRoomActivity,
    error,
    loading,
    refreshing,
    joiningRoom,
    fetchRooms,
    refreshRooms,
    handleJoinRoom,
  } = useRoomList({
    initialRooms,
    hasInitialRooms,
    currentUser,
    router,
    connectionStatus,
    setConnectionStatus,
    isRetrying,
    attemptConnection,
  });

  const connectionCheckTimerRef = useRef(null);
  const initialFetchStartedRef = useRef(false);
  const refreshRoomsRef = useRef(refreshRooms);

  useEffect(() => {
    refreshRoomsRef.current = refreshRooms;
  }, [refreshRooms]);

  useEffect(() => {
    if (!currentUserKey) {
      initialFetchStartedRef.current = false;
      return;
    }

    if (initialFetchStartedRef.current) return;

    initialFetchStartedRef.current = true;

    let retryTimer = null;
    let cancelled = false;

    const initFetch = async (retryAttempt = 0) => {
      let succeeded = false;

      try {
        if (hasInitialRooms) {
          // SSR 결과가 있어도 기존 /api/health 관찰 흐름은 유지한다.
          succeeded = await attemptConnection();
        } else {
          // SSR bootstrap이 실패한 경우에는 빈 목록을 정상 데이터로
          // 간주하지 않고 브라우저에서 방 목록을 다시 조회한다.
          succeeded = await fetchRooms();
        }
      } catch (error) {
        succeeded = false;
      }

      // fetchRooms handles its own error state and returns false; attemptConnection
      // rejects on failure. Retry a few times so a transient /api/rooms failure
      // does not leave the page permanently stuck without join buttons.
      //
      // 재시도 스케줄(base 1s→2s→4s→8s→8s, 총 6회 시도)은 e2e 타임아웃에 맞춘 값이다:
      // - loginScenario의 join-button expect는 기본 5초 → 0/1/3초 시도가 창 안에 들어간다
      // - joinRandomChatRoomAction의 waitFor는 기본 30초 → 마지막 시도가 ~23초에 시작해
      //   응답 지연을 감안해도 창 안에서 완료될 수 있다
      //
      // 부하 상황에서 다수 클라이언트가 동시에 램프업하면 결정론적 백오프는 재시도가
      // 같은 시점에 몰려(t=1s,2s,4s…) 서버에 동기화된 스파이크를 만든다. base 의 50~100%
      // 구간으로 지터를 줘 재시도 파도를 분산한다. 지터는 지연을 "줄이는" 방향이라
      // 각 시도가 base 시점보다 늦어지지 않으므로 위 e2e 창은 그대로 보존된다.
      if (succeeded === false && !cancelled && retryAttempt < 5) {
        const base = Math.min(1000 * (2 ** retryAttempt), 8000);
        const delay = base * (0.5 + Math.random() * 0.5);
        retryTimer = setTimeout(() => {
          if (!cancelled) {
            initFetch(retryAttempt + 1);
          }
        }, delay);
      }
    };

    initFetch();

    return () => {
      cancelled = true;
      if (retryTimer) {
        clearTimeout(retryTimer);
      }
    };
  }, [currentUserKey, fetchRooms, hasInitialRooms, attemptConnection]);

  useEffect(() => {
    if (!currentUserKey || connectionStatus !== CONNECTION_STATUS.CHECKING) return;

    connectionCheckTimerRef.current = setInterval(() => {
      attemptConnection();
    }, 5000);

    return () => {
      if (connectionCheckTimerRef.current) {
        clearInterval(connectionCheckTimerRef.current);
      }
    };
  }, [currentUserKey, connectionStatus, attemptConnection]);

  // 활성도 지표는 소켓 이벤트만으로 만료를 알 수 없어 주기적으로 다시 조회한다.
  // 보이지 않는 탭에서는 갱신을 멈추고, 다시 보일 때 즉시 한 번 따라잡는다.
  useEffect(() => {
    if (!currentUserKey || connectionStatus !== CONNECTION_STATUS.CONNECTED) return;

    const refreshWhenVisible = () => {
      if (document.visibilityState !== 'visible') return;
      refreshRoomsRef.current({ silent: true });
    };

    const refreshTimer = setInterval(refreshWhenVisible, ROOM_LIST_REFRESH_INTERVAL);
    document.addEventListener('visibilitychange', refreshWhenVisible);

    return () => {
      clearInterval(refreshTimer);
      document.removeEventListener('visibilitychange', refreshWhenVisible);
    };
  }, [currentUserKey, connectionStatus]);

  useRoomsSocket({
    currentUser,
    setConnectionStatus,
    prependRoom,
    replaceRoom,
    mergeRoomActivity,
  });

  return (
    <Box
      $css={{
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        minHeight: 'calc(100vh - 72px)',
        padding: '$300',
      }}
    >
      <VStack
        $css={{
          gap: '$400',
          width: '100%',
          maxWidth: '1200px',
          padding: '$400',
          borderRadius: '$300',
          border: '1px solid var(--vapor-color-border-normal)',
        }}
      >
        <VStack $css={{ gap: '$300', alignItems: 'center' }}>
          <HStack
            className="w-full"
            $css={{ gap: '$300', alignItems: 'center', justifyContent: 'space-between' }}
          >
            <Text typography="heading3">채팅방 목록</Text>
            <HStack
              $css={{ gap: '$200', justifyContent: 'flex-end' }}
              style={{ minWidth: `${STATUS_BADGE_WIDTH + HEADER_ACTION_WIDTH + 8}px` }}
            >
              <Badge
                colorPalette={STATUS_CONFIG[connectionStatus]?.color || 'danger'}
                style={{
                  display: 'inline-flex',
                  justifyContent: 'center',
                  width: `${STATUS_BADGE_WIDTH}px`,
                  whiteSpace: 'nowrap',
                }}
              >
                {STATUS_CONFIG[connectionStatus].label}
              </Badge>
              {error || connectionStatus === CONNECTION_STATUS.ERROR ? (
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => fetchRooms()}
                  disabled={isRetrying}
                  style={{ width: `${HEADER_ACTION_WIDTH}px` }}
                >
                  <RefreshOutlineIcon size={16} />
                  재연결
                </Button>
              ) : (
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => refreshRooms()}
                  disabled={refreshing || loading}
                  data-testid="refresh-rooms-button"
                  style={{ width: `${HEADER_ACTION_WIDTH}px` }}
                >
                  <RefreshOutlineIcon size={16} />
                  {refreshing ? '갱신 중' : '새로고침'}
                </Button>
              )}
            </HStack>
          </HStack>
        </VStack>
        <Box
          data-testid="rooms-list-surface"
          style={{
            height: `${ROOMS_TABLE_HEIGHT}px`,
            minHeight: `${ROOMS_TABLE_HEIGHT}px`,
            overflow: 'hidden',
            position: 'relative',
          }}
        >
          {error && (
            <Box
              data-testid="rooms-error-overlay"
              aria-live="polite"
              style={{
                position: 'absolute',
                top: '16px',
                left: '16px',
                right: '16px',
                zIndex: 2,
              }}
            >
              <Callout.Root
                colorPalette={error.type === 'danger' ? 'danger' : error.type === 'warning' ? 'warning' : 'primary'}
              >
                <HStack $css={{ gap: '$200', alignItems: 'flex-start' }}>
                  <Callout.Icon>
                    {connectionStatus === CONNECTION_STATUS.ERROR ? (
                      <NetworkIcon size={18} />
                    ) : (
                      <ErrorCircleIcon size={18} />
                    )}
                  </Callout.Icon>
                  <VStack $css={{ gap: '$150', alignItems: 'flex-start' }}>
                    <Text typography="subtitle2" style={{ fontWeight: 500 }}>{error.title}</Text>
                    <Text typography="body2">{error.message}</Text>
                    {error.showRetry && !isRetrying && (
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => fetchRooms()}
                      >
                        다시 시도
                      </Button>
                    )}
                  </VStack>
                </HStack>
              </Callout.Root>
            </Box>
          )}

          {connectionStatus === CONNECTION_STATUS.ERROR ? (
            <ConnectionErrorBanner message="채팅 서버와 연결할 수 없습니다. 잠시 후 다시 시도해주세요." />
          ) : loading ? (
            <RoomsTableSkeleton />
          ) : roomOrder.length > 0 ? (
            <RoomsTable
              roomOrder={roomOrder}
              roomsById={roomsById}
              roomsRevision={roomsRevision}
              connectionStatus={connectionStatus}
              onJoinRoom={handleJoinRoom}
            />
          ) : !error && (
            <VStack
              $css={{ gap: '$300', alignItems: 'center', padding: '$400' }}
              style={{ minHeight: `${ROOMS_TABLE_HEIGHT}px`, justifyContent: 'center' }}
              data-testid="rooms-empty"
            >
              <Text typography="body1">생성된 채팅방이 없습니다.</Text>
              <Button
                colorPalette="primary"
                onClick={() => router.push('/chat/new')}
                disabled={connectionStatus !== CONNECTION_STATUS.CONNECTED}
              >
                새 채팅방 만들기
              </Button>
            </VStack>
          )}
        </Box>
      </VStack>
    </Box>
  );
}
