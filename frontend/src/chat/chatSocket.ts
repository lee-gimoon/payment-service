import { Client } from "@stomp/stompjs";
import { apiServer } from "../api/http";
import { getAccessToken } from "../auth/keycloak";

/**
 * 숨긴 탭이 연결을 붙잡고 있으면 heartbeat가 오가서 운영 서버가 잠들지 못한다.
 * 탭을 이만큼 숨겨 두면 연결을 닫고, 다시 보면 연결한다.
 */
const HIDDEN_DISCONNECT_MS = 2 * 60_000;

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
 * 탭을 오래 숨기면 연결을 닫았다가 다시 볼 때 연결하고, 놓친 메시지는 onConnected의 조회로 채운다.
 */
export function openChatSocket({ destination, onMessage, onStatus, onConnected }: ChatSocketOptions): () => void {
  let active = true;
  let connectedOnce = false;
  let hiddenTimer: ReturnType<typeof setTimeout> | undefined;
  const client = new Client({
    brokerURL: socketUrl(),
    reconnectDelay: 3000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    // 브라우저 WebSocket은 연결 요청에 Authorization 헤더를 붙일 수 없으므로 STOMP CONNECT 프레임에 담는다.
    beforeConnect: async () => {
      await apiServer.whenAwake();
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
  const onVisibilityChange = () => {
    clearTimeout(hiddenTimer);
    if (document.visibilityState === "hidden") {
      hiddenTimer = setTimeout(() => void client.deactivate(), HIDDEN_DISCONNECT_MS);
    } else if (!client.active) {
      client.activate();
    }
  };
  onStatus("connecting");
  client.activate();
  document.addEventListener("visibilitychange", onVisibilityChange);
  onVisibilityChange();
  return () => {
    active = false;
    clearTimeout(hiddenTimer);
    document.removeEventListener("visibilitychange", onVisibilityChange);
    void client.deactivate();
  };
}

function socketUrl(): string {
  const scheme = window.location.protocol === "https:" ? "wss" : "ws";
  return `${scheme}://${window.location.host}/ws`;
}
