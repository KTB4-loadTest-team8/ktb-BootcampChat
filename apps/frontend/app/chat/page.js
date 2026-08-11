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
        hasInitialRooms={false}
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
      hasInitialRooms
      initialConnectionStatus={initialData.connected ? 'connected' : 'checking'}
    />
  );
}

export default async function ChatPage() {
  const session = getBffSession(await cookies());

  if (!session) {
    // 인증이 쿠키로 일원화되어 승격 브리지가 필요 없다. 세션 없으면 로그인으로.
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
