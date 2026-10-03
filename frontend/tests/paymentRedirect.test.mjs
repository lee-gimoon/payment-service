import assert from "node:assert/strict";
import { afterEach, beforeEach, test } from "node:test";
import { readPaymentRedirect, recordPaymentFailure, recordStockRejection } from "../src/payments/paymentRedirect.ts";

const attemptId = "123e4567-e89b-12d3-a456-426614174000";

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
  window.location.search = `?flow=success&orderId=order-123&paymentKey=test-payment&amount=10000&attemptId=${attemptId}`;
  const redirect = readPaymentRedirect();
  assert.deepEqual(redirect.confirmation, {
    orderId: "order-123", paymentKey: "test-payment", amount: 10000, attemptId
  });
  assert.equal(window.location.search, "?orderId=order-123");
  assert.deepEqual(readPaymentRedirect().confirmation, redirect.confirmation);
});

test("인증 복귀의 결제 시도 ID를 승인 요청에 연결한다", () => {
  window.location.search = `?flow=success&orderId=order-123&paymentKey=test-payment&amount=10000&attemptId=${attemptId}`;
  const redirect = readPaymentRedirect();
  assert.equal(redirect.attemptId, attemptId);
  assert.equal(redirect.confirmation.attemptId, attemptId);
  assert.equal(window.location.search, "?orderId=order-123");
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
    orderId: "order-123", paymentKey: "old-key", amount: 10000, attemptId
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
    window.location.search = `?flow=success&orderId=order-123&paymentKey=key&amount=${amount}&attemptId=${attemptId}`;
    assert.equal(readPaymentRedirect().confirmation, null);
  }
});

test("다른 주문의 임시 승인 정보를 복원하지 않는다", () => {
  window.sessionStorage.setItem("pendingConfirmation:order-123", JSON.stringify({
    orderId: "other-order", paymentKey: "key", amount: 10000, attemptId
  }));
  window.location.search = "?orderId=order-123";
  assert.equal(readPaymentRedirect().confirmation, null);
});

test("sessionStorage를 사용할 수 없어도 인증 결과를 전달한다", () => {
  window.sessionStorage.setItem = () => { throw new Error("Storage disabled"); };
  window.location.search = `?flow=success&orderId=order-123&paymentKey=key&amount=10000&attemptId=${attemptId}`;
  assert.equal(readPaymentRedirect().confirmation.paymentKey, "key");
});

test("결제 시도 ID가 없거나 UUID 형식이 아니면 승인 요청을 만들지 않는다", () => {
  for (const invalidId of ["", "invalid", "------------------------------------", "123e4567e89b12d3a456426614174000"]) {
    window.location.search = `?flow=success&orderId=order-123&paymentKey=key&amount=10000&attemptId=${invalidId}`;
    assert.equal(readPaymentRedirect().confirmation, null);
  }
});

test("결제 시도 ID가 없는 저장 데이터로 승인을 복원하지 않는다", () => {
  window.sessionStorage.setItem("pendingConfirmation:order-123", JSON.stringify({
    orderId: "order-123", paymentKey: "key", amount: 10000
  }));
  window.location.search = "?orderId=order-123";
  assert.equal(readPaymentRedirect().confirmation, null);
});

test("잘못된 인증 복귀는 이전 시도의 승인 정보를 복원하지 않는다", () => {
  window.sessionStorage.setItem("pendingConfirmation:order-123", JSON.stringify({
    orderId: "order-123", paymentKey: "old-key", amount: 10000, attemptId
  }));
  window.location.search = "?flow=success&orderId=order-123&paymentKey=key&amount=10000";
  assert.equal(readPaymentRedirect().confirmation, null);
  assert.equal(readPaymentRedirect().confirmation, null);
});

test("취소·실패 복귀 정보는 URL 정리 후 재로그인해도 같은 시도로 복원한다", () => {
  for (const code of ["PAY_PROCESS_CANCELED", "REJECT_CARD_COMPANY"]) {
    window.location.search = `?flow=fail&requestedOrderId=order-123&attemptId=${attemptId}&code=${code}&message=Declined`;
    const original = readPaymentRedirect();
    assert.equal(window.location.search, "?orderId=order-123");
    assert.deepEqual(readPaymentRedirect(), original);
  }
});

