import { useEffect, useRef, useState, type KeyboardEvent, type ReactNode, type RefObject } from "react";
import { Link, useLocation } from "react-router-dom";
import { getChatRooms, getMyChat, markMyChatRead, sendMyChatMessage } from "../api/chatApi";
import { useAuth } from "../auth/auth";
import {
  ADMIN_CHAT_DESTINATION,
  CUSTOMER_CHAT_DESTINATION,
  openChatSocket,
  type ChatConnectionStatus
} from "../chat/chatSocket";
import { useChatThread } from "../chat/useChatThread";
import { isChatAdminEvent, isChatMessage } from "../lib/chatMessages";
import { ChatThread } from "./ChatThread";

/**
 * 모든 쇼핑몰 화면 오른쪽 아래의 1:1 문의 창. 관리자 상담 화면에서는 숨긴다.
 * 쇼핑몰 관리자는 고객 창구를 쓰지 않으므로 같은 자리에 상담 관리 바로가기를 보여준다.
 */
export function ChatWidget() {
  const { status, customer, login } = useAuth();
  const { pathname } = useLocation();
  if (status === "checking" || pathname.startsWith("/admin")) return null;
  if (status === "signedIn" && customer?.isShopAdmin) return <AdminChatShortcut key={customer.id} />;
  if (status === "signedIn" && customer) return <CustomerChat key={customer.id} />;
  return <SignedOutChat onLogin={() => login()} />;
}

/** 답변을 기다리는 상담 수를 실시간으로 보여주고, 누르면 상담 관리 화면으로 간다. */
function AdminChatShortcut() {
  const [waiting, setWaiting] = useState(0);

  useEffect(() => {
    let active = true;
    let timer: number | undefined;
    let refreshRequest = 0;
    const refresh = () => {
      const request = ++refreshRequest;
      getChatRooms(true)
        .then(rooms => { if (active && request === refreshRequest) setWaiting(rooms.length); })
        .catch(() => {
          // 수를 못 불러와도 바로가기는 그대로 쓸 수 있다.
        });
    };
    // 메시지가 몰려 와도 목록은 잠시 뒤 한 번만 다시 불러온다.
    const scheduleRefresh = () => {
      window.clearTimeout(timer);
      timer = window.setTimeout(refresh, 300);
    };
    refresh();
    const close = openChatSocket({
      destination: ADMIN_CHAT_DESTINATION,
      onMessage: body => { if (isChatAdminEvent(body)) scheduleRefresh(); },
      onStatus: () => {},
      onConnected: refresh
    });
    return () => {
      active = false;
      window.clearTimeout(timer);
      close();
    };
  }, []);

  return <div className="chat-widget">
    <Link className="chat-launcher" to="/admin/chat">
      상담 관리
      {waiting > 0 && <span className="chat-badge">답변 대기 {waiting}</span>}
    </Link>
  </div>;
}

function SignedOutChat({ onLogin }: { onLogin: () => void }) {
  const [open, setOpen] = useState(false);
  const launcher = useRef<HTMLButtonElement>(null);
  const close = () => { setOpen(false); launcher.current?.focus(); };
  return <div className="chat-widget">
    {open && <ChatPanel compact onClose={close}>
      <div className="chat-signin">
        <p>문의는 로그인한 회원만 남길 수 있습니다. 주문·결제 문의는 주문한 회원인지 확인한 뒤 답변하기 때문입니다.</p>
        <button className="primary-button" type="button" onClick={onLogin}>로그인하고 문의하기</button>
      </div>
    </ChatPanel>}
    <ChatLauncher open={open} unread={0} buttonRef={launcher} onToggle={() => setOpen(value => !value)} />
  </div>;
}

function CustomerChat() {
  const [open, setOpen] = useState(false);
  const [connection, setConnection] = useState<ChatConnectionStatus>("connecting");
  const launcher = useRef<HTMLButtonElement>(null);
  const thread = useChatThread({
    load: getMyChat,
    send: sendMyChatMessage,
    markRead: markMyChatRead,
    counterpart: "ADMIN",
    viewing: open
  });
  const { receive, sync } = thread;

  useEffect(() => { void sync(); }, [sync]);

  // 창을 닫아 두어도 연결을 유지해 새 답변 수를 보여준다. 다시 연결되면 놓친 메시지를 조회로 채운다.
  useEffect(() => openChatSocket({
    destination: CUSTOMER_CHAT_DESTINATION,
    onMessage: body => { if (isChatMessage(body)) receive([body], true); },
    onStatus: setConnection,
    onConnected: () => { void sync(); }
  }), [receive, sync]);

  const close = () => { setOpen(false); launcher.current?.focus(); };

  return <div className="chat-widget">
    {open && <ChatPanel onClose={close}>
      {!thread.loaded && !thread.error && <p className="chat-status" role="status">상담 내용을 불러오고 있습니다.</p>}
      {thread.loaded && connection === "reconnecting" && <p className="chat-status" role="status">
        연결이 끊겨 다시 연결하고 있습니다. 새 답변은 연결되면 표시됩니다.
      </p>}
      {thread.error && <p className="error chat-error" role="alert">
        {thread.error} <button className="text-button" type="button" onClick={() => void sync()}>다시 불러오기</button>
      </p>}
      <ChatThread
        viewer="CUSTOMER"
        counterpartName="MODO CLUB"
        messages={thread.messages}
        pending={thread.pending}
        hasMore={thread.hasMore}
        loadingOlder={thread.loadingOlder}
        emptyText="사이즈, 주문, 결제 상태 등 궁금한 점을 남겨주세요. 주문 문의는 주문번호를 함께 적어주세요."
        inputLabel="문의 내용"
        placeholder="문의 내용을 입력하세요"
        autoFocus
        onLoadOlder={() => void thread.loadOlder()}
        onSend={content => void thread.send(content)}
        onRetry={thread.retry}
      />
      <p className="chat-privacy">카드 번호, 비밀번호 같은 결제·계정 정보는 보내지 마세요.</p>
    </ChatPanel>}
    <ChatLauncher open={open} unread={thread.unread} buttonRef={launcher} onToggle={() => setOpen(value => !value)} />
  </div>;
}

interface ChatPanelProps {
  /** 대화 목록 없이 안내만 보여줄 때 내용 높이에 맞춘다. */
  compact?: boolean;
  onClose: () => void;
  children: ReactNode;
}

function ChatPanel({ compact, onClose, children }: ChatPanelProps) {
  function handleKeyDown(event: KeyboardEvent<HTMLElement>) {
    if (event.key === "Escape") onClose();
  }
  return <section className={`chat-panel${compact ? " compact" : ""}`} id="chat-panel" aria-labelledby="chat-title"
    onKeyDown={handleKeyDown}>
    <div className="chat-panel-header">
      <div>
        <strong id="chat-title">MODO CLUB 문의</strong>
        <small>쇼핑몰 관리자가 확인하고 답변합니다.</small>
      </div>
      <button className="chat-close" type="button" onClick={onClose}>닫기</button>
    </div>
    {children}
  </section>;
}

interface ChatLauncherProps {
  open: boolean;
  unread: number;
  buttonRef: RefObject<HTMLButtonElement | null>;
  onToggle: () => void;
}

function ChatLauncher({ open, unread, buttonRef, onToggle }: ChatLauncherProps) {
  return <button ref={buttonRef} className="chat-launcher" type="button" aria-expanded={open} aria-controls="chat-panel"
    onClick={onToggle}>
    {open ? "문의 닫기" : "문의하기"}
    {!open && unread > 0 && <span className="chat-badge">새 답변 {unread}</span>}
  </button>;
}
