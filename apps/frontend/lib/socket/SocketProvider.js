import React, { createContext, useEffect, useMemo, useRef } from 'react';
import socketClient from './socketClient';

export const SocketContext = createContext(null);

export const SocketProvider = ({ children, client = socketClient, session = null }) => {
  const value = useMemo(() => client, [client]);
  const activeSessionRef = useRef(null);

  useEffect(() => {
    const hasSession = Boolean(session?.token && session?.sessionId);
    const sessionKey = hasSession ? session.sessionId : null;
    const connectSession = () => client.connect({
      auth: {
        token: session.token,
        sessionId: session.sessionId,
      },
    }).catch(() => {});

    if (hasSession) {
      activeSessionRef.current = sessionKey;
      connectSession();
    } else if (activeSessionRef.current) {
      activeSessionRef.current = null;
      client.disconnect();
    }

    const handleOnline = () => {
      if (!client.isConnected() && hasSession) {
        connectSession();
      }
    };

    window.addEventListener('online', handleOnline);

    return () => {
      window.removeEventListener('online', handleOnline);
    };
  }, [client, session?.sessionId, session?.token]);

  return (
    <SocketContext.Provider value={value}>
      {children}
    </SocketContext.Provider>
  );
};
