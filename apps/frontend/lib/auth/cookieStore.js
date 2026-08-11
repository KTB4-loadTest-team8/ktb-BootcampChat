import { AUTH_COOKIE_MAX_AGE_SECONDS } from './authCookies';

// document.cookie 위에 get/set/remove 를 제공하는 클라이언트 쿠키 jar.
//
// 인증을 localStorage 대신 이 쿠키들로 일원화하기 위한 저장 엔진이다.
// 반드시 지켜야 하는 3가지를 여기서 강제한다.
//   1) 클라이언트 JS 가 값을 읽을 수 있어야 한다(소켓 handshake/API 헤더용) →
//      JS 로 set 하는 쿠키는 태생적으로 non-HttpOnly.
//   2) secure 는 https 에서만 붙인다. E2E 는 http://127.0.0.1 이라 secure 를 붙이면
//      쿠키가 아예 저장되지 않아 전 테스트가 깨진다.
//   3) path=/ 와 max-age 를 항상 명시한다. path 가 없으면 /chat/[roomId] 등에서
//      못 읽고, max-age 가 없으면 세션 쿠키가 되어 복원이 불안정하다.

const parseCookies = (cookieString) => {
  const jar = {};

  (cookieString || '').split(';').forEach((pair) => {
    const index = pair.indexOf('=');
    if (index === -1) return;

    const key = pair.slice(0, index).trim();
    if (!key) return;

    jar[key] = decodeURIComponent(pair.slice(index + 1).trim());
  });

  return jar;
};

const isSecureContext = () =>
  typeof location !== 'undefined' && location.protocol === 'https:';

export const createCookieJar = (
  doc = typeof document !== 'undefined' ? document : null
) => ({
  get(name) {
    if (!doc) return null;
    const value = parseCookies(doc.cookie)[name];
    return value === undefined ? null : value;
  },

  set(name, value, { maxAge = AUTH_COOKIE_MAX_AGE_SECONDS, path = '/' } = {}) {
    if (!doc) return;

    let cookie =
      `${name}=${encodeURIComponent(value)}` +
      `; path=${path}; max-age=${maxAge}; samesite=lax`;

    if (isSecureContext()) {
      cookie += '; secure';
    }

    doc.cookie = cookie;
  },

  remove(name, { path = '/' } = {}) {
    if (!doc) return;
    doc.cookie = `${name}=; path=${path}; max-age=0`;
  },
});

export const defaultCookieJar = createCookieJar();
