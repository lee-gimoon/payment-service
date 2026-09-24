import assert from "node:assert/strict";
import { afterEach, beforeEach, test } from "node:test";
import { openPaymentWindow } from "../src/payments/paymentWindow.ts";

const order = { orderId: "order-123", productName: "티셔츠", quantity: 1, amount: 10000 };
const config = { paymentMethodVariantKey: "CARD_ONLY", agreementVariantKey: "TERMS" };

beforeEach(() => { globalThis.window = { location: { origin: "http://localhost:5173" } }; });
afterEach(() => { delete globalThis.window; });

function fakeWidgets(overrides = {}) {
  const events = {};
  const calls = [];
  const ready = Promise.withResolvers();
  const paymentWindow = {
    on(name, callback) {
      events[name] = callback;
      if (name === "paymentRequest") ready.resolve();
    },
    async destroy() { calls.push(["destroy"]); }
  };
  const widgets = {
    async setAmount(amount) { calls.push(["amount", amount]); },
    async renderPaymentWindow(options) { calls.push(["render", options]); return paymentWindow; },
    async requestPayment(request) { calls.push(["request", request]); },
    ...overrides
  };
  return { widgets, events, calls, paymentWindow, ready: ready.promise };
}

test("금액·UI 준비 후 구매자의 선택을 기다리고 주문과 복귀 URL로 인증을 요청한다", async () => {
  const fake = fakeWidgets();
  const completion = openPaymentWindow(fake.widgets, order, config, new AbortController().signal);
  await fake.ready;
  assert.deepEqual(fake.calls, [
    ["amount", { currency: "KRW", value: 10000 }],
    ["render", { variantKey: { paymentMethod: "CARD_ONLY", agreement: "TERMS" } }]
  ]);
  await fake.events.paymentRequest({ paymentMethod: { code: "CARD" } });
  await completion;
  const request = fake.calls.find(([type]) => type === "request")[1];
  assert.deepEqual(request, {
    orderId: "order-123", orderName: "티셔츠 1장",
    successUrl: "http://localhost:5173/payment/result?flow=success",
    failUrl: "http://localhost:5173/payment/result?flow=fail&requestedOrderId=order-123"
  });
  assert.deepEqual(fake.calls.at(-1), ["destroy"]);
});

test("연속 결제 이벤트는 SDK 인증 요청을 한 번만 실행한다", async () => {
  const request = Promise.withResolvers();
  let requests = 0;
  const fake = fakeWidgets({ requestPayment: async () => { requests++; await request.promise; } });
  const completion = openPaymentWindow(fake.widgets, order, config, new AbortController().signal);
  await fake.ready;
  const first = fake.events.paymentRequest({ paymentMethod: { code: "NAVERPAY" } });
  await fake.events.paymentRequest({ paymentMethod: { code: "CARD" } });
  assert.equal(requests, 1);
  request.resolve();
  await first;
  await completion;
});

test("구매자가 창을 닫으면 작업을 끝내고 같은 주문으로 다시 열 수 있다", async () => {
  const fake = fakeWidgets();
  const completion = openPaymentWindow(fake.widgets, order, config, new AbortController().signal);
  await fake.ready;
  await fake.events.cancel();
  await completion;
  await fake.events.paymentRequest({ paymentMethod: { code: "CARD" } });
  assert.equal(fake.calls.some(([type]) => type === "request" || type === "destroy"), false);

  const reopened = openPaymentWindow(fake.widgets, order, config, new AbortController().signal);
  await new Promise((resolve) => setImmediate(resolve));
  await fake.events.paymentRequest({ paymentMethod: { code: "TOSSPAY" } });
  await reopened;
  assert.equal(fake.calls.filter(([type]) => type === "request").length, 1);
});

