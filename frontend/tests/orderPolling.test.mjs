import assert from "node:assert/strict";
import { test } from "node:test";
import { watchOrder } from "../src/payments/orderPolling.ts";

test("서버의 미확정·취소 진행 상태를 읽다가 취소 완료에서 자동 조회를 끝낸다", async () => {
  const states = ["UNKNOWN", "CANCEL_PENDING", "CANCELED"];
  const received = [];
  const completed = Promise.withResolvers();
  let calls = 0;
  const stop = watchOrder(async () => ({ payment: { status: states[calls++] } }), order => {
    received.push(order.payment.status);
    if (order.payment.status === "CANCELED") completed.resolve();
  }, 1);
  try {
    await completed.promise;
    await new Promise(resolve => setTimeout(resolve, 15));
    assert.deepEqual(received, states);
    assert.equal(calls, 3);
  } finally { stop(); }
});

test("조회 통신 오류 후에도 다시 읽고 성공한 주문에서 멈춘다", async () => {
  const completed = Promise.withResolvers();
  let calls = 0;
  const stop = watchOrder(async () => {
    if (++calls === 1) throw new Error("offline");
    return { payment: { status: "SUCCEEDED" } };
  }, order => completed.resolve(order), 1);
  try {
    assert.equal((await completed.promise).payment.status, "SUCCEEDED");
    assert.equal(calls, 2);
  } finally { stop(); }
});

test("화면을 떠나면 진행 중인 조회를 중단하고 늦은 응답을 표시하지 않는다", async () => {
  const entered = Promise.withResolvers();
  const response = Promise.withResolvers();
  let updated = false;
  let signal;
  const stop = watchOrder(async requestSignal => {
    signal = requestSignal;
    entered.resolve();
    return response.promise;
  }, () => { updated = true; }, 1);
  await entered.promise;
  stop();
  response.resolve({ payment: { status: "SUCCEEDED" } });
  await new Promise(resolve => setTimeout(resolve, 5));
  assert.equal(signal.aborted, true);
  assert.equal(updated, false);
});
