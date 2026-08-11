import { cookies } from 'next/headers';
import { NextResponse } from 'next/server';
import {
  BACKEND_API_URL,
  clearAuthCookies,
  getBackendAuthHeaders,
  getBffSession,
} from '@/lib/server/bffAuth';

export const dynamic = 'force-dynamic';

export async function POST(request) {
  const cookieStore = await cookies();
  const cookieSession = getBffSession(cookieStore);
  const legacyToken = request.headers.get('x-auth-token');
  const legacySessionId = request.headers.get('x-session-id');
  const session = cookieSession || (
    legacyToken && legacySessionId
      ? { token: legacyToken, sessionId: legacySessionId }
      : null
  );

  let status = 200;
  let payload = { success: true, message: '로그아웃이 완료되었습니다.' };

  if (session) {
    try {
      const backendResponse = await fetch(`${BACKEND_API_URL}/api/auth/logout`, {
        method: 'POST',
        cache: 'no-store',
        headers: {
          Accept: 'application/json',
          'Content-Type': 'application/json',
          ...getBackendAuthHeaders(session),
        },
        body: '{}',
      });
      status = backendResponse.status;
      payload = await backendResponse.json().catch(() => payload);
    } catch {
      // 로그아웃은 로컬 쿠키 제거가 우선인 best-effort 작업이다.
    }
  }

  const response = NextResponse.json(payload, { status });
  clearAuthCookies(response);
  return response;
}
