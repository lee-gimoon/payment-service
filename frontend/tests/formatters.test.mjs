import assert from "node:assert/strict";
import { test } from "node:test";
import { orderStatusLabel, paymentStatusLabel } from "../src/lib/formatters.ts";

test("결제 기한이 지나 취소된 주문은 주문 상태와 결제 상태 모두 주문 취소로 표시한다", () => {
  assert.equal(orderStatusLabel("CANCELED"), "주문 취소");
  assert.equal(paymentStatusLabel("CANCELED"), "주문 취소");
});

test("서버의 주문 상태마다 한국어 문구가 있다", () => {
  for (const status of ["PENDING_PAYMENT", "PAYMENT_IN_PROGRESS", "PAID", "CANCELED"]) {
    assert.ok(orderStatusLabel(status));
  }
  for (const status of ["READY", "APPROVING", "SUCCEEDED", "FAILED", "UNKNOWN", "REVIEW_REQUIRED", "CANCELED"]) {
    assert.ok(paymentStatusLabel(status));
  }
});
