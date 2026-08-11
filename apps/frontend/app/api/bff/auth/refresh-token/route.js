import { cookies } from 'next/headers';
import { NextResponse } from 'next/server';
import {
  BACKEND_API_URL,
  clearAuthCookies,
  getBackendAuthHeaders,
  getBffSession,
  setAuthCookies,
} from '@/lib/server/bffAuth';

export const dynamic = 'force-dynamic';

export async function POST(request) {
  const cookieSession = getBffSession(await cookies());
  const legacyToken = request.headers.get('x-auth-token');
  const legacySessionId = request.headers.get('x-session-id');
  const session = cookieSession || (
    legacyToken && legacySessionId
      ? { token: legacyToken, sessionId: legacySessionId }
      : null
  );

  if (!session) {
    const response = NextResponse.json(
      { success: false, message: '인증 정보가 없습니다.' },
      { status: 401 }
    );
    clearAuthCookies(response);
    return response;
  }

  const backendResponse = await fetch(`${BACKEND_API_URL}/api/auth/refresh-token`, {
    method: 'POST',
    cache: 'no-store',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      ...getBackendAuthHeaders(session),
    },
    body: '{}',
  });
  const payload = await backendResponse.json().catch(() => ({
    success: false,
    message: '토큰 갱신 응답을 처리할 수 없습니다.',
  }));
  const response = NextResponse.json(payload, { status: backendResponse.status });

  if (backendResponse.ok && payload?.token && payload?.sessionId) {
    setAuthCookies(response, payload);
  } else if (backendResponse.status === 401) {
    clearAuthCookies(response);
  }

  return response;
}
