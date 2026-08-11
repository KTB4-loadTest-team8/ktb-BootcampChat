import { useState, useCallback, useRef } from 'react';
import axiosInstance from '@/services/axios';
import { CONNECTION_STATUS } from './useServerConnection';

export const useRoomList = ({
  currentUser,
  router,
  connectionStatus,
  setConnectionStatus,
  isRetrying,
  attemptConnection,
}) => {
  const [roomOrder, setRoomOrder] = useState([]);
  // 이벤트마다 Map 전체를 복제하면 다시 O(n)이 되므로 저장소는 ref로 유지하고,
  // revision만 올려 React에 변경을 알린다. 각 방 객체의 참조는 변경된 ID만 교체된다.
  const roomsByIdRef = useRef(new Map());
  const [roomsRevision, setRoomsRevision] = useState(0);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [isInitialLoad, setIsInitialLoad] = useState(true);
  const [joiningRoom, setJoiningRoom] = useState(false);

  const isLoadingRef = useRef(false);

  const commitRoomMutation = useCallback(() => {
    setRoomsRevision((revision) => revision + 1);
  }, []);

  const replaceRooms = useCallback((rooms) => {
    const nextRoomsById = new Map();
    const nextRoomOrder = [];

    for (const room of rooms) {
      if (!room?._id || nextRoomsById.has(room._id)) continue;
      nextRoomsById.set(room._id, room);
      nextRoomOrder.push(room._id);
    }

    roomsByIdRef.current = nextRoomsById;
    setRoomOrder(nextRoomOrder);
    commitRoomMutation();
  }, [commitRoomMutation]);

  const prependRoom = useCallback((room) => {
    if (!room?._id) return;

    const alreadyExists = roomsByIdRef.current.has(room._id);
    roomsByIdRef.current.set(room._id, room);

    if (!alreadyExists) {
      setRoomOrder((order) => [room._id, ...order]);
    }

    commitRoomMutation();
  }, [commitRoomMutation]);

  const replaceRoom = useCallback((room) => {
    if (!room?._id || !roomsByIdRef.current.has(room._id)) return;

    roomsByIdRef.current.set(room._id, room);
    commitRoomMutation();
  }, [commitRoomMutation]);

  const mergeRoomActivity = useCallback((activity) => {
    if (!activity?._id) return;

    const currentRoom = roomsByIdRef.current.get(activity._id);
    if (!currentRoom) return;

    roomsByIdRef.current.set(activity._id, {
      ...currentRoom,
      recentMessageCount: activity.recentMessageCount,
    });
    commitRoomMutation();
  }, [commitRoomMutation]);

  const handleFetchError = useCallback((error) => {
    let errorMessage = '채팅방 목록을 불러오는데 실패했습니다.';
    let errorType = 'danger';
    let showRetry = !isRetrying;

    if (error.message === 'AUTH_EXPIRED') {
      errorMessage = '인증이 만료되었습니다. 다시 로그인해주세요.';
      errorType = 'danger';
      showRetry = false;

      setError({
        title: '인증 만료',
        message: errorMessage,
        type: errorType,
        showRetry,
      });

      setConnectionStatus(CONNECTION_STATUS.ERROR);
      return;
    }

    if (error.message === 'SERVER_UNREACHABLE') {
      errorMessage = '서버와 연결할 수 없습니다. 다시 시도해주세요.';
      errorType = 'warning';
      showRetry = true;
    }

    setError({
      title: '채팅방 목록 로드 실패',
      message: errorMessage,
      type: errorType,
      showRetry,
    });

    setConnectionStatus(CONNECTION_STATUS.ERROR);
  }, [isRetrying, setConnectionStatus]);

  const loadRooms = useCallback(async () => {
    // 인증 복원 직후 방 목록 요청을 시작한다. 연결 상태 판정은 기존 E2E 계약대로
    // health 응답을 기다리되, 정상 경로에서 두 HTTP 요청의 지연이 누적되지 않게 한다.
    const roomsRequest = axiosInstance.get('/api/rooms').then(
      (response) => ({ response, error: null }),
      (error) => ({ response: null, error })
    );

    await attemptConnection();

    const { response, error } = await roomsRequest;

    if (error) {
      throw error;
    }

    if (!response?.data?.data) {
      throw new Error('INVALID_RESPONSE');
    }

    replaceRooms(response.data.data);
  }, [attemptConnection, replaceRooms]);

  const fetchRooms = useCallback(async () => {
    if (!currentUser?.token || isLoadingRef.current) {
      return;
    }

    try {
      isLoadingRef.current = true;

      setLoading(true);
      setError(null);

      await loadRooms();

      if (isInitialLoad) {
        setIsInitialLoad(false);
      }
    } catch (error) {
      handleFetchError(error);
    } finally {
      setLoading(false);
      isLoadingRef.current = false;
    }
  }, [currentUser, isInitialLoad, loadRooms, handleFetchError]);

  /**
   * 이미 그려진 목록을 유지한 채 다시 조회한다.
   * 자동 갱신(silent)은 실패해도 화면을 흔들지 않고 다음 주기를 기다린다.
   */
  const refreshRooms = useCallback(async ({ silent = false } = {}) => {
    if (!currentUser?.token || isLoadingRef.current) {
      return false;
    }

    try {
      isLoadingRef.current = true;
      setRefreshing(true);

      await loadRooms();
      setError(null);

      return true;
    } catch (error) {
      if (!silent) {
        setError({
          title: '채팅방 목록 갱신 실패',
          message: '목록을 갱신하지 못했습니다. 잠시 후 다시 시도해주세요.',
          type: 'warning',
          showRetry: false,
        });
      }

      return false;
    } finally {
      setRefreshing(false);
      isLoadingRef.current = false;
    }
  }, [currentUser, loadRooms]);

  const handleJoinRoom = useCallback(async (roomId) => {
    if (connectionStatus !== CONNECTION_STATUS.CONNECTED) {
      setError({
        title: '채팅방 입장 실패',
        message: '서버와 연결이 끊어져 있습니다.',
        type: 'danger',
      });
      return;
    }

    setJoiningRoom(true);

    try {
      const response = await axiosInstance.post(`/api/rooms/${roomId}/join`, {});

      if (response.data.success) {
        router.push(`/chat/${roomId}`);
      }
    } catch (error) {
      let errorMessage = '입장에 실패했습니다.';
      if (error.response?.status === 404) {
        errorMessage = '채팅방을 찾을 수 없습니다.';
      } else if (error.response?.status === 403) {
        errorMessage = '채팅방 입장 권한이 없습니다.';
      }

      setError({
        title: '채팅방 입장 실패',
        message: error.response?.data?.message || errorMessage,
        type: 'danger',
      });
    } finally {
      setJoiningRoom(false);
    }
  }, [connectionStatus, router]);

  return {
    roomOrder,
    roomsById: roomsByIdRef.current,
    roomsRevision,
    prependRoom,
    replaceRoom,
    mergeRoomActivity,
    error,
    setError,
    loading,
    refreshing,
    joiningRoom,
    fetchRooms,
    refreshRooms,
    handleJoinRoom,
  };
};

export default useRoomList;
