import { Suspense } from 'react';
import { cookies } from 'next/headers';
import { redirect } from 'next/navigation';
import ChatHeader from '@/components/ChatHeader';
import ChatRoomsClient from './ChatRoomsClient';
import { getBffSession } from '@/lib/server/bffAuth';
import { loadInitialChatData } from '@/lib/server/chatBootstrap';

const RoomsFallback = () => (
  <main
    style={{
      minHeight: 'calc(100vh - 72px)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
    }}
  >
    <p>채팅방 목록을 불러오는 중...</p>
  </main>
);

async function RoomsStream({ session }) {
  let initialData;

  try {
    initialData = await loadInitialChatData(session);
  } catch {
    return (
      <ChatRoomsClient
        initialRooms={[]}
        initialConnectionStatus="checking"
      />
    );
  }

  if (!initialData.authenticated) {
    redirect('/?redirect=/chat');
  }

  return (
    <ChatRoomsClient
      initialRooms={initialData.rooms}
      initialConnectionStatus={initialData.connected ? 'connected' : 'checking'}
    />
  );
}

export default async function ChatPage() {
  const session = getBffSession(await cookies());

  if (!session) {
    redirect('/?redirect=/chat');
  }

  return (
    <>
      <ChatHeader />
      <Suspense fallback={<RoomsFallback />}>
        <RoomsStream session={session} />
      </Suspense>
    </>
  );
}
