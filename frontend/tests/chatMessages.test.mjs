import assert from "node:assert/strict";
import { test } from "node:test";
import {
  isChatAdminEvent,
  isChatMessage,
  lastMessageId,
  MAX_CHAT_LENGTH,
  mergeMessages,
  sendableContent,
  unseenMessages,
  withoutDelivered
} from "../src/lib/chatMessages.ts";

function message(id, sender = "CUSTOMER", clientMessageId = `client-${id}`) {
  return { id, clientMessageId, sender, content: `메시지 ${id}`, createdAt: "2026-10-01T01:00:00Z" };
}

test("HTTP 응답과 WebSocket 알림으로 같은 메시지가 두 번 와도 한 번만 번호 순으로 남긴다", () => {
  const fromResponse = [message(3)];
  const fromSocket = [message(3), message(1), message(2)];
  const merged = mergeMessages(fromResponse, fromSocket);
  assert.deepEqual(merged.map(item => item.id), [1, 2, 3]);
  assert.equal(lastMessageId(merged), 3);
  assert.equal(lastMessageId([]), null);
});

test("서버에 저장된 메시지만 보내는 중 목록에서 뺀다", () => {
  const pending = [
    { clientMessageId: "sent", content: "보냄", failed: false },
    { clientMessageId: "failed", content: "실패", failed: true }
  ];
  assert.deepEqual(withoutDelivered(pending, [message(7, "CUSTOMER", "sent")]), [pending[1]]);
});

test("이미 받은 메시지는 새 메시지로 세지 않는다", () => {
  const current = [message(1, "ADMIN"), message(2, "ADMIN")];
  assert.deepEqual(unseenMessages(current, [message(2, "ADMIN"), message(3, "ADMIN")]).map(item => item.id), [3]);
});

test("서버와 같은 규칙으로 앞뒤 공백을 지운 1~1000자만 보낼 수 있다", () => {
  assert.equal(sendableContent("  사이즈 문의  \n"), "사이즈 문의");
  assert.equal(sendableContent(" \n "), null);
  assert.equal(sendableContent("가".repeat(MAX_CHAT_LENGTH)), "가".repeat(MAX_CHAT_LENGTH));
  assert.equal(sendableContent("가".repeat(MAX_CHAT_LENGTH + 1)), null);
});

test("형식이 맞는 실시간 알림만 받아들인다", () => {
  assert.equal(isChatMessage(message(1)), true);
  assert.equal(isChatMessage({ ...message(1), sender: "SYSTEM" }), false);
  assert.equal(isChatMessage({ ...message(1), id: "1" }), false);
  assert.equal(isChatMessage(null), false);
  assert.equal(isChatAdminEvent({ roomId: "room-1", message: message(1) }), true);
  assert.equal(isChatAdminEvent({ roomId: "room-1", message: { id: 1 } }), false);
});
