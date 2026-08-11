import { createSessionUser, isSessionExpired } from './session';
import { defaultCookieJar } from './cookieStore';
import {
  ACCESS_TOKEN_COOKIE,
  SESSION_ID_COOKIE,
  USER_COOKIE,
  LAST_TOKEN_VERIFICATION_COOKIE,
} from './authCookies';

// 인증 저장소. 예전에는 localStorage 를 썼으나, SSR(getBffSession)과 클라이언트가
// 같은 세션을 공유하도록 쿠키로 일원화했다. 공개 함수 시그니처는 그대로라
// 호출부(AuthContext / getAuthHeaders / CustomAvatar / ProfileImageUpload)는 바뀌지 않는다.
//
// 저장 배치:
//   - token / sessionId : ACCESS_TOKEN_COOKIE / SESSION_ID_COOKIE (SSR 도 읽는 쿠키)
//   - 프로필(그 외 필드) : USER_COOKIE 에 JSON
//   - 재검증 타임스탬프  : LAST_TOKEN_VERIFICATION_COOKIE

// 하위 호환용 별칭(기존 import 명 유지).
export const USER_STORAGE_KEY = USER_COOKIE;
export const LAST_TOKEN_VERIFICATION_KEY = LAST_TOKEN_VERIFICATION_COOKIE;

const splitUser = (user) => {
  const { token, sessionId, ...profile } = user;
  return { token, sessionId, profile };
};

export const clearStoredUser = (jar = defaultCookieJar) => {
  jar?.remove(USER_COOKIE);
  jar?.remove(ACCESS_TOKEN_COOKIE);
  jar?.remove(SESSION_ID_COOKIE);
};

export const clearAuthStorage = (jar = defaultCookieJar) => {
  clearStoredUser(jar);
  jar?.remove(LAST_TOKEN_VERIFICATION_COOKIE);
};

export const getLastTokenVerification = (jar = defaultCookieJar) => {
  const storedValue = jar?.get(LAST_TOKEN_VERIFICATION_COOKIE);
  return storedValue ? parseInt(storedValue, 10) : null;
};

export const saveLastTokenVerification = (
  now = Date.now(),
  jar = defaultCookieJar
) => {
  jar?.set(LAST_TOKEN_VERIFICATION_COOKIE, now.toString());
};

export const loadStoredUser = ({
  jar = defaultCookieJar,
  now = Date.now(),
} = {}) => {
  if (!jar) {
    return null;
  }

  try {
    const token = jar.get(ACCESS_TOKEN_COOKIE);
    const sessionId = jar.get(SESSION_ID_COOKIE);

    // 토큰 쿠키가 인증의 단일 출처다. 없으면 비로그인.
    if (!token) {
      return null;
    }

    const profileStr = jar.get(USER_COOKIE);
    const profile = profileStr ? JSON.parse(profileStr) : {};

    const userData = { ...profile, token, sessionId };

    if (isSessionExpired(userData, now)) {
      clearStoredUser(jar);
      return null;
    }

    const activeUser = createSessionUser(userData, now);

    // 슬라이딩: 프로필 쿠키의 lastActivity 를 갱신한다(토큰 쿠키 수명은 서버가 관리).
    const { profile: nextProfile } = splitUser(activeUser);
    jar.set(USER_COOKIE, JSON.stringify(nextProfile));

    return activeUser;
  } catch (error) {
    clearStoredUser(jar);
    return null;
  }
};

export const saveStoredUser = (
  userData,
  {
    jar = defaultCookieJar,
    now = Date.now(),
  } = {}
) => {
  if (!jar) {
    return null;
  }

  if (!userData) {
    clearStoredUser(jar);
    return null;
  }

  const activeUser = createSessionUser(userData, now);
  const { token, sessionId, profile } = splitUser(activeUser);

  // 토큰/세션은 SSR 도 읽는 인증 쿠키에 반영(비어 있지 않을 때만).
  if (token) {
    jar.set(ACCESS_TOKEN_COOKIE, token);
  }
  if (sessionId) {
    jar.set(SESSION_ID_COOKIE, sessionId);
  }

  jar.set(USER_COOKIE, JSON.stringify(profile));

  return activeUser;
};
