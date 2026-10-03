import { defineConfig, type ProxyOptions } from "vite";
import react from "@vitejs/plugin-react";

// /orders/… 같은 화면 주소를 새로고침하거나 로그인 후 돌아올 때는 API 대신 React 화면을 준다.
function api(target: string): ProxyOptions {
  return {
    target,
    bypass: request => request.headers.accept?.includes("text/html") ? "/index.html" : undefined
  };
}

export default defineConfig({
  plugins: [react()],
  server: {
    host: "127.0.0.1",
    port: 5173,
    headers: { "Cross-Origin-Opener-Policy": "same-origin-allow-popups" },
    proxy: {
      "/orders": api("http://127.0.0.1:8080"),
      "/payment-attempts": api("http://127.0.0.1:8080"),
      "/products": api("http://127.0.0.1:8080"),
      "/payments": api("http://127.0.0.1:8080"),
      "/payment-config": api("http://127.0.0.1:8080"),
      "/chat": api("http://127.0.0.1:8080"),
      "/admin": api("http://127.0.0.1:8080"),
      // 마이페이지 API. 화면 주소 /mypage와 겹치지 않게 슬래시까지 맞춘다.
      "/me/": api("http://127.0.0.1:8080"),
      // 상담 실시간 알림(STOMP over WebSocket)
      "/ws": { target: "ws://127.0.0.1:8080", ws: true }
    }
  },
  preview: {
    host: "127.0.0.1",
    port: 4173
  }
});
