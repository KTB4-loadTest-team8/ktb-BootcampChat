'use client';

import { useRouter } from 'next/navigation';
import ChatRoomsView from '@/features/chat/rooms/ChatRoomsView';

export default function ChatRoomsClient({ initialRooms, initialConnectionStatus }) {
  const router = useRouter();

  return (
    <ChatRoomsView
      router={router}
      initialRooms={initialRooms}
      hasInitialRooms
      initialConnectionStatus={initialConnectionStatus}
    />
  );
}
