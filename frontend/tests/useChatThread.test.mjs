import assert from "node:assert/strict";
import { test } from "node:test";
import { createChatThread } from "./helpers/chatThreadHarness.mjs";

function message(id, sender = "ADMIN", clientMessageId = `message-${id}`) {
  return { id, sender, clientMessageId, content: `메시지 ${id}`, createdAt: "2026-10-01T01:00:00Z" };
}

function history(messages, unreadCount = 0, hasMore = false) {
  return { messages, unreadCount, hasMore };
}

function deferred() {
  let resolve, reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}

async function mount(t, overrides = {}) {
  const thread = await createChatThread({
    counterpart: "ADMIN", viewing: false,
    load: async () => history([]),
    send: async () => { throw new Error("Unexpected send"); },
    markRead: async () => {},
    ...overrides
  });
  t.after(() => thread.dispose());
  return thread;
}

const ids = thread => thread.current.messages.map(item => item.id);

test("재연결은 내 전송 응답이나 실시간 알림 전에 놓친 답변도 복구한다", async t => {
  let stored = [message(1, "CUSTOMER")];
  const thread = await mount(t, {
    load: async page => history(page ? stored.filter(item => item.id > page.after) : stored),
    send: async clientId => ({ ...stored.at(-1), clientMessageId: clientId })
  });
  await thread.current.sync();
  stored = [...stored, message(2), message(3, "CUSTOMER")];
  await thread.current.send("추가 질문", "own-3");
  await thread.current.sync();
  await thread.flush();
  assert.deepEqual(ids(thread), [1, 2, 3]);

  stored = [...stored, message(4), message(5)];
  thread.current.receive([stored.at(-1)], true);
  await thread.current.sync();
  await thread.flush();
  assert.deepEqual(ids(thread), [1, 2, 3, 4, 5]);
});

test("처음 대화가 비어 있어도 새 실시간 메시지 앞의 누락분을 가져온다", async t => {
  let stored = [];
  const thread = await mount(t, {
    load: async page => history(page ? stored.filter(item => item.id > page.after) : stored)
  });
  await thread.current.sync();
  stored = [message(1), message(2), message(3)];
  thread.current.receive([stored[2]], true);
  await thread.current.sync();
  await thread.flush();
  assert.deepEqual(ids(thread), [1, 2, 3]);
});

test("복구 조회가 실패하는 동안 더 큰 번호를 받아도 재시도는 누락분을 복구한다", async t => {
  let stored = [message(1, "CUSTOMER")];
  let failure;
  const thread = await mount(t, {
    load: async page => {
      if (failure) { const request = failure; failure = undefined; return request.promise; }
      return history(page ? stored.filter(item => item.id > page.after) : stored);
    },
    send: async clientId => ({ ...stored.at(-1), clientMessageId: clientId })
  });
  await thread.current.sync();
  const failedRequest = deferred();
  failure = failedRequest;
  const firstSync = thread.current.sync();
  await thread.flush();
  stored = [...stored, message(2), message(3), message(4, "CUSTOMER")];
  await thread.current.send("추가 질문", "own-4");
  failedRequest.reject(new Error("연결 끊김"));
  await firstSync;
  await thread.flush();
  assert.equal(thread.current.error, "연결 끊김");
  await thread.current.sync();
  await thread.flush();
  assert.deepEqual(ids(thread), [1, 2, 3, 4]);
  assert.equal(thread.current.error, "");
});

test("복구 요청이 겹치고 여러 페이지로 나뉘어도 다음 재연결이 중간 메시지를 건너뛰지 않는다", async t => {
  let stored = [message(1, "CUSTOMER")];
  const blocked = deferred();
  let hold = false;
  const thread = await mount(t, {
    load: async page => {
      const remaining = page ? stored.filter(item => item.id > page.after) : stored;
      const result = history(remaining.slice(0, 1), 0, remaining.length > 1);
      if (hold) { await blocked.promise; }
      return result;
    },
    send: async clientId => ({ ...stored.at(-1), clientMessageId: clientId })
  });
  await thread.current.sync();
  stored = [...stored, message(2), message(3)];
  hold = true;
  const syncs = [thread.current.sync(), thread.current.sync()];
  await thread.flush();
  hold = false;
  blocked.resolve();
  await Promise.all(syncs);
  await thread.flush();
  assert.deepEqual(ids(thread), [1, 2, 3]);

  stored = [...stored, message(4), message(5, "CUSTOMER")];
  await thread.current.send("질문", "own-5");
  await thread.current.sync();
  await thread.flush();
  assert.deepEqual(ids(thread), [1, 2, 3, 4, 5]);
});

