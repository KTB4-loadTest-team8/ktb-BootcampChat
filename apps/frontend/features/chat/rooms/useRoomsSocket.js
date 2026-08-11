import { useRef, useEffect } from 'react';
import socketClient from '@/lib/socket/socketClient';

const CONNECTION_STATUS = {
  CONNECTED: 'connected',
  DISCONNECTED: 'disconnected',
  ERROR: 'error',
};

export const useRoomsSocket = ({
  currentUser,
  setConnectionStatus,
  prependRoom,
  replaceRoom,
  mergeRoomActivity,
}) => {
  const socketRef = useRef(null);

  useEffect(() => {
    if (!currentUser?.token) return;

    let isSubscribed = true;

    const connectSocket = async () => {
      try {
        const socket = await socketClient
          .connect({
            auth: {
              token: currentUser.token,
              sessionId: currentUser.sessionId,
            },
          })
          .catch((err) => {
            console.log('Socket connection error:', err);
            setConnectionStatus(CONNECTION_STATUS.ERROR);
          });

        if (!isSubscribed || !socket) return;

        socketRef.current = socket;

        const handlers = {
          connect: () => {
            setConnectionStatus(CONNECTION_STATUS.CONNECTED);
          },
          disconnect: () => {
            setConnectionStatus(CONNECTION_STATUS.DISCONNECTED);
          },
          error: () => {
            setConnectionStatus(CONNECTION_STATUS.ERROR);
          },
          roomCreated: (newRoom) => {
            prependRoom(newRoom);
          },
          roomUpdated: (updatedRoom) => {
            replaceRoom(updatedRoom);
          },
          // 활성도 지표만 담긴 경량 payload이므로 방 정보를 덮지 않고 병합한다
          roomActivity: (activity) => {
            if (!activity?._id) return;
            mergeRoomActivity(activity);
          },
        };

        Object.entries(handlers).forEach(([event, handler]) => {
          socket.on(event, handler);
        });

        setConnectionStatus(
          socket.connected
            ? CONNECTION_STATUS.CONNECTED
            : CONNECTION_STATUS.DISCONNECTED
        );

        return () => {
          Object.entries(handlers).forEach(([event, handler]) => {
            socket.off(event, handler);
          });
        };
      } catch (error) {
        if (!isSubscribed) return;

        if (
          error.message?.includes('Authentication required') ||
          error.message?.includes('Invalid session')
        ) {
          // Auth error will be handled by the useAuth context
        }

        setConnectionStatus(CONNECTION_STATUS.ERROR);
      }
    };

    let unsubscribe = null;
    connectSocket().then((cleanup) => {
      if (!isSubscribed) {
        cleanup?.();
        return;
      }
      unsubscribe = cleanup;
    });

    return () => {
      isSubscribed = false;
      unsubscribe?.();
      socketRef.current = null;
    };
  }, [currentUser]); // eslint-disable-line react-hooks/exhaustive-deps

  return { socketRef };
};

export default useRoomsSocket;
