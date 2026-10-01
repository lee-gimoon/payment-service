import assert from "node:assert/strict";
import { afterEach, test } from "node:test";
import { ApiRequestError, request } from "../src/api/http.ts";

const realFetch = globalThis.fetch;
afterEach(() => { globalThis.fetch = realFetch; });

function respond(status, body, contentType = "application/json") {
  globalThis.fetch = async () => new Response(body, { status, headers: { "Content-Type": contentType } });
}

async function rejection(promise) {
  try {
    await promise;
  } catch (error) {
    return error;
  }
  assert.fail("요청이 실패해야 합니다.");
}

test("서버에 연결하지 못하면 브라우저 원문 대신 한국어 안내를 던진다", async () => {
  globalThis.fetch = async () => { throw new TypeError("Failed to fetch"); };
  const error = await rejection(request("/admin/chat/rooms"));
  assert.ok(error instanceof ApiRequestError);
  assert.equal(error.status, 0);
  assert.equal(error.code, "SERVER_UNREACHABLE");
  assert.equal(error.message, "서버에 연결하지 못했습니다. 잠시 후 다시 확인해주세요.");
});

test("개발 서버 프록시가 백엔드에 닿지 못한 502도 같은 안내로 바꾼다", async () => {
  respond(502, "", "text/plain");
  const error = await rejection(request("/chat/messages"));
  assert.equal(error.status, 502);
  assert.equal(error.code, "SERVER_UNREACHABLE");
});

test("백엔드가 보낸 503 JSON 오류는 서버 문구를 그대로 쓴다", async () => {
  respond(503, JSON.stringify({ code: "STORAGE_UNAVAILABLE", message: "결제 결과를 저장하지 못했습니다." }));
  const error = await rejection(request("/payments/confirm"));
  assert.equal(error.code, "STORAGE_UNAVAILABLE");
  assert.equal(error.message, "결제 결과를 저장하지 못했습니다.");
});

test("허용한 오류 상태는 본문을 돌려주고, 204는 본문 없이 끝낸다", async () => {
  respond(422, JSON.stringify({ status: "PENDING_PAYMENT" }));
  assert.deepEqual(await request("/payments/confirm", undefined, [422]), { status: "PENDING_PAYMENT" });
  globalThis.fetch = async () => new Response(null, { status: 204 });
  assert.equal(await request("/chat/read"), undefined);
});

test("401은 상태를 그대로 전달해 화면이 로그인 안내를 고를 수 있다", async () => {
  respond(401, JSON.stringify({ code: "UNAUTHORIZED", message: "로그인이 필요합니다." }));
  const error = await rejection(request("/orders/abc"));
  assert.equal(error.status, 401);
  assert.equal(error.code, "UNAUTHORIZED");
});
