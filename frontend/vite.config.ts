import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    host: "127.0.0.1",
    port: 5173,
    headers: { "Cross-Origin-Opener-Policy": "same-origin-allow-popups" },
    proxy: {
      "/orders": "http://127.0.0.1:8080",
      "/payment-attempts": "http://127.0.0.1:8080",
      "/products": "http://127.0.0.1:8080",
      "/payments": "http://127.0.0.1:8080",
      "/payment-config": "http://127.0.0.1:8080"
    }
  },
  preview: {
    host: "127.0.0.1",
    port: 4173
  }
});
