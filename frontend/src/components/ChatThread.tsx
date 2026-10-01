import { Fragment, useId, useLayoutEffect, useRef, useState, type FormEvent, type KeyboardEvent } from "react";
import {
  chatDayLabel,
  chatTimeLabel,
  MAX_CHAT_LENGTH,
  sendableContent,
  type PendingChatMessage
} from "../lib/chatMessages";
import type { ChatMessage, ChatSender } from "../types/chat";

interface ChatThreadProps {
  /** 이 화면을 보는 쪽. 이쪽 메시지를 오른쪽에 둔다. */
  viewer: ChatSender;
  /** 상대 말풍선 위에 표시할 이름 */
  counterpartName: string;
  messages: ChatMessage[];
  pending: PendingChatMessage[];
  hasMore: boolean;
  loadingOlder: boolean;
  emptyText: string;
  inputLabel: string;
  placeholder: string;
  autoFocus?: boolean;
  onLoadOlder: () => void;
  onSend: (content: string) => void;
  onRetry: (message: PendingChatMessage) => void;
}

/** 고객 문의 창과 관리자 상담 화면이 함께 쓰는 대화 목록과 입력란. */
export function ChatThread({
  viewer,
  counterpartName,
  messages,
  pending,
  hasMore,
  loadingOlder,
  emptyText,
  inputLabel,
  placeholder,
  autoFocus,
  onLoadOlder,
  onSend,
  onRetry
}: ChatThreadProps) {
  const inputId = useId();
  const listRef = useRef<HTMLDivElement>(null);
  const stickToBottom = useRef(true);
  const olderAnchor = useRef<{ height: number; top: number } | null>(null);
  const [draft, setDraft] = useState("");
  const content = sendableContent(draft);

  useLayoutEffect(() => {
    const list = listRef.current;
    if (!list) return;
    if (olderAnchor.current) {
      // 이전 대화를 위에 붙여도 보고 있던 위치를 유지한다.
      list.scrollTop = list.scrollHeight - olderAnchor.current.height + olderAnchor.current.top;
      olderAnchor.current = null;
    } else if (stickToBottom.current) {
      list.scrollTop = list.scrollHeight;
    }
  }, [messages, pending]);

  useLayoutEffect(() => {
    if (!loadingOlder) olderAnchor.current = null;
  }, [loadingOlder]);

  function handleScroll() {
    const list = listRef.current;
    if (list) stickToBottom.current = list.scrollHeight - list.scrollTop - list.clientHeight < 80;
  }

  function loadOlder() {
    const list = listRef.current;
    if (list) olderAnchor.current = { height: list.scrollHeight, top: list.scrollTop };
    onLoadOlder();
  }

  function submit(event?: FormEvent) {
    event?.preventDefault();
    if (!content) return;
    stickToBottom.current = true;
    onSend(content);
    setDraft("");
  }

  function handleKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    // 한글 조합 중 Enter는 글자를 확정하는 입력이므로 보내지 않는다.
    if (event.key === "Enter" && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault();
      submit();
    }
  }

  return <div className="chat-thread">
    <div className="chat-messages" ref={listRef} onScroll={handleScroll} role="log" aria-label="대화 내용">
      {hasMore && <button className="chat-older" type="button" disabled={loadingOlder} onClick={loadOlder}>
        {loadingOlder ? "이전 대화를 불러오는 중" : "이전 대화 보기"}
      </button>}
      {messages.length === 0 && pending.length === 0 && <p className="chat-empty">{emptyText}</p>}
      {messages.map((message, index) => {
        const day = chatDayLabel(message.createdAt);
        const mine = message.sender === viewer;
        return <Fragment key={message.id}>
          {(index === 0 || chatDayLabel(messages[index - 1].createdAt) !== day) && <p className="chat-day">{day}</p>}
          <div className={`chat-bubble-row${mine ? " mine" : ""}`}>
            <div className="chat-bubble">
              {!mine && <strong className="chat-author">{counterpartName}</strong>}
              <p>{message.content}</p>
              <time dateTime={message.createdAt}>{chatTimeLabel(message.createdAt)}</time>
            </div>
          </div>
        </Fragment>;
      })}
      {pending.map(message => <div key={message.clientMessageId}
        className={`chat-bubble-row mine pending${message.failed ? " failed" : ""}`}>
        <div className="chat-bubble">
          <p>{message.content}</p>
          {message.failed
            ? <span className="chat-failed" role="alert">
              보내지 못했습니다. <button className="text-button" type="button" onClick={() => onRetry(message)}>다시 보내기</button>
            </span>
            : <span className="chat-sending">보내는 중</span>}
        </div>
      </div>)}
    </div>
    <form className="chat-composer" onSubmit={submit}>
      <label className="visually-hidden" htmlFor={inputId}>{inputLabel}</label>
      <textarea
        id={inputId}
        rows={2}
        maxLength={MAX_CHAT_LENGTH}
        placeholder={placeholder}
        value={draft}
        autoFocus={autoFocus}
        onChange={event => setDraft(event.target.value)}
        onKeyDown={handleKeyDown}
      />
      <div className="chat-composer-actions">
        <small>Enter 보내기 · Shift+Enter 줄바꿈 · {draft.trim().length}/{MAX_CHAT_LENGTH}</small>
        <button className="primary-button" type="submit" disabled={!content}>보내기</button>
      </div>
    </form>
  </div>;
}