test("로그인 만료·다른 회원·통신 오류에는 실패 정보를 유지하고 재로그인 후 기록한다", async () => {
  for (const error of [Object.assign(new Error("Sign in"), { status: 401 }),
    Object.assign(new Error("Not found"), { status: 404 }), new TypeError("Failed to fetch")]) {
    window.location.search = `?flow=fail&orderId=order-123&attemptId=${attemptId}&code=PAY_PROCESS_CANCELED&message=Cancelled`;
    const redirect = readPaymentRedirect();
    await assert.rejects(recordPaymentFailure(redirect, async () => { throw error; }), failure => failure === error);
    const resumed = readPaymentRedirect();
    assert.deepEqual(resumed, redirect);
    const calls = [];
    await recordPaymentFailure(resumed, async (...args) => { calls.push(args); });
    assert.deepEqual(calls, [[attemptId, "AUTH_CANCELED", "PAY_PROCESS_CANCELED"]]);
    assert.equal(window.sessionStorage.getItem("pendingAuthenticationResult:order-123"), null);
    assert.equal(readPaymentRedirect().flow, null);
  }
});

test("취소 외의 인증 실패는 AUTH_FAILED로 기록한다", async () => {
  window.location.search = `?flow=fail&orderId=order-123&attemptId=${attemptId}&code=REJECT_CARD_COMPANY`;
  const calls = [];
  await recordPaymentFailure(readPaymentRedirect(), async (...args) => { calls.push(args); });
  assert.deepEqual(calls, [[attemptId, "AUTH_FAILED", "REJECT_CARD_COMPANY"]]);
});

test("실패 기록 성공 후 주문 조회에 실패해도 다음 복귀에서 실패 기록을 다시 보내지 않는다", async () => {
  window.location.search = `?flow=fail&orderId=order-123&attemptId=${attemptId}&code=PAY_PROCESS_CANCELED`;
  let calls = 0;
  const record = async () => { calls++; };
  await assert.rejects(async () => {
    await recordPaymentFailure(readPaymentRedirect(), record);
    throw new Error("Order lookup failed");
  }, /Order lookup failed/);
  await recordPaymentFailure(readPaymentRedirect(), record);
  assert.equal(calls, 1);
});

test("늦은 취소가 승인 상태로 응답해도 처리한 복귀 정보는 제거한다", async () => {
  for (const status of ["APPROVING", "SUCCEEDED"]) {
    window.location.search = `?flow=fail&orderId=order-123&attemptId=${attemptId}&code=PAY_PROCESS_CANCELED`;
    await recordPaymentFailure(readPaymentRedirect(), async () => ({ id: attemptId, status }));
    assert.equal(readPaymentRedirect().flow, null);
  }
});

test("새 인증 성공은 이전 취소 복귀 정보를 제거하고 승인을 복원한다", () => {
  window.location.search = `?flow=fail&orderId=order-123&attemptId=${attemptId}&code=PAY_PROCESS_CANCELED`;
  readPaymentRedirect();
  window.location.search = `?flow=success&orderId=order-123&attemptId=${attemptId}&paymentKey=new-key&amount=10000`;
  const success = readPaymentRedirect();
  assert.equal(success.flow, "success");
  assert.equal(window.sessionStorage.getItem("pendingAuthenticationResult:order-123"), null);
  assert.deepEqual(readPaymentRedirect().confirmation, success.confirmation);
});

test("이전 실패 기록의 늦은 응답이 새 시도의 복귀 정보를 지우지 않는다", async () => {
  window.location.search = `?flow=fail&orderId=order-123&attemptId=${attemptId}&code=PAY_PROCESS_CANCELED`;
  let complete;
  const recording = recordPaymentFailure(readPaymentRedirect(), () => new Promise(resolve => { complete = resolve; }));
  const newAttemptId = "123e4567-e89b-12d3-a456-426614174001";
  window.location.search = `?flow=fail&orderId=order-123&attemptId=${newAttemptId}&code=REJECT_CARD_COMPANY`;
  readPaymentRedirect();
  complete();
  await recording;
  assert.equal(readPaymentRedirect().attemptId, newAttemptId);
});

