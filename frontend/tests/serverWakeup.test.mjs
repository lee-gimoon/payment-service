import assert from "node:assert/strict";
import { afterEach, test } from "node:test";
import { isWaking, SleepingServer, subscribeWaking } from "../src/lib/serverWakeup.ts";

const realFetch = globalThis.fetch;
afterEach(() => { globalThis.fetch = realFetch; });

const fast = { probeIntervalMs: 1, probeTimeoutMs: 1_000, giveUpAfterMs: 1_000, noticeDelayMs: 0 };
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));

/** 응답을 차례로 돌려주는 fetch 대역. Error는 네트워크 오류로 던진다. */
function answer(...results) {
  const calls = [];
  globalThis.fetch = async url => {
    calls.push(url);
    const result = results.length > 1 ? results.shift() : results[0];
    if (result instanceof Error) throw result;
    return new Response("{}", { status: result });
  };
  return calls;
}

test("서버를 재우지 않는 환경에서는 확인 요청 없이 바로 진행한다", async () => {
  const calls = answer(200);
  await new SleepingServer("/payment-config", false, fast).whenAwake(true);
  assert.equal(calls.length, 0);
});

test("깨어날 때까지 확인 요청을 되풀이하고, 깨어난 뒤에는 한동안 확인하지 않는다", async () => {
  const calls = answer(new TypeError("Failed to fetch"), 502, 200);
  const server = new SleepingServer("/payment-config", true, fast);
  await server.whenAwake();
  assert.deepEqual(calls, ["/payment-config", "/payment-config", "/payment-config"]);
  await server.whenAwake();
  assert.equal(calls.length, 3);
});

test("동시에 기다려도 확인 요청은 한 줄로 보낸다", async () => {
  const calls = answer(502, 200);
  const server = new SleepingServer("/payment-config", true, fast);
  await Promise.all([server.whenAwake(), server.whenAwake(), server.whenAwake()]);
  assert.equal(calls.length, 2);
});

test("실제 응답을 받은 서버는 확인하지 않고, force면 다시 확인한다", async () => {
  const calls = answer(200);
  const server = new SleepingServer("/payment-config", true, fast);
  server.markAwake();
  await server.whenAwake();
  assert.equal(calls.length, 0);
  await server.whenAwake(true);
  assert.equal(calls.length, 1);
});

test("포기 시간이 지나면 기다리기를 멈춰 실제 요청이 오류를 알리게 한다", async () => {
  answer(502);
  const server = new SleepingServer("/payment-config", true, { ...fast, giveUpAfterMs: 20 });
  await server.whenAwake();
  assert.equal(isWaking(), false);
});

test("깨우는 동안 안내 상태를 알리고, 끝나면 내린다", async () => {
  let wake;
  globalThis.fetch = () => new Promise(resolve => { wake = () => resolve(new Response("{}", { status: 200 })); });
  const changes = [];
  const unsubscribe = subscribeWaking(() => changes.push(isWaking()));
  const waiting = new SleepingServer("/payment-config", true, fast).whenAwake();
  await pause(10);
  assert.equal(isWaking(), true);
  wake();
  await waiting;
  unsubscribe();
  assert.equal(isWaking(), false);
  assert.deepEqual(changes, [true, false]);
});
