import { beforeEach, describe, expect, it } from 'vitest';
import { loadStoredUser, saveStoredUser } from '../authStorage';
import { createCookieJar } from '../cookieStore';
import { ACCESS_TOKEN_COOKIE, USER_COOKIE } from '../authCookies';

// 브라우저 쿠키 누적 의미를 흉내내는 in-memory document.
const makeCookieDoc = () => {
  const store = new Map();

  return {
    get cookie() {
      return Array.from(store.entries())
        .map(([name, value]) => `${name}=${value}`)
        .join('; ');
    },
    set cookie(str) {
      const [pair, ...attrs] = str.split(';').map((s) => s.trim());
      const eq = pair.indexOf('=');
      const name = pair.slice(0, eq);
      const value = pair.slice(eq + 1);

      const maxAgeAttr = attrs.find((a) => a.toLowerCase().startsWith('max-age='));
      const maxAge = maxAgeAttr ? Number(maxAgeAttr.split('=')[1]) : undefined;

      if (maxAge === 0) {
        store.delete(name);
      } else {
        store.set(name, value);
      }
    },
  };
};

describe('authStorage', () => {
  let jar;

  beforeEach(() => {
    jar = createCookieJar(makeCookieDoc());
  });

  it('loads a valid stored user and refreshes lastActivity', () => {
    jar.set(ACCESS_TOKEN_COOKIE, 'token-1');
    jar.set(USER_COOKIE, JSON.stringify({ id: 'user-1', lastActivity: 1_000 }));

    const user = loadStoredUser({ jar, now: 2_000 });

    expect(user).toMatchObject({
      id: 'user-1',
      token: 'token-1',
      lastActivity: 2_000,
    });
    expect(JSON.parse(jar.get(USER_COOKIE))).toMatchObject({
      id: 'user-1',
      lastActivity: 2_000,
    });
  });

  it('returns null when no user is stored', () => {
    expect(loadStoredUser({ jar, now: 2_000 })).toBeNull();
  });

  it('clears corrupt stored user values', () => {
    jar.set(ACCESS_TOKEN_COOKIE, 'token-1');
    jar.set(USER_COOKIE, '{not-json');

    expect(loadStoredUser({ jar, now: 2_000 })).toBeNull();
    expect(jar.get(USER_COOKIE)).toBeNull();
    expect(jar.get(ACCESS_TOKEN_COOKIE)).toBeNull();
  });

  it('clears expired sessions', () => {
    jar.set(ACCESS_TOKEN_COOKIE, 'token-1');
    jar.set(USER_COOKIE, JSON.stringify({ id: 'user-1', lastActivity: 1_000 }));

    const user = loadStoredUser({
      jar,
      now: 2 * 60 * 60 * 1_000 + 1_001,
    });

    expect(user).toBeNull();
    expect(jar.get(USER_COOKIE)).toBeNull();
    expect(jar.get(ACCESS_TOKEN_COOKIE)).toBeNull();
  });

  it('saves users with a refreshed activity timestamp', () => {
    const user = saveStoredUser(
      { id: 'user-1', token: 'token-1', sessionId: 'session-1' },
      { jar, now: 3_000 }
    );

    expect(user).toMatchObject({
      id: 'user-1',
      token: 'token-1',
      sessionId: 'session-1',
      lastActivity: 3_000,
    });
    // 토큰/세션은 인증 쿠키로, 프로필은 USER_COOKIE 로 분리 저장된다.
    expect(jar.get(ACCESS_TOKEN_COOKIE)).toBe('token-1');
    expect(JSON.parse(jar.get(USER_COOKIE))).toMatchObject({
      id: 'user-1',
      lastActivity: 3_000,
    });
  });
});
