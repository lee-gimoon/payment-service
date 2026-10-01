import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { getChatRoomMessages, getChatRooms, markChatRoomRead, sendChatReply } from "../api/chatApi";
import { useAuth } from "../auth/auth";
import { ADMIN_CHAT_DESTINATION, openChatSocket, type ChatConnectionStatus } from "../chat/chatSocket";
import { useChatThread } from "../chat/useChatThread";
import { AppShell } from "../components/AppShell";
import { ChatThread } from "../components/ChatThread";
import { chatListTimeLabel, isChatAdminEvent } from "../lib/chatMessages";
import type { ChatAdminEvent, ChatRoom } from "../types/chat";

type RoomFilter = "all" | "waiting";

interface RoomListener {
  onEvent: (event: ChatAdminEvent) => void;
  onReconnect: () => void;
}

export function AdminChatPage() {
  const { status, customer, login } = useAuth();

  useEffect(() => {
    document.title = "상담 관리 · MODO CLUB";
  }, []);

  return <AppShell footerText="고객 1:1 상담" mainClassName="admin-chat-page">
    <nav className="breadcrumb" aria-label="현재 위치"><Link to="/">스토어</Link><span aria-hidden="true">/</span><span>상담 관리</span></nav>
    <p className="eyebrow">SUPPORT</p>
    <h1>상담 관리</h1>
    {status === "checking" && <p role="status">로그인 상태를 확인하고 있습니다.</p>}
    {status === "signedOut" && <section className="lookup-card" aria-labelledby="admin-sign-in-title">
      <div>
        <p className="eyebrow">SIGN IN</p>
        <h2 id="admin-sign-in-title">로그인이 필요합니다</h2>
        <p className="subtle">상담 관리는 쇼핑몰 관리자 계정으로 로그인해야 사용할 수 있습니다.</p>
      </div>
      <button className="primary-button" type="button" onClick={() => login()}>로그인하고 상담 관리하기</button>
    </section>}
    {status === "signedIn" && !customer?.isShopAdmin && <section className="empty-state">
      <h2>쇼핑몰 관리자만 볼 수 있습니다</h2>
      <p>상담 관리는 쇼핑몰 관리자 역할(shop-admin)이 있는 계정만 사용할 수 있습니다. 문의는 화면 오른쪽 아래 문의하기를 이용해주세요.</p>
      <Link className="primary-button" to="/">스토어로 돌아가기</Link>
    </section>}
    {status === "signedIn" && customer?.isShopAdmin && <AdminChatConsole />}
  </AppShell>;
}

