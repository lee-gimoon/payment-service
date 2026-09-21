import assert from "node:assert/strict";
import { afterEach, beforeEach, test } from "node:test";
import { readPaymentRedirect } from "../src/payments/paymentRedirect.ts";

beforeEach(() => {
  const values = new Map();
  globalThis.window = {
    location: { search: "" },
    sessionStorage: {
      getItem: (key) => values.get(key) ?? null,
      setItem: (key, value) => values.set(key, value),
      removeItem: (key) => values.delete(key)
    },
    history: {
      replaceState: (_state, _title, url) => {
        window.location.search = new URL(url, "http://localhost").search;
      }
    }
  };
});

afterEach(() => { delete globalThis.window; });

test("인증 성공은 승인 요청 정보를 보관하고 URL에서 paymentKey를 지운다", () => {
  window.location.search = "?flow=success&orderId=order-123&paymentKey=test-payment&amount=10000";
  const redirect = readPaymentRedirect();
  assert.deepEqual(redirect.confirmation, {
    orderId: "order-123", paymentKey: "test-payment", amount: 10000
  });
  assert.equal(window.location.search, "?orderId=order-123");
  // 서버 응답 전에 새로고침해도 동일한 요청 정보를 복원한다.
  assert.deepEqual(readPaymentRedirect().confirmation, redirect.confirmation);
});

test("인증 취소에서 orderId가 빠져도 요청한 주문번호와 오류 사유를 읽는다", () => {
  window.location.search = "?flow=fail&requestedOrderId=order-123&code=PAY_PROCESS_CANCELED&message=Cancelled";
  const redirect = readPaymentRedirect();
  assert.equal(redirect.orderId, "order-123");
  assert.equal(redirect.errorCode, "PAY_PROCESS_CANCELED");
  assert.equal(redirect.errorMessage, "Cancelled");
  assert.equal(redirect.confirmation, null);
});

test("실패 리다이렉트는 저장된 승인 정보를 재사용하지 않는다", () => {
  window.sessionStorage.setItem("pendingConfirmation:order-123", JSON.stringify({
    orderId: "order-123", paymentKey: "old-key", amount: 10000
  }));
  window.location.search = "?flow=fail&orderId=order-123&code=REJECT_CARD_COMPANY";
  assert.equal(readPaymentRedirect().confirmation, null);
  assert.equal(readPaymentRedirect().confirmation, null);
});

test("주문번호 없는 취소도 오류 사유는 유지한다", () => {
  window.location.search = "?flow=fail&code=PAY_PROCESS_CANCELED&message=Cancelled";
  const redirect = readPaymentRedirect();
  assert.equal(redirect.orderId, null);
  assert.equal(redirect.errorCode, "PAY_PROCESS_CANCELED");
});

test("잘못된 금액으로는 승인 요청을 만들지 않는다", () => {
  for (const amount of ["0", "-1", "10000.5", "1e4", "NaN", "1000000000000", ""]) {
    window.location.search = `?flow=success&orderId=order-123&paymentKey=key&amount=${amount}`;
    assert.equal(readPaymentRedirect().confirmation, null);
  }
});

test("다른 주문의 임시 승인 정보를 복원하지 않는다", () => {
  window.sessionStorage.setItem("pendingConfirmation:order-123", JSON.stringify({
    orderId: "other-order", paymentKey: "key", amount: 10000
  }));
  window.location.search = "?orderId=order-123";
  assert.equal(readPaymentRedirect().confirmation, null);
});

test("sessionStorage를 사용할 수 없어도 인증 결과를 전달한다", () => {
  window.sessionStorage.setItem = () => { throw new Error("Storage disabled"); };
  window.location.search = "?flow=success&orderId=order-123&paymentKey=key&amount=10000";
  assert.equal(readPaymentRedirect().confirmation.paymentKey, "key");
});
