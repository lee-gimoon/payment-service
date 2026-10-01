export type ChatSender = "CUSTOMER" | "ADMIN";

export interface ChatMessage {
  id: number;
  clientMessageId: string;
  sender: ChatSender;
  content: string;
  createdAt: string;
}

export interface ChatHistory {
  /** 오래된 순 */
  messages: ChatMessage[];
  /** before로 조회했으면 더 이전, after로 조회했으면 더 이후 메시지가 있는지 */
  hasMore: boolean;
  /** 조회한 쪽이 아직 읽지 않은 상대 메시지 수 */
  unreadCount: number;
}

export interface ChatRoom {
  roomId: string;
  customerName: string;
  customerEmail: string | null;
  lastMessage: ChatMessage;
  waiting: boolean;
  unreadCount: number;
}

/** 관리자 구독 주소로 오는 새 메시지 */
export interface ChatAdminEvent {
  roomId: string;
  message: ChatMessage;
}
