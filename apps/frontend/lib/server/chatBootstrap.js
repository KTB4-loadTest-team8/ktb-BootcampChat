import 'server-only';

import { BACKEND_API_URL, getBackendAuthHeaders } from './bffAuth';

const readJson = async (response) => {
  const text = await response.text();
  if (!text) return null;

  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
};

export const loadInitialChatData = async (session) => {
  const authHeaders = getBackendAuthHeaders(session);
  const requestOptions = { cache: 'no-store' };

  const [roomsResponse, healthResponse] = await Promise.all([
    fetch(`${BACKEND_API_URL}/api/rooms`, {
      ...requestOptions,
      headers: authHeaders,
    }),
    fetch(`${BACKEND_API_URL}/api/health`, requestOptions),
  ]);

  if (roomsResponse.status === 401 || roomsResponse.status === 403) {
    return { authenticated: false, rooms: [], connected: false };
  }

  const [roomsPayload, healthPayload] = await Promise.all([
    readJson(roomsResponse),
    readJson(healthResponse),
  ]);

  if (!roomsResponse.ok || !Array.isArray(roomsPayload?.data)) {
    throw new Error('ROOMS_FETCH_FAILED');
  }

  return {
    authenticated: true,
    rooms: roomsPayload.data,
    connected: healthResponse.ok && healthPayload?.status === 'ok',
  };
};
