import { NextResponse } from 'next/server';
import {
  BACKEND_API_URL,
  setAuthCookies,
} from '@/lib/server/bffAuth';

export const dynamic = 'force-dynamic';

export async function POST(request) {
  const body = await request.text();
  const backendResponse = await fetch(`${BACKEND_API_URL}/api/auth/login`, {
    method: 'POST',
    cache: 'no-store',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      'User-Agent': request.headers.get('user-agent') || '',
      'X-Forwarded-For': request.headers.get('x-forwarded-for') || '',
    },
    body,
  });

  const payload = await backendResponse.json().catch(() => ({
    success: false,
    message: '로그인 응답을 처리할 수 없습니다.',
  }));
  const response = NextResponse.json(payload, { status: backendResponse.status });

  if (backendResponse.ok && payload?.token && payload?.sessionId) {
    setAuthCookies(response, payload);
  }

  return response;
}
