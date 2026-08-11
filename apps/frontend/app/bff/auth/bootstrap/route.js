import { NextResponse } from 'next/server';
import {
  BACKEND_API_URL,
  setAuthCookies,
} from '@/lib/server/bffAuth';

export const dynamic = 'force-dynamic';

export async function POST(request) {
  const token = request.headers.get('x-auth-token');
  const sessionId = request.headers.get('x-session-id');

  if (!token || !sessionId) {
    return NextResponse.json(
      { success: false, message: '인증 정보가 없습니다.' },
      { status: 401 }
    );
  }

  const backendResponse = await fetch(`${BACKEND_API_URL}/api/auth/verify-token`, {
    method: 'POST',
    cache: 'no-store',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      'x-auth-token': token,
      'x-session-id': sessionId,
    },
    body: '{}',
  });
  const payload = await backendResponse.json().catch(() => null);

  if (!backendResponse.ok || payload?.valid !== true) {
    return NextResponse.json(
      { success: false, message: payload?.message || '세션이 만료되었습니다.' },
      { status: 401 }
    );
  }

  const response = NextResponse.json({ success: true });
  setAuthCookies(response, { token, sessionId });
  return response;
}