function AdminChatConsole() {
  const { roomId } = useParams();
  const [rooms, setRooms] = useState<ChatRoom[]>([]);
  const [waitingRooms, setWaitingRooms] = useState<ChatRoom[]>([]);
  const [roomsLoaded, setRoomsLoaded] = useState(false);
  const [filter, setFilter] = useState<RoomFilter>("all");
  const [connection, setConnection] = useState<ChatConnectionStatus>("connecting");
  const [error, setError] = useState("");
  const listeners = useRef(new Set<RoomListener>());
  const refreshTimer = useRef<number | undefined>(undefined);
  const refreshRequest = useRef(0);

  const refreshRooms = useCallback(async () => {
    const request = ++refreshRequest.current;
    try {
      // 전체 목록의 최근 100개 밖에 있는 미답변 상담도 서버에서 먼저 걸러 가져온다.
      const [allRooms, pendingRooms] = await Promise.all([getChatRooms(), getChatRooms(true)]);
      if (request !== refreshRequest.current) return;
      setRooms(allRooms);
      setWaitingRooms(pendingRooms);
      setRoomsLoaded(true);
      setError("");
    } catch (requestError) {
      if (request !== refreshRequest.current) return;
      setError(requestError instanceof Error ? requestError.message : "상담 목록을 불러오지 못했습니다.");
    }
  }, []);

  // 메시지가 몰려 와도 목록은 잠시 뒤 한 번만 다시 불러온다.
  const scheduleRefresh = useCallback(() => {
    window.clearTimeout(refreshTimer.current);
    refreshTimer.current = window.setTimeout(() => void refreshRooms(), 300);
  }, [refreshRooms]);

  useEffect(() => () => {
    window.clearTimeout(refreshTimer.current);
    refreshRequest.current += 1;
  }, []);

  useEffect(() => openChatSocket({
    destination: ADMIN_CHAT_DESTINATION,
    onMessage: body => {
      if (!isChatAdminEvent(body)) return;
      listeners.current.forEach(listener => listener.onEvent(body));
      scheduleRefresh();
    },
    onStatus: setConnection,
    onConnected: () => {
      void refreshRooms();
      listeners.current.forEach(listener => listener.onReconnect());
    }
  }), [refreshRooms, scheduleRefresh]);

  useEffect(() => { void refreshRooms(); }, [refreshRooms]);

  const listen = useCallback((listener: RoomListener) => {
    listeners.current.add(listener);
    return () => { listeners.current.delete(listener); };
  }, []);

  const markRoomRead = useCallback((id: string, lastReadMessageId: number) => {
    const mark = (current: ChatRoom[]) => current.map(room =>
      room.roomId === id && room.lastMessage.id <= lastReadMessageId ? { ...room, unreadCount: 0 } : room);
    setRooms(mark);
    setWaitingRooms(mark);
    scheduleRefresh();
  }, [scheduleRefresh]);

  const visibleRooms = filter === "waiting" ? waitingRooms : rooms;
  const selectedRoom = rooms.find(room => room.roomId === roomId) ?? waitingRooms.find(room => room.roomId === roomId);

  return <div className={`admin-chat${roomId ? " has-selection" : ""}`}>
    <section className="chat-room-list" aria-labelledby="chat-rooms-title">
      <div className="chat-room-list-header">
        <h2 id="chat-rooms-title">상담 목록</h2>
        <small role="status">{connectionLabel(connection)}</small>
      </div>
      <div className="chat-filter" role="group" aria-label="상담 목록 보기">
        <button type="button" aria-pressed={filter === "all"} onClick={() => setFilter("all")}>전체 {rooms.length}</button>
        <button type="button" aria-pressed={filter === "waiting"} onClick={() => setFilter("waiting")}>답변 대기 {waitingRooms.length}</button>
      </div>
      {error && <p className="error chat-error" role="alert">{error}</p>}
      {!roomsLoaded && !error && <p className="chat-rooms-empty" role="status">상담 목록을 불러오고 있습니다.</p>}
      {roomsLoaded && visibleRooms.length === 0 && <p className="chat-rooms-empty">
        {filter === "waiting" ? "답변을 기다리는 상담이 없습니다." : "아직 들어온 상담이 없습니다."}
      </p>}
      <ul className="chat-rooms">
        {visibleRooms.map(room => <li key={room.roomId}>
          <Link className={`chat-room${room.roomId === roomId ? " selected" : ""}`}
            to={`/admin/chat/${encodeURIComponent(room.roomId)}`}
            aria-current={room.roomId === roomId ? "true" : undefined}>
            <span className="chat-room-top">
              <strong>{room.customerName}</strong>
              <time dateTime={room.lastMessage.createdAt}>{chatListTimeLabel(room.lastMessage.createdAt)}</time>
            </span>
            {room.customerEmail && <small>{room.customerEmail}</small>}
            <span className="chat-room-preview">{room.lastMessage.sender === "ADMIN" ? "답변: " : ""}{room.lastMessage.content}</span>
            {(room.waiting || room.unreadCount > 0) && <span className="chat-room-badges">
              {room.waiting && <span className="status-badge waiting">답변 대기</span>}
              {room.unreadCount > 0 && <span className="chat-count">안 읽음 {room.unreadCount}</span>}
            </span>}
          </Link>
        </li>)}
      </ul>
    </section>
    <section className="chat-room-thread" aria-labelledby="chat-thread-title">
      {roomId
        ? <AdminRoomThread key={roomId} roomId={roomId} room={selectedRoom} listen={listen} onRead={markRoomRead} />
        : <>
          <h2 className="visually-hidden" id="chat-thread-title">대화</h2>
          <p className="chat-thread-placeholder">왼쪽 상담 목록에서 고객을 고르면 대화를 볼 수 있습니다.</p>
        </>}
    </section>
  </div>;
}

interface AdminRoomThreadProps {
  roomId: string;
  room: ChatRoom | undefined;
  listen: (listener: RoomListener) => () => void;
  onRead: (roomId: string, lastReadMessageId: number) => void;
}

function AdminRoomThread({ roomId, room, listen, onRead }: AdminRoomThreadProps) {
  const thread = useChatThread({
    load: page => getChatRoomMessages(roomId, page),
    send: (clientMessageId, content) => sendChatReply(roomId, clientMessageId, content),
    markRead: lastReadMessageId => markChatRoomRead(roomId, lastReadMessageId),
    counterpart: "CUSTOMER",
    viewing: true,
    onRead: lastReadMessageId => onRead(roomId, lastReadMessageId)
  });
  const { receive, sync } = thread;

  useEffect(() => {
    void sync();
    return listen({
      onEvent: event => { if (event.roomId === roomId) receive([event.message], true); },
      onReconnect: () => { void sync(); }
    });
  }, [roomId, listen, receive, sync]);

  const customerName = room?.customerName ?? "고객";
  return <>
    <div className="chat-thread-header">
      <Link className="chat-back" to="/admin/chat">목록으로</Link>
      <div>
        <h2 id="chat-thread-title">{customerName}</h2>
        {room?.customerEmail && <small>{room.customerEmail}</small>}
      </div>
    </div>
    {!thread.loaded && !thread.error && <p className="chat-status" role="status">대화를 불러오고 있습니다.</p>}
    {thread.error && <p className="error chat-error" role="alert">{thread.error}</p>}
    <ChatThread
      viewer="ADMIN"
      counterpartName={customerName}
      messages={thread.messages}
      pending={thread.pending}
      hasMore={thread.hasMore}
      loadingOlder={thread.loadingOlder}
      emptyText="아직 메시지가 없습니다."
      inputLabel="답변 내용"
      placeholder="고객에게 보낼 답변을 입력하세요"
      onLoadOlder={() => void thread.loadOlder()}
      onSend={content => void thread.send(content)}
      onRetry={thread.retry}
    />
  </>;
}

function connectionLabel(status: ChatConnectionStatus): string {
  if (status === "connected") return "실시간 연결됨";
  if (status === "reconnecting") return "연결이 끊겨 다시 연결하고 있습니다";
  return "연결하고 있습니다";
}
