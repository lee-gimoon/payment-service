import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { afterEach, beforeEach, test } from "node:test";
import * as React from "react";
import { transformSync } from "rolldown/utils";
import { ApiRequestError } from "../src/api/http.ts";
import * as formatters from "../src/lib/formatters.ts";
import * as orderStorage from "../src/lib/orderStorage.ts";
import * as storage from "../src/lib/storage.ts";
import * as paymentRedirect from "../src/payments/paymentRedirect.ts";
import * as hooks from "./helpers/chatThreadHarness.mjs";

const orderId = "order-123";
const currentAttemptId = "223e4567-e89b-12d3-a456-426614174000";
const nextAttemptId = "323e4567-e89b-12d3-a456-426614174000";
const storageKey = `pendingConfirmation:${orderId}`;
const command = { orderId, attemptId: currentAttemptId, paymentKey: "current-payment-key", amount: 10000 };
const previousFailureMessage = "이전 결제 시도는 카드사에서 거절되었습니다.";

function memoryStorage() {
  const values = new Map();
  return {
    getItem: key => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
    removeItem: key => values.delete(key)
  };
}

function successRedirect(confirmation = command) {
  window.location.search = `?${new URLSearchParams({
    flow: "success", orderId: confirmation.orderId, attemptId: confirmation.attemptId,
    paymentKey: confirmation.paymentKey, amount: String(confirmation.amount)
  })}`;
}

beforeEach(() => {
  globalThis.window = {
    location: { origin: "http://localhost:5173", pathname: "/payment/result", search: "" },
    sessionStorage: memoryStorage(),
    localStorage: memoryStorage(),
    history: {
      replaceState: (_state, _title, url) => {
        const safeUrl = new URL(url, window.location.origin);
        window.location.pathname = safeUrl.pathname;
        window.location.search = safeUrl.search;
      }
    }
  };
  globalThis.document = { title: "" };
  successRedirect();
});

afterEach(() => {
  delete globalThis.window;
  delete globalThis.document;
});

function order(attemptStatus = "STARTED", paymentStatus = "FAILED") {
  return {
    orderId, productName: "테스트 티셔츠", quantity: 1, amount: 10000, currency: "KRW",
    items: [{ productId: "tee-01", productName: "테스트 티셔츠", size: "M", quantity: 1, unitPrice: 10000 }],
    shipping: { recipientName: "테스트", phone: "01012345678", postalCode: "12345", address: "테스트 주소", addressDetail: "", memo: null },
    createdAt: "2026-10-01T01:00:00Z",
    status: paymentStatus === "SUCCEEDED" ? "PAID"
      : ["APPROVING", "UNKNOWN", "REVIEW_REQUIRED"].includes(paymentStatus) ? "PAYMENT_IN_PROGRESS" : "PENDING_PAYMENT",
    latestAttempt: { id: currentAttemptId, status: attemptStatus, startedAt: "2026-10-01T02:00:00Z" },
    payment: {
      status: paymentStatus, pgStatus: null, approvedAt: null, checkedAt: null, errorCode: null,
      message: paymentStatus === "FAILED" ? previousFailureMessage : `${paymentStatus} 서버 결과`,
      paidAmount: paymentStatus === "SUCCEEDED" ? 10000 : null,
      paidCurrency: paymentStatus === "SUCCEEDED" ? "KRW" : null
    },
    delivery: null, canceledAt: null
  };
}

