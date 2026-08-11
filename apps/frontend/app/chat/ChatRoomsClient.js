'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import ChatRoomsView from '@/features/chat/rooms/ChatRoomsView';

export default function ChatRoomsClient({ initialRooms, initialConnectionStatus }) {
  const router = useRouter();

  useEffect(() => {
    window.sessionStorage.removeItem('bffSessionBootstrapAttempted');
  }, []);

  return (
    <ChatRoomsView
      router={router}
      initialRooms={initialRooms}
      hasInitialRooms
      initialConnectionStatus={initialConnectionStatus}
    />
  );
}