test("창을 닫은 뒤 도착한 답변은 이전 읽음 요청이 완료되어도 안 읽음으로 남는다", async t => {
  const read = deferred();
  const requested = [];
  const thread = await mount(t, {
    viewing: true,
    load: async () => history([message(1)], 1),
    markRead: id => { requested.push(id); return read.promise; }
  });
  await thread.current.sync();
  await thread.flush();
  assert.deepEqual(requested, [1]);
  await thread.setViewing(false);
  thread.current.receive([message(2)], true);
  await thread.flush();
  read.resolve();
  await thread.flush();
  assert.equal(thread.current.unread, 1);
  assert.deepEqual(requested, [1]);
});

test("읽음 응답이 역순으로 와도 이미 읽은 답변을 다시 세거나 새 답변 알림을 지우지 않는다", async t => {
  const requests = [];
  const thread = await mount(t, {
    viewing: true,
    load: async () => history([message(1)], 1),
    markRead: id => { const read = deferred(); requests.push({ id, ...read }); return read.promise; }
  });
  await thread.current.sync();
  await thread.flush();
  thread.current.receive([message(2)], true);
  await thread.flush();
  const first = requests.find(request => request.id === 1);
  const second = requests.find(request => request.id === 2);
  assert.ok(first);
  assert.ok(second);
  await thread.setViewing(false);
  thread.current.receive([message(3)], true);
  await thread.flush();
  second.resolve();
  await thread.flush();
  assert.equal(thread.current.unread, 1);
  first.resolve();
  await thread.flush();
  assert.equal(thread.current.unread, 1);
});

test("읽음 요청이 실패하면 알림을 유지하고 창을 다시 열었을 때 재시도한다", async t => {
  let calls = 0;
  const thread = await mount(t, {
    viewing: true,
    load: async () => history([message(1)], 1),
    markRead: async () => { if (++calls === 1) throw new Error("연결 끊김"); }
  });
  await thread.current.sync();
  await thread.flush();
  assert.equal(thread.current.unread, 1);
  assert.equal(calls, 1);
  await thread.setViewing(false);
  await thread.setViewing(true);
  assert.equal(calls, 2);
  assert.equal(thread.current.unread, 0);
});

test("늦게 도착한 대화 조회가 조회 중 받은 새 답변 배지를 지우지 않는다", async t => {
  const oldSnapshot = deferred();
  const freshSnapshot = deferred();
  let calls = 0;
  const thread = await mount(t, {
    load: () => ++calls === 1 ? oldSnapshot.promise : freshSnapshot.promise
  });
  const sync = thread.current.sync();
  await thread.flush();
  thread.current.receive([message(1)], true);
  await thread.flush();
  assert.equal(thread.current.unread, 1);
  oldSnapshot.resolve(history([], 0));
  await thread.flush();
  assert.equal(thread.current.unread, 1);
  freshSnapshot.resolve(history([message(1)], 1));
  await sync;
  await thread.flush();
  assert.deepEqual(ids(thread), [1]);
  assert.equal(thread.current.unread, 1);
});

test("조회 중 읽음 처리가 완료되면 오래된 조회 결과로 읽은 답변을 다시 세지 않는다", async t => {
  const read = deferred();
  const oldSnapshot = deferred();
  let loads = 0;
  const thread = await mount(t, {
    viewing: true,
    load: () => {
      if (++loads === 1) return Promise.resolve(history([message(1)], 1));
      if (loads === 2) return oldSnapshot.promise;
      return Promise.resolve(history([], 0));
    },
    markRead: () => read.promise
  });
  await thread.current.sync();
  await thread.flush();
  const sync = thread.current.sync();
  await thread.flush();
  await thread.setViewing(false);
  read.resolve();
  await thread.flush();
  assert.equal(thread.current.unread, 0);
  oldSnapshot.resolve(history([], 1));
  await sync;
  await thread.flush();
  assert.equal(thread.current.unread, 0);
});
