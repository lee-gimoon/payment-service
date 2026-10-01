import { useCallback, useEffect, useRef, useState } from "react";
import type { ChatPage } from "../api/chatApi";
import {
  lastMessageId,
  mergeMessages,
  unseenMessages,
  withoutDelivered,
  type PendingChatMessage
} from "../lib/chatMessages";
import type { ChatHistory, ChatMessage, ChatSender } from "../types/chat";

interface ChatThreadSource {
  load: (page?: ChatPage) => Promise<ChatHistory>;
  send: (clientMessageId: string, content: string) => Promise<ChatMessage>;
  markRead: (lastReadMessageId: number) => Promise<void>;
  /** 상대 쪽. 상대 메시지가 새로 오면 안 읽은 수를 늘린다. */
  counterpart: ChatSender;
  /** 대화를 화면에 보여주고 있는지. 보고 있으면 안 읽은 메시지를 바로 읽음으로 표시한다. */
  viewing: boolean;
  onRead?: (lastReadMessageId: number) => void;
}

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback;
}

/**
 * 한 대화의 메시지, 보내는 중인 메시지, 안 읽은 수를 관리한다.
 * 서버 메시지는 번호로 합치므로 HTTP 응답·WebSocket 알림·다시 연결한 뒤 조회가 겹쳐도 한 번만 보인다.
 */
export function useChatThread(source: ChatThreadSource) {
  const sourceRef = useRef(source);
  useEffect(() => { sourceRef.current = source; });
  const messagesRef = useRef<ChatMessage[]>([]);
  // HTTP 전송 응답이나 실시간 알림은 중간 메시지를 건너뛸 수 있다.
  // 복구 위치는 대화 조회로 빠짐없이 받은 지점만 기록한다. 0은 조회한 빈 대화다.
  const syncedThroughRef = useRef<number | null>(null);
  const syncQueueRef = useRef<Promise<void>>(Promise.resolve());
  const readThroughRef = useRef(0);
  const unreadRevisionRef = useRef(0);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [pending, setPending] = useState<PendingChatMessage[]>([]);
  const [hasMore, setHasMore] = useState(false);
  const [unread, setUnread] = useState(0);
  const [loaded, setLoaded] = useState(false);
  const [loadingOlder, setLoadingOlder] = useState(false);
  const [error, setError] = useState("");

  /** countUnread는 실시간 알림으로 온 메시지에만 쓴다. 조회 결과는 서버가 준 안 읽은 수를 따른다. */
  const receive = useCallback((incoming: ChatMessage[], countUnread = false) => {
    if (!incoming.length) return;
    const fresh = unseenMessages(messagesRef.current, incoming);
    messagesRef.current = mergeMessages(messagesRef.current, incoming);
    setMessages(messagesRef.current);
    setPending(current => withoutDelivered(current, incoming));
    const replies = fresh.filter(message => message.sender === sourceRef.current.counterpart
      && message.id > readThroughRef.current).length;
    if (countUnread && replies) {
      unreadRevisionRef.current += 1;
      setUnread(count => count + replies);
    }
  }, []);

  const unreadAfter = useCallback((lastRead: number) => messagesRef.current.filter(message =>
    message.sender === sourceRef.current.counterpart && message.id > lastRead).length, []);

  /** 겹친 조회는 순서대로 실행하고, 실패해도 다음 조회는 마지막으로 채운 지점부터 재개한다. */
  const sync = useCallback(() => {
    const next = syncQueueRef.current.then(async () => {
      try {
        let more = true;
        while (more) {
          const after = syncedThroughRef.current;
          const readAtStart = readThroughRef.current;
          const unreadRevision = unreadRevisionRef.current;
          const page = await sourceRef.current.load(after === null ? undefined : { after });
          receive(page.messages);
          if (after === null) setHasMore(page.hasMore);
          syncedThroughRef.current = lastMessageId(page.messages) ?? after ?? 0;
          const unreadChanged = unreadRevisionRef.current !== unreadRevision;
          // 조회 중 새 답변이나 읽음 완료가 왔다면 오래된 집계로 덮지 않고 다음 조회로 맞춘다.
          if (!unreadChanged) setUnread(page.unreadCount);
          else if (readThroughRef.current > readAtStart) setUnread(unreadAfter(readThroughRef.current));
          // 첫 조회의 hasMore는 이전 대화 유무다. 이후 조회만 다음 페이지로 이어 간다.
          more = unreadChanged || (after !== null && page.hasMore && page.messages.length > 0);
        }
        setLoaded(true);
        setError("");
      } catch (requestError) {
        setError(errorMessage(requestError, "상담 내용을 불러오지 못했습니다."));
      }
    });
    syncQueueRef.current = next;
    return next;
  }, [receive, unreadAfter]);

  const loadOlder = useCallback(async () => {
    const first = messagesRef.current[0];
    if (!first) return;
    setLoadingOlder(true);
    try {
      const page = await sourceRef.current.load({ before: first.id });
      receive(page.messages);
      setHasMore(page.hasMore);
    } catch (requestError) {
      setError(errorMessage(requestError, "이전 대화를 불러오지 못했습니다."));
    } finally {
      setLoadingOlder(false);
    }
  }, [receive]);

  /** 실패한 메시지는 같은 clientMessageId로 다시 보내므로, 이미 저장됐어도 한 번만 남는다. */
  const send = useCallback(async (content: string, clientMessageId: string = crypto.randomUUID()) => {
    setPending(current => [
      ...current.filter(message => message.clientMessageId !== clientMessageId),
      { clientMessageId, content, failed: false }
    ]);
    try {
      receive([await sourceRef.current.send(clientMessageId, content)]);
    } catch {
      // 실패는 말풍선에 표시하고, 다시 보내기로 같은 메시지를 다시 보낸다.
      setPending(current => current.map(message =>
        message.clientMessageId === clientMessageId ? { ...message, failed: true } : message));
    }
  }, [receive]);

  const retry = useCallback((message: PendingChatMessage) => {
    void send(message.content, message.clientMessageId);
  }, [send]);

  useEffect(() => {
    if (!source.viewing || unread === 0) return;
    const last = lastMessageId(messages);
    if (last === null) return;
    sourceRef.current.markRead(last)
      .then(() => {
        // 요청 이후 도착한 답변은 남기고, 응답 순서가 뒤바뀌어도 읽음 위치는 되돌리지 않는다.
        if (last > readThroughRef.current) {
          readThroughRef.current = last;
          unreadRevisionRef.current += 1;
        }
        setUnread(unreadAfter(readThroughRef.current));
        sourceRef.current.onRead?.(readThroughRef.current);
      })
      .catch(() => {
        // 읽음 표시에 실패해도 대화는 계속 볼 수 있다. 다음 메시지나 조회 때 다시 표시한다.
      });
  }, [source.viewing, unread, messages, unreadAfter]);

  return { messages, pending, hasMore, unread, loaded, loadingOlder, error, receive, sync, loadOlder, send, retry };
}
