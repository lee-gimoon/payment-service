import { signedIn } from "../auth/keycloak";
import type { ChatHistory, ChatMessage, ChatRoom } from "../types/chat";
import { jsonBody, request } from "./http";

export interface ChatPage {
  /** 이 번호보다 이전 메시지 (이전 대화 더 보기) */
  before?: number;
  /** 이 번호보다 이후 메시지 (다시 연결한 뒤 놓친 메시지 채우기) */
  after?: number;
}

function query({ before, after }: ChatPage = {}): string {
  const params = new URLSearchParams();
  if (before !== undefined) params.set("before", String(before));
  if (after !== undefined) params.set("after", String(after));
  const text = params.toString();
  return text ? `?${text}` : "";
}

function roomPath(roomId: string): string {
  return `/admin/chat/rooms/${encodeURIComponent(roomId)}`;
}

export async function getMyChat(page?: ChatPage): Promise<ChatHistory> {
  return request<ChatHistory>(`/chat/messages${query(page)}`, await signedIn());
}

/** 같은 clientMessageId로 다시 보내면 서버가 새로 저장하지 않고 저장된 메시지를 돌려준다. */
export async function sendMyChatMessage(clientMessageId: string, content: string): Promise<ChatMessage> {
  return request<ChatMessage>("/chat/messages", await signedIn(jsonBody("POST", { clientMessageId, content })));
}

export async function markMyChatRead(lastReadMessageId: number): Promise<void> {
  return request<void>("/chat/read", await signedIn(jsonBody("POST", { lastReadMessageId })));
}

export async function getChatRooms(waitingOnly = false): Promise<ChatRoom[]> {
  return request<ChatRoom[]>(`/admin/chat/rooms${waitingOnly ? "?waiting=true" : ""}`, await signedIn());
}

export async function getChatRoomMessages(roomId: string, page?: ChatPage): Promise<ChatHistory> {
  return request<ChatHistory>(`${roomPath(roomId)}/messages${query(page)}`, await signedIn());
}

export async function sendChatReply(roomId: string, clientMessageId: string, content: string): Promise<ChatMessage> {
  return request<ChatMessage>(`${roomPath(roomId)}/messages`,
    await signedIn(jsonBody("POST", { clientMessageId, content })));
}

export async function markChatRoomRead(roomId: string, lastReadMessageId: number): Promise<void> {
  return request<void>(`${roomPath(roomId)}/read`, await signedIn(jsonBody("POST", { lastReadMessageId })));
}
