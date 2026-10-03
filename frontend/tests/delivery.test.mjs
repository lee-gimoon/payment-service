import assert from "node:assert/strict";
import { test } from "node:test";
import { CARRIER_CODES, CARRIERS, deliveryStatusLabel, normalizeTrackingNumber } from "../src/lib/delivery.ts";

test("서버가 받는 택배사 다섯 곳에 이름과 조회 주소가 있다", () => {
  assert.deepEqual(CARRIER_CODES, ["CJ", "HANJIN", "LOTTE", "EPOST", "LOGEN"]);
  for (const code of CARRIER_CODES) {
    assert.ok(CARRIERS[code].name);
    assert.match(CARRIERS[code].trackingUrl("123456789012"), /^https:\/\/.+123456789012/);
  }
});

test("붙여 넣은 송장번호의 하이픈과 공백을 지운다", () => {
  assert.equal(normalizeTrackingNumber("1234-5678 9012"), "123456789012");
});

test("배송 단계를 한국어로 보여준다", () => {
  assert.equal(deliveryStatusLabel("PREPARING"), "상품 준비 중");
  assert.equal(deliveryStatusLabel("SHIPPED"), "배송 중");
  assert.equal(deliveryStatusLabel("DELIVERED"), "배송 완료");
});