test("다른 주문이나 잘못된 시도·오류 정보를 저장한 실패 기록은 복원하지 않는다", () => {
  const valid = { orderId: "order-123", attemptId, errorCode: "PAY_PROCESS_CANCELED", errorMessage: "Cancelled" };
  for (const data of [null, [], { ...valid, orderId: "order-456" }, { ...valid, attemptId: "invalid" },
    { ...valid, errorCode: 123 }, { ...valid, errorCode: "x".repeat(81) },
    { ...valid, errorMessage: {} }, { ...valid, errorMessage: "x".repeat(301) }]) {
    window.sessionStorage.setItem("pendingAuthenticationResult:order-123", JSON.stringify(data));
    window.location.search = "?orderId=order-123";
    assert.equal(readPaymentRedirect().flow, null);
  }
});

test("잘못된 새 인증 복귀에 이전 실패 정보를 재사용하지 않는다", () => {
  window.location.search = `?flow=fail&orderId=order-123&attemptId=${attemptId}&code=PAY_PROCESS_CANCELED`;
  readPaymentRedirect();
  window.location.search = "?flow=success&orderId=order-123&paymentKey=key&amount=10000";
  assert.equal(readPaymentRedirect().confirmation, null);
  assert.equal(readPaymentRedirect().flow, null);
});

const nextAttemptId = "223e4567-e89b-12d3-a456-426614174000";

test("품절 거절 뒤 새로고침하면 승인 정보 없이 품절 안내만 복원한다", () => {
  window.location.search = `?flow=success&orderId=order-123&paymentKey=test-payment&amount=10000&attemptId=${attemptId}`;
  recordStockRejection(readPaymentRedirect().confirmation, "볼트 그래픽 티 M 사이즈의 재고가 부족합니다.");

  const reloaded = readPaymentRedirect();
  assert.equal(reloaded.confirmation, null);
  assert.deepEqual(reloaded.stockRejection, {
    orderId: "order-123", attemptId, message: "볼트 그래픽 티 M 사이즈의 재고가 부족합니다."
  });
  assert.equal(window.sessionStorage.getItem("pendingConfirmation:order-123"), null);
});

test("같은 시도의 인증 성공 주소를 다시 열어도 품절로 거절된 승인을 만들지 않는다", () => {
  const search = `?flow=success&orderId=order-123&paymentKey=test-payment&amount=10000&attemptId=${attemptId}`;
  window.location.search = search;
  recordStockRejection(readPaymentRedirect().confirmation, "품절");
  window.location.search = search;
  const reopened = readPaymentRedirect();
  assert.equal(reopened.confirmation, null);
  assert.equal(reopened.stockRejection.attemptId, attemptId);
});

test("같은 주문의 새 결제 시도는 이전 품절 안내를 지우고 승인 정보를 만든다", () => {
  window.location.search = `?flow=success&orderId=order-123&paymentKey=old-key&amount=10000&attemptId=${attemptId}`;
  recordStockRejection(readPaymentRedirect().confirmation, "품절");
  window.location.search = `?flow=success&orderId=order-123&paymentKey=new-key&amount=10000&attemptId=${nextAttemptId}`;
  const redirect = readPaymentRedirect();
  assert.equal(redirect.stockRejection, null);
  assert.equal(redirect.confirmation.paymentKey, "new-key");
  assert.equal(readPaymentRedirect().confirmation.paymentKey, "new-key");
});

test("이전 시도의 늦은 품절 응답이 새 시도의 승인 정보를 지우지 않는다", () => {
  window.location.search = `?flow=success&orderId=order-123&paymentKey=old-key&amount=10000&attemptId=${attemptId}`;
  const previous = readPaymentRedirect().confirmation;
  window.location.search = `?flow=success&orderId=order-123&paymentKey=new-key&amount=10000&attemptId=${nextAttemptId}`;
  readPaymentRedirect();
  recordStockRejection(previous, "품절");

  const reloaded = readPaymentRedirect();
  assert.equal(reloaded.stockRejection, null);
  assert.equal(reloaded.confirmation.paymentKey, "new-key");
});