test("결제 시도 ID를 복귀 URL에 넣고 창 닫기를 서버 기록 콜백으로 전달한다", async () => {
  const attemptId = "123e4567-e89b-12d3-a456-426614174000";
  let canceled = 0;
  const first = fakeWidgets();
  const closing = openPaymentWindow(first.widgets, order, config, new AbortController().signal,
    attemptId, async () => { canceled++; });
  await first.ready;
  await first.events.cancel();
  await closing;
  assert.equal(canceled, 1);

  const second = fakeWidgets();
  const requesting = openPaymentWindow(second.widgets, order, config, new AbortController().signal, attemptId);
  await second.ready;
  await second.events.paymentRequest({ paymentMethod: { code: "CARD" } });
  await requesting;
  const request = second.calls.find(([kind]) => kind === "request")[1];
  assert.match(request.successUrl, /attemptId=123e4567-e89b-12d3-a456-426614174000/);
  assert.match(request.failUrl, /attemptId=123e4567-e89b-12d3-a456-426614174000/);
});

test("가상계좌·브랜드페이 등 미지원 수단은 인증 요청 전에 차단한다", async () => {
  for (const code of ["VIRTUAL_ACCOUNT", "TRANSFER", "BRANDPAY", "PAYPAL", "UNKNOWN_METHOD"]) {
    const fake = fakeWidgets();
    const completion = openPaymentWindow(fake.widgets, order, config, new AbortController().signal);
    const rejection = assert.rejects(completion, /카드·국내 간편결제만 지원/);
    await fake.ready;
    await fake.events.paymentRequest({ paymentMethod: { code } });
    await rejection;
    assert.equal(fake.calls.some(([type]) => type === "request"), false);
    assert.deepEqual(fake.calls.at(-1), ["destroy"]);
  }
});

test("SDK 인증 실패는 호출한 화면으로 전달하고 창을 정리한다", async () => {
  const failure = new Error("인증 요청 실패");
  const fake = fakeWidgets({ requestPayment: async () => { throw failure; } });
  const completion = openPaymentWindow(fake.widgets, order, config, new AbortController().signal);
  const rejection = assert.rejects(completion, (error) => error === failure);
  await fake.ready;
  await fake.events.paymentRequest({ paymentMethod: { code: "KAKAOPAY" } });
  await rejection;
  assert.deepEqual(fake.calls.at(-1), ["destroy"]);
});

test("화면을 떠나면 열린 창을 제거하고 늦은 선택 이벤트를 무시한다", async () => {
  const fake = fakeWidgets();
  const controller = new AbortController();
  const completion = openPaymentWindow(fake.widgets, order, config, controller.signal);
  await fake.ready;
  controller.abort();
  await completion;
  await fake.events.paymentRequest({ paymentMethod: { code: "CARD" } });
  assert.equal(fake.calls.some(([type]) => type === "request"), false);
  assert.deepEqual(fake.calls.at(-1), ["destroy"]);
});

test("금액 설정 중 화면을 떠나면 창을 열지 않는다", async () => {
  const amount = Promise.withResolvers();
  const fake = fakeWidgets({ setAmount: () => amount.promise });
  const controller = new AbortController();
  const completion = openPaymentWindow(fake.widgets, order, config, controller.signal);
  controller.abort();
  amount.resolve();
  await completion;
  assert.deepEqual(fake.calls, []);
});

test("화면을 떠난 뒤 늦게 만들어진 창도 정리한다", async () => {
  const rendering = Promise.withResolvers();
  const entered = Promise.withResolvers();
  const fake = fakeWidgets({ renderPaymentWindow: () => { entered.resolve(); return rendering.promise; } });
  const controller = new AbortController();
  const completion = openPaymentWindow(fake.widgets, order, config, controller.signal);
  await entered.promise;
  controller.abort();
  rendering.resolve(fake.paymentWindow);
  await completion;
  assert.deepEqual(fake.calls.at(-1), ["destroy"]);
  assert.deepEqual(fake.events, {});
});

test("variantKey 미설정은 SDK 기본 UI를 사용한다", async () => {
  const fake = fakeWidgets();
  const completion = openPaymentWindow(fake.widgets, order,
    { paymentMethodVariantKey: "", agreementVariantKey: "" }, new AbortController().signal);
  await fake.ready;
  assert.deepEqual(fake.calls[1][1], { variantKey: { paymentMethod: undefined, agreement: undefined } });
  await fake.events.cancel();
  await completion;
});