function unreachable() {
  return new ApiRequestError(0, {
    code: "SERVER_UNREACHABLE", message: "서버에 연결하지 못했습니다. 잠시 후 다시 확인해주세요."
  });
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

function button(renderer, label) {
  return nodes(renderer.current).find(node => node.type === "button" && text(node) === label);
}

async function click(renderer, label) {
  const action = button(renderer, label);
  assert.ok(action, `${label} 버튼이 있어야 합니다.`);
  assert.equal(action.props.disabled, false);
  action.props.onClick();
  await renderer.flush();
}

function storedConfirmation() {
  return JSON.parse(window.sessionStorage.getItem(storageKey) ?? "null");
}

function detailValue(renderer, label) {
  const row = nodes(renderer.current).find(node => node.type === "div"
    && nodes(node.props.children).some(child => child.type === "dt" && text(child) === label));
  return row ? text(nodes(row.props.children).find(child => child.type === "dd")) : undefined;
}

// Compile the production component just as adminChat.test.mjs does, while the
// existing hook harness controls renders and promise completion. Redirect and
// browser-storage helpers remain the real production modules.
async function mount(t, { confirm = async () => { throw unreachable(); }, load = async () => order() } = {}) {
  const confirmations = [];
  const source = (await readFile(new URL("../src/pages/PaymentResultPage.tsx", import.meta.url), "utf8"))
    .replace(/^import [\s\S]*?;\r?\n/gm, "").replace(/^export /gm, "");
  const { code, errors } = transformSync("PaymentResultPage.tsx", source, { jsx: { runtime: "classic" } });
  assert.equal(errors.length, 0);
  const scope = {
    ...hooks, ...formatters, ...orderStorage, ...storage, ...paymentRedirect,
    React, Link: "a", AppShell: "main", ApiRequestError,
    useAuth: () => ({ status: "signedIn", login: () => {} }),
    confirmPayment: async confirmation => {
      confirmations.push({ ...confirmation });
      return confirm(confirmation, confirmations.length);
    },
    getOrder: async requestedOrderId => {
      assert.equal(requestedOrderId, orderId);
      return load();
    },
    recordAuthenticationResult: async () => { throw new Error("인증 성공에서 실패 기록을 보내면 안 됩니다."); }
  };
  const Component = new Function(...Object.keys(scope), `${code}\nreturn PaymentResult;`)(...Object.values(scope));
  const renderer = await hooks.createRenderer(Component, { customerId: "customer-01" });
  t.after(() => renderer.dispose());
  return { renderer, confirmations };
}

test("이전 승인이 FAILED여도 현재 STARTED 시도의 인증 정보를 보존하고 같은 명령으로 재확인한다", async t => {
  // The order-level payment reports attempt A's failed approval; latestAttempt
  // reports attempt B, whose confirmation request did not reach the server.
  const { renderer, confirmations } = await mount(t, {
    confirm: async (_confirmation, count) => {
      if (count === 1) throw unreachable();
      return order("SUCCEEDED", "SUCCEEDED");
    }
  });
  await click(renderer, "주문 내역 새로고침");
  assert.deepEqual(storedConfirmation(), command);
  assert.ok(button(renderer, "승인 요청 다시 확인"));

  await click(renderer, "승인 요청 다시 확인");
  assert.deepEqual(confirmations, [command, command]);
  assert.equal(storedConfirmation(), null);
  assert.equal(button(renderer, "승인 요청 다시 확인"), undefined);
  assert.equal(text(nodes(renderer.current).find(node => node.type === "h1")), "결제가 완료되었습니다.");
});

test("현재 시도가 아직 STARTED이면 과거 실패를 이번 인증의 최종 결과로 안내하지 않는다", async t => {
  const pending = order();
  pending.payment = {
    ...pending.payment, errorCode: "PREVIOUS_FAILURE", paidAmount: 10000, paidCurrency: "KRW",
    approvedAt: "2026-09-01T01:00:00Z"
  };
  const { renderer } = await mount(t, { load: async () => pending });
  await click(renderer, "주문 내역 새로고침");
  const title = text(nodes(renderer.current).find(node => node.type === "h1"));
  const message = text(nodes(renderer.current).find(node => node.type === "p" && node.props.role === "status"));
  assert.notEqual(title, formatters.paymentStatusLabel("FAILED"));
  assert.notEqual(message, previousFailureMessage);
  assert.match(message, /승인/);
  assert.match(message, /확인|요청/);
  assert.equal(detailValue(renderer, "결제 상태"), "승인 요청 확인 필요");
  assert.equal(detailValue(renderer, "실제 승인 금액"), "—");
  assert.equal(detailValue(renderer, "승인 시각"), "—");
  assert.equal(detailValue(renderer, "오류 코드"), undefined);
  assert.ok(button(renderer, "승인 요청 다시 확인"));
  assert.deepEqual(storedConfirmation(), command);
});

for (const status of ["APPROVING", "UNKNOWN", "REVIEW_REQUIRED", "FAILED", "SUCCEEDED"]) {
  test(`현재 시도의 ${status} 서버 결과를 확인하면 승인 명령을 정리한다`, async t => {
    const { renderer, confirmations } = await mount(t, { load: async () => order(status, status) });
    await click(renderer, "주문 내역 새로고침");
    assert.equal(storedConfirmation(), null);
    assert.equal(button(renderer, "승인 요청 다시 확인"), undefined);
    assert.deepEqual(confirmations, [command]);
    assert.equal(paymentRedirect.readPaymentRedirect().confirmation, null);
  });
}

test("기한이 지나 취소된 주문은 STARTED 시도의 승인 명령도 정리한다", async t => {
  const canceled = { ...order(), status: "CANCELED", canceledAt: "2026-10-02T03:00:00Z" };
  canceled.payment = { ...canceled.payment, status: "CANCELED", message: "결제 기한이 지나 취소되었습니다. 청구된 금액은 없습니다." };
  const { renderer } = await mount(t, { load: async () => canceled });
  await click(renderer, "주문 내역 새로고침");
  assert.equal(storedConfirmation(), null);
  assert.equal(button(renderer, "승인 요청 다시 확인"), undefined);
  assert.equal(text(nodes(renderer.current).find(node => node.type === "h1")), "결제 기한이 지나 취소된 주문입니다");
  const nextAction = nodes(renderer.current).find(node => node.props?.className === "primary-button result-cta");
  assert.equal(nextAction.props.to, "/#products");
});

test("보존한 인증 결과는 새로고침 뒤에도 복원해 같은 시도의 승인을 이어간다", async t => {
  const original = await mount(t);
  await click(original.renderer, "주문 내역 새로고침");
  assert.deepEqual(storedConfirmation(), command);
  assert.equal(window.location.search, `?orderId=${orderId}`);
  original.renderer.dispose();

  const reloaded = await mount(t, { confirm: async () => order("SUCCEEDED", "SUCCEEDED") });
  assert.deepEqual(reloaded.confirmations, [command]);
  assert.equal(storedConfirmation(), null);
  assert.equal(text(nodes(reloaded.renderer.current).find(node => node.type === "h1")), "결제가 완료되었습니다.");
});

test("이전 시도 재확인의 늦은 응답이 새 시도의 저장된 인증 결과를 지우지 않는다", async t => {
  const delayed = Promise.withResolvers();
  const { renderer, confirmations } = await mount(t, {
    confirm: async (_confirmation, count) => {
      if (count === 1) throw unreachable();
      return delayed.promise;
    }
  });
  await click(renderer, "승인 요청 다시 확인");
  const nextCommand = { ...command, attemptId: nextAttemptId, paymentKey: "next-payment-key" };
  successRedirect(nextCommand);
  assert.deepEqual(paymentRedirect.readPaymentRedirect().confirmation, nextCommand);

  delayed.resolve(order("FAILED", "FAILED"));
  await renderer.flush();
  assert.deepEqual(confirmations, [command, command]);
  assert.deepEqual(storedConfirmation(), nextCommand);
  assert.deepEqual(paymentRedirect.readPaymentRedirect().confirmation, nextCommand);
});
