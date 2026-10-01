import { Client } from "@stomp/stompjs";
import { getAccessToken } from "../auth/keycloak";

/** 고객이 구독하는 주소. 서버가 로그인한 회원에게만 전달한다. */
export const CUSTOMER_CHAT_DESTINATION = "/user/queue/chat";
/** 쇼핑몰 관리자가 구독하는 주소. 모든 상담방의 새 메시지가 온다. */
export const ADMIN_CHAT_DESTINATION = "/topic/admin/chat";

export type ChatConnectionStatus = "connecting" | "connected" | "reconnecting";

interface ChatSocketOptions {
  destination: string;
  onMessage: (body: unknown) => void;
  onStatus: (status: ChatConnectionStatus) => void;
  /** 연결될 때마다 부른다. 끊긴 동안 놓친 메시지는 알림으로 다시 오지 않으므로 조회로 채운다. */
  onConnected: () => void;
}

/**
 * 새 메시지 알림을 받는 WebSocket(STOMP) 연결을 연다. 메시지 보내기는 HTTP API로 한다.
 * 연결이 끊기면 3초 뒤 새 access token으로 다시 연결한다. 반환한 함수를 부르면 연결을 닫는다.
 */
export function openChatSocket({ destination, onMessage, onStatus, onConnected }: ChatSocketOptions): () => void {
  let active = true;
  let connectedOnce = false;
  const client = new Client({
    brokerURL: socketUrl(),
    reconnectDelay: 3000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    // 브라우저 WebSocket은 연결 요청에 Authorization 헤더를 붙일 수 없으므로 STOMP CONNECT 프레임에 담는다.
    beforeConnect: async () => {
      const token = await getAccessToken();
      client.connectHeaders = token ? { Authorization: `Bearer ${token}` } : {};
    },
    onConnect: () => {
      if (!active) return;
      client.subscribe(destination, frame => {
        try {
          onMessage(JSON.parse(frame.body));
        } catch {
          // 형식이 다른 알림은 무시한다. 다음 조회에서 서버 값으로 맞춰진다.
        }
      });
      connectedOnce = true;
      onStatus("connected");
      onConnected();
    },
    onWebSocketClose: () => {
      if (active) onStatus(connectedOnce ? "reconnecting" : "connecting");
    }
  });
  onStatus("connecting");
  client.activate();
  return () => {
    active = false;
    void client.deactivate();
  };
}

function socketUrl(): string {
  const scheme = window.location.protocol === "https:" ? "wss" : "ws";
  return `${scheme}://${window.location.host}/ws`;
}
