'use client';

import { useRouter } from 'next/navigation';
import ChatRoomsView from '@/features/chat/rooms/ChatRoomsView';

export default function ChatRoomsClient({
  initialRooms,
  initialConnectionStatus,
  hasInitialRooms = false,
}) {
  const router = useRouter();

  return (
    <ChatRoomsView
      router={router}
      initialRooms={initialRooms}
      hasInitialRooms={hasInitialRooms}
      initialConnectionStatus={initialConnectionStatus}
    />
  );
}
