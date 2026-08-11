import { beforeEach, describe, expect, it } from 'vitest';
import { createApiClient, getAuthHeaders } from '../client';
import { defaultCookieJar } from '../../auth/cookieStore';
import {
  ACCESS_TOKEN_COOKIE,
  USER_COOKIE,
  LAST_TOKEN_VERIFICATION_COOKIE,
} from '../../auth/authCookies';

const readHeader = (headers, name) => headers.get?.(name) ?? headers[name];

const clearAllCookies = () => {
  document.cookie.split(';').forEach((entry) => {
    const name = entry.split('=')[0].trim();
    if (name) {
      document.cookie = `${name}=; path=/; max-age=0`;
    }
  });
};

describe('api client', () => {
  beforeEach(() => {
    clearAllCookies();
  });

  it('builds auth headers from a session', () => {
    expect(
      getAuthHeaders({
        token: 'token-1',
        sessionId: 'session-1',
      })
    ).toEqual({
      'x-auth-token': 'token-1',
      'x-session-id': 'session-1',
    });
  });

  it('injects stored auth headers into requests', async () => {
    const client = createApiClient({
      baseURL: 'http://api.test',
      getSession: () => ({
        token: 'token-1',
        sessionId: 'session-1',
      }),
    });

    client.defaults.adapter = async (config) => ({
      config,
      data: { ok: true },
      headers: {},
      status: 200,
      statusText: 'OK',
    });

    const response = await client.get('/api/rooms');

    expect(readHeader(response.config.headers, 'x-auth-token')).toBe('token-1');
    expect(readHeader(response.config.headers, 'x-session-id')).toBe('session-1');
  });

  it('respects skipAuth requests', async () => {
    const client = createApiClient({
      baseURL: 'http://api.test',
      getSession: () => ({
        token: 'token-1',
        sessionId: 'session-1',
      }),
    });

    client.defaults.adapter = async (config) => ({
      config,
      data: { ok: true },
      headers: {},
      status: 200,
      statusText: 'OK',
    });

    const response = await client.post('/api/auth/login', {}, { skipAuth: true });

    expect(readHeader(response.config.headers, 'x-auth-token')).toBeUndefined();
    expect(readHeader(response.config.headers, 'x-session-id')).toBeUndefined();
  });

  it('clears stored users on auth expiration', async () => {
    defaultCookieJar.set(ACCESS_TOKEN_COOKIE, 'token-1');
    defaultCookieJar.set(USER_COOKIE, JSON.stringify({ id: 'user-1' }));
    defaultCookieJar.set(LAST_TOKEN_VERIFICATION_COOKIE, '3000');

    const client = createApiClient({
      baseURL: 'http://api.test',
      getSession: () => ({
        token: 'token-1',
      }),
    });

    client.defaults.adapter = async (config) => {
      const error = new Error('Unauthorized');
      error.config = config;
      error.response = {
        config,
        data: {},
        headers: {},
        status: 401,
        statusText: 'Unauthorized',
      };
      throw error;
    };

    await expect(client.get('/api/profile')).rejects.toMatchObject({
      code: 'AUTH_EXPIRED',
      status: 401,
    });
    expect(defaultCookieJar.get(ACCESS_TOKEN_COOKIE)).toBeNull();
    expect(defaultCookieJar.get(USER_COOKIE)).toBeNull();
    expect(defaultCookieJar.get(LAST_TOKEN_VERIFICATION_COOKIE)).toBeNull();
  });

  it('can leave 401 responses to endpoint-specific handlers', async () => {
    const client = createApiClient({
      baseURL: 'http://api.test',
      getSession: () => null,
    });

    client.defaults.adapter = async (config) => {
      const error = new Error('Unauthorized');
      error.config = config;
      error.response = {
        config,
        data: { message: 'invalid credentials' },
        headers: {},
        status: 401,
        statusText: 'Unauthorized',
      };
      throw error;
    };

    await expect(
      client.post('/api/auth/login', {}, { skipAuth: true, handleAuthError: false })
    ).rejects.toMatchObject({
      response: {
        status: 401,
        data: { message: 'invalid credentials' },
      },
    });
  });
});
