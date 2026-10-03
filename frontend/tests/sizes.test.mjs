import assert from "node:assert/strict";
import { test } from "node:test";
import { addableQuantity, availableSize, remainingLabel } from "../src/lib/sizes.ts";

const sizes = soldOut => ["S", "M", "L", "XL"].map(size => ({ size, soldOut: soldOut.includes(size) }));

test("고른 사이즈에 재고가 있으면 그대로 둔다", () => {
  assert.equal(availableSize(sizes(["S"]), "M"), "M");
});

test("고른 사이즈가 품절이면 재고가 있는 첫 사이즈로 바꾼다", () => {
  assert.equal(availableSize(sizes(["S", "M"]), "M"), "L");
});

test("모든 사이즈가 품절이면 고를 사이즈가 없다", () => {
  assert.equal(availableSize(sizes(["S", "M", "L", "XL"]), "M"), null);
});

test("더 담을 수 있는 수량은 남은 수량과 한 옵션 한도 10장 중 작은 값에서 담은 수량을 뺀다", () => {
  assert.equal(addableQuantity(3, 0), 3);
  assert.equal(addableQuantity(10, 4), 6);
  assert.equal(addableQuantity(3, 3), 0);
  assert.equal(addableQuantity(2, 5), 0);
});

test("서버가 보낸 남은 수량 10은 10장 이상으로 표시한다", () => {
  assert.equal(remainingLabel(10), "남은 수량 10장 이상");
  assert.equal(remainingLabel(3), "남은 수량 3장");
  assert.equal(remainingLabel(0), "품절");
});
