// 인증 쿠키 이름/수명의 단일 출처.
// bffAuth.js(server-only)와 클라이언트 cookieStore/authStorage가 함께 import 한다.
// 서버가 심는 인증 쿠키와 클라이언트가 읽는 쿠키가 반드시 같은 이름이어야 하므로
// 여기서만 정의한다.

export const ACCESS_TOKEN_COOKIE = 'ktb_access_token';
export const SESSION_ID_COOKIE = 'ktb_session_id';
// 프로필 표시 데이터(id/name/email/profileImage). 토큰과 분리해 SSR getBffSession은
// 토큰 쿠키만, UI는 이 쿠키를 읽는다.
export const USER_COOKIE = 'ktb_user';
// 토큰 재검증 throttle 타임스탬프(클라 전용).
export const LAST_TOKEN_VERIFICATION_COOKIE = 'ktb_last_token_verification';

export const AUTH_COOKIE_MAX_AGE_SECONDS = 2 * 60 * 60;
