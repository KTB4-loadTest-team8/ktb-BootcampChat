'use client';

import { useEffect } from 'react';
import { useAuth } from '@/contexts/AuthContext';
import { clearAuthStorage } from '@/lib/auth/authStorage';

const BOOTSTRAP_ATTEMPT_KEY = 'bffSessionBootstrapAttempted';

export default function LegacySessionBridge() {
  const { user, isLoading } = useAuth();

  useEffect(() => {
    if (isLoading) return;

    if (!user?.token || !user?.sessionId) {
      window.location.replace('/?redirect=/chat');
      return;
    }

    // Set-Cookie가 프록시에서 제거되는 경우에도 /chat ↔ / 무한 루프에 빠지지 않는다.
    if (window.sessionStorage.getItem(BOOTSTRAP_ATTEMPT_KEY)) {
      clearAuthStorage();
      window.location.replace('/?redirect=/chat');
      return;
    }

    window.sessionStorage.setItem(BOOTSTRAP_ATTEMPT_KEY, 'true');

    fetch('/bff/auth/bootstrap', {
      method: 'POST',
      credentials: 'include',
      headers: {
        'x-auth-token': user.token,
        'x-session-id': user.sessionId,
      },
    }).then((response) => {
      if (!response.ok) {
        throw new Error('BFF_SESSION_BOOTSTRAP_FAILED');
      }

      window.location.replace('/chat');
    }).catch(() => {
      clearAuthStorage();
      window.location.replace('/?redirect=/chat');
    });
  }, [isLoading, user]);

  return (
    <main
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      <p>로그인 세션을 확인하는 중...</p>
    </main>
  );
}
