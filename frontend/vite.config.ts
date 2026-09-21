/** 파일 역할: React 빌드 플러그인, 로컬 개발 서버, Spring Boot로 전달할 개발용 API 프록시를 설정한다. */
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    host: "127.0.0.1",
    port: 5173,
    headers: { "Cross-Origin-Opener-Policy": "same-origin-allow-popups" },
    // 개발 중 같은 출처의 상대 경로로 보낸 API 요청을 8080 포트의 서버에 전달한다.
    proxy: {
      "/orders": "http://127.0.0.1:8080",
      "/payments": "http://127.0.0.1:8080",
      "/payment-config": "http://127.0.0.1:8080"
    }
  },
  preview: {
    host: "127.0.0.1",
    port: 4173
  }
});
