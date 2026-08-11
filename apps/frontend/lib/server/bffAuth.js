import 'server-only';

export const ACCESS_TOKEN_COOKIE = 'ktb_access_token';
export const SESSION_ID_COOKIE = 'ktb_session_id';
export const AUTH_COOKIE_MAX_AGE_SECONDS = 2 * 60 * 60;

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

export const getAuthCookieOptions = () => ({
  httpOnly: true,
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
};
