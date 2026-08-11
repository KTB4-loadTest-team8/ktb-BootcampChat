import 'server-only';
import {
  ACCESS_TOKEN_COOKIE,
  SESSION_ID_COOKIE,
  USER_COOKIE,
  AUTH_COOKIE_MAX_AGE_SECONDS,
} from '@/lib/auth/authCookies';

export {
  ACCESS_TOKEN_COOKIE,
  SESSION_ID_COOKIE,
  USER_COOKIE,
  AUTH_COOKIE_MAX_AGE_SECONDS,
};

export const BACKEND_API_URL = (
  process.env.API_URL ||
  process.env.NEXT_PUBLIC_API_URL ||
  'http://localhost:5000'
).replace(/\/$/, '');

export const getBffSession = (cookieStore) => {
  const token = cookieStore.get(ACCESS_TOKEN_COOKIE)?.value;
  const sessionId = cookieStore.get(SESSION_ID_COOKIE)?.value;

  if (!token || !sessionId) return null;

  return { token, sessionId };
};

export const getBackendAuthHeaders = (session) => ({
  'x-auth-token': session.token,
  'x-session-id': session.sessionId,
});

// 성능 우선: 인증을 쿠키로 일원화하되, 소켓 handshake/직결 API 가 토큰을 읽어야 하므로
// httpOnly 를 끈다(클라이언트 JS 접근 허용). secure 는 https(production)에서만.
export const getAuthCookieOptions = () => ({
  httpOnly: false,
  secure: process.env.NODE_ENV === 'production',
  sameSite: 'lax',
  path: '/',
  maxAge: AUTH_COOKIE_MAX_AGE_SECONDS,
});

export const setAuthCookies = (response, { token, sessionId }) => {
  const options = getAuthCookieOptions();
  response.cookies.set(ACCESS_TOKEN_COOKIE, token, options);
  response.cookies.set(SESSION_ID_COOKIE, sessionId, options);
};

export const clearAuthCookies = (response) => {
  const options = { ...getAuthCookieOptions(), maxAge: 0 };
  response.cookies.set(ACCESS_TOKEN_COOKIE, '', options);
  response.cookies.set(SESSION_ID_COOKIE, '', options);
  response.cookies.set(USER_COOKIE, '', options);
};
