import assert from "node:assert/strict";
import { test } from "node:test";
import { availableSize } from "../src/lib/sizes.ts";

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
