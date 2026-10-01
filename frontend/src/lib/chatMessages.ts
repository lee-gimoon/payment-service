import type { ChatAdminEvent, ChatMessage } from "../types/chat";

export const MAX_CHAT_LENGTH = 1000;

export function isChatMessage(value: unknown): value is ChatMessage {
  if (typeof value !== "object" || value === null) return false;
  const message = value as Record<string, unknown>;
  return typeof message.id === "number" && typeof message.clientMessageId === "string"
    && (message.sender === "CUSTOMER" || message.sender === "ADMIN")
    && typeof message.content === "string" && typeof message.createdAt === "string";
}

export function isChatAdminEvent(value: unknown): value is ChatAdminEvent {
  if (typeof value !== "object" || value === null) return false;
  const event = value as Record<string, unknown>;
  return typeof event.roomId === "string" && isChatMessage(event.message);
}

/** 서버에 저장되기 전의 내 메시지. 실패하면 같은 clientMessageId로 다시 보낸다. */
export interface PendingChatMessage {
  clientMessageId: string;
  content: string;
  failed: boolean;
}

/**
 * 서버 메시지를 번호 순으로 합친다. 같은 메시지가 HTTP 응답, WebSocket 알림, 다시 연결한 뒤 조회로
 * 여러 번 와도 한 번만 남는다.
 */
export function mergeMessages(current: readonly ChatMessage[], incoming: readonly ChatMessage[]): ChatMessage[] {
  const byId = new Map(current.map(message => [message.id, message]));
  for (const message of incoming) byId.set(message.id, message);
  return [...byId.values()].sort((a, b) => a.id - b.id);
}

/** 서버에 저장된 것으로 확인된 메시지는 보내는 중 목록에서 뺀다. */
export function withoutDelivered(
  pending: readonly PendingChatMessage[],
  delivered: readonly ChatMessage[]
): PendingChatMessage[] {
  const ids = new Set(delivered.map(message => message.clientMessageId));
  return pending.filter(message => !ids.has(message.clientMessageId));
}

/** 아직 목록에 없던 메시지만 고른다. 새 답변 수를 셀 때 쓴다. */
export function unseenMessages(current: readonly ChatMessage[], incoming: readonly ChatMessage[]): ChatMessage[] {
  const known = new Set(current.map(message => message.id));
  return incoming.filter(message => !known.has(message.id));
}

export function lastMessageId(messages: readonly ChatMessage[]): number | null {
  return messages.length ? messages[messages.length - 1].id : null;
}

/** 서버와 같은 규칙으로 앞뒤 공백을 지운 내용이 1~1000자면 보낼 수 있다. */
export function sendableContent(text: string): string | null {
  const content = text.trim();
  return content && content.length <= MAX_CHAT_LENGTH ? content : null;
}

export function chatDayLabel(value: string): string {
  return new Date(value).toLocaleDateString("ko-KR", { year: "numeric", month: "long", day: "numeric", weekday: "short" });
}

export function chatTimeLabel(value: string): string {
  return new Date(value).toLocaleTimeString("ko-KR", { hour: "numeric", minute: "2-digit" });
}

/** 목록용 짧은 시각. 오늘이면 시각, 아니면 날짜를 보여준다. */
export function chatListTimeLabel(value: string, now = new Date()): string {
  const date = new Date(value);
  return date.toDateString() === now.toDateString()
    ? chatTimeLabel(value)
    : date.toLocaleDateString("ko-KR", { month: "numeric", day: "numeric" });
}
