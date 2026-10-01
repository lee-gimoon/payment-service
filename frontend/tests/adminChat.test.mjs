import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { test } from "node:test";
import * as React from "react";
import { transformSync } from "rolldown/utils";
import * as hooks from "./helpers/chatThreadHarness.mjs";

// Compile the actual component/API source. Replace its external dependencies so
// the tests can control server responses without a browser or new test packages.
async function loadSource(path, name, scope) {
  const source = (await readFile(new URL(`../src/${path}`, import.meta.url), "utf8"))
    .replace(/^import [\s\S]*?;\r?\n/gm, "").replace(/^export /gm, "");
  const { code, errors } = transformSync(path, source, { jsx: { runtime: "classic" } });
  assert.equal(errors.length, 0);
  return new Function(...Object.keys(scope), `${code}\nreturn ${name};`)(...Object.values(scope));
}

function room(roomId, waiting) {
  return {
    roomId, customerName: roomId, customerEmail: `${roomId}@modo.test`, waiting, unreadCount: waiting ? 1 : 0,
    lastMessage: { id: 1, sender: waiting ? "CUSTOMER" : "ADMIN", content: roomId, createdAt: "2026-10-01T01:00:00Z" }
  };
}

function nodes(tree) {
  if (Array.isArray(tree)) return tree.flatMap(nodes);
  if (!tree || typeof tree !== "object") return [];
  return [tree, ...nodes(tree.props?.children)];
}

function text(tree) {
  if (Array.isArray(tree)) return tree.map(text).join("");
  if (tree && typeof tree === "object") return text(tree.props?.children);
  return tree === null || tree === undefined || typeof tree === "boolean" ? "" : String(tree);
}

async function mount(t, target, getChatRooms, selectedRoomId) {
  let socket;
  const name = target === "console" ? "AdminChatConsole" : "AdminChatShortcut";
  const file = target === "console" ? "pages/AdminChatPage.tsx" : "components/ChatWidget.tsx";
  const Component = await loadSource(file, name, {
    ...hooks, React, Link: "a", getChatRooms,
    useParams: () => ({ roomId: selectedRoomId }),
    ADMIN_CHAT_DESTINATION: "/topic/admin/chat",
    openChatSocket: options => { socket = options; return () => {}; },
    chatListTimeLabel: () => "10:00",
    window: { setTimeout, clearTimeout }
  });
  const renderer = await hooks.createRenderer(Component);
  t.after(() => renderer.dispose());
  return { renderer, get socket() { return socket; } };
}

test("답변 대기 API는 서버 필터를 요청한다", async () => {
  const paths = [];
  const getChatRooms = await loadSource("api/chatApi.ts", "getChatRooms", {
    signedIn: async () => ({}), request: async path => { paths.push(path); return []; }
  });
  await getChatRooms();
  await getChatRooms(true);
  assert.deepEqual(paths, ["/admin/chat/rooms", "/admin/chat/rooms?waiting=true"]);
});

test("최근 100개가 답변 완료여도 대기 필터는 오래된 미답변 상담을 표시한다", async t => {
  const all = Array.from({ length: 100 }, (_, index) => room(`answered-${index}`, false));
  const waiting = room("old-waiting", true);
  const { renderer } = await mount(t, "console", async waitingOnly => waitingOnly ? [waiting] : all, waiting.roomId);
  const waitingButton = nodes(renderer.current).find(node => node.type === "button" && text(node).startsWith("답변 대기"));
  assert.equal(text(waitingButton), "답변 대기 1");
  waitingButton.props.onClick();
  await renderer.flush();
  const roomLinks = nodes(renderer.current).filter(node => node.type === "a" && node.props.className?.startsWith("chat-room"));
  assert.deepEqual(roomLinks.map(link => link.props.to), ["/admin/chat/old-waiting"]);
  const selected = nodes(renderer.current).find(node => typeof node.type === "function" && node.props.roomId === waiting.roomId);
  assert.equal(selected.props.room.customerName, waiting.customerName);
});

test("관리자 바로가기도 전체 목록 밖의 미답변 상담 수를 표시한다", async t => {
  const all = Array.from({ length: 100 }, (_, index) => room(`answered-${index}`, false));
  const { renderer } = await mount(t, "shortcut", async waitingOnly => waitingOnly ? [room("old-waiting", true)] : all);
  assert.equal(text(renderer.current), "상담 관리답변 대기 1");
});

for (const target of ["console", "shortcut"]) {
  test(`${target}의 늦게 도착한 이전 조회가 최신 답변 대기 수를 덮어쓰지 않는다`, async t => {
    let latest = false;
    const previous = [];
    const mounted = await mount(t, target, waitingOnly => {
      if (!latest) return new Promise(resolve => previous.push({ waitingOnly, resolve }));
      return Promise.resolve(waitingOnly ? [room("latest", true)] : []);
    });
    latest = true;
    mounted.socket.onConnected();
    await mounted.renderer.flush();
    for (const request of previous) request.resolve(request.waitingOnly ? [] : [room("old", false)]);
    await mounted.renderer.flush();
    assert.ok(text(mounted.renderer.current).includes("답변 대기 1"));
  });
}
