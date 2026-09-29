import assert from "node:assert/strict";
import { afterEach, beforeEach, test } from "node:test";
import {
  clearPendingOrderId,
  getLastOrderId,
  getPendingOrderId,
  saveLastOrderId,
  savePendingOrderId
} from "../src/lib/orderStorage.ts";

let values;

beforeEach(() => {
  values = new Map();
  globalThis.window = {
    localStorage: {
      getItem: key => values.get(key) ?? null,
      setItem: (key, value) => values.set(key, value),
      removeItem: key => values.delete(key)
    }
  };
});

afterEach(() => { delete globalThis.window; });

test("회원 A에서 B로 바꿨다가 돌아와도 각자의 주문 복구 정보를 유지한다", () => {
  savePendingOrderId("member-a", "order-a");
  saveLastOrderId("member-a", "order-a");
  assert.equal(getPendingOrderId("member-b"), null);
  assert.equal(getLastOrderId("member-b"), null);

  savePendingOrderId("member-b", "order-b");
  saveLastOrderId("member-b", "order-b");
  assert.equal(getPendingOrderId("member-b"), "order-b");
  assert.equal(getLastOrderId("member-b"), "order-b");
  assert.equal(getPendingOrderId("member-a"), "order-a");
  assert.equal(getLastOrderId("member-a"), "order-a");
});

test("B의 결제 성공은 A의 대기 주문을 지우지 않는다", () => {
  savePendingOrderId("member-a", "order-a");
  savePendingOrderId("member-b", "order-b");
  clearPendingOrderId("member-b", "order-b");
  assert.equal(getPendingOrderId("member-b"), null);
  assert.equal(getPendingOrderId("member-a"), "order-a");
});

test("이전 주문의 늦은 성공 응답은 같은 회원의 새로운 대기 주문을 지우지 않는다", () => {
  savePendingOrderId("member-a", "new-order");
  clearPendingOrderId("member-a", "old-order");
  assert.equal(getPendingOrderId("member-a"), "new-order");
});

test("회원 정보 없는 전역 주문번호는 어느 회원의 주문으로도 복원하지 않는다", () => {
  values.set("pendingOrderId", "legacy-pending");
  values.set("lastOrderId", "legacy-last");
  assert.equal(getPendingOrderId("member-a"), null);
  assert.equal(getLastOrderId("member-a"), null);
  assert.equal(getPendingOrderId("member-b"), null);
  assert.equal(getLastOrderId("member-b"), null);
  assert.equal(values.get("pendingOrderId"), "legacy-pending");
  assert.equal(values.get("lastOrderId"), "legacy-last");
});

test("로그인한 회원 ID가 없으면 주문 복구 저장소를 읽거나 수정하지 않는다", () => {
  for (const id of [null, undefined, "", "   "]) {
    savePendingOrderId(id, "order-a");
    saveLastOrderId(id, "order-a");
    clearPendingOrderId(id, "order-a");
    assert.equal(getPendingOrderId(id), null);
    assert.equal(getLastOrderId(id), null);
  }
  assert.equal(values.size, 0);
});

test("저장소가 제한되어도 주문 복구 함수는 예외를 던지지 않는다", () => {
  Object.defineProperty(window, "localStorage", {
    get: () => { throw new Error("Storage disabled"); }
  });
  assert.doesNotThrow(() => savePendingOrderId("member-a", "order-a"));
  assert.doesNotThrow(() => saveLastOrderId("member-a", "order-a"));
  assert.doesNotThrow(() => clearPendingOrderId("member-a", "order-a"));
  assert.equal(getPendingOrderId("member-a"), null);
  assert.equal(getLastOrderId("member-a"), null);
});
