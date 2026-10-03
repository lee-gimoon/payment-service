import assert from "node:assert/strict";
import { test } from "node:test";
import { formatPhone, postcodeAddress, toAddressInput } from "../src/lib/addresses.ts";

test("숫자만 저장된 연락처에 하이픈을 넣는다", () => {
  assert.equal(formatPhone("01012345678"), "010-1234-5678");
  assert.equal(formatPhone("0212345678"), "02-1234-5678");
  assert.equal(formatPhone("021234567"), "02-123-4567");
  assert.equal(formatPhone("0311234567"), "031-123-4567");
  assert.equal(formatPhone("12345"), "12345");
});

const road = {
  zonecode: "06236", userSelectedType: "R", roadAddress: "서울 강남구 테헤란로 123", jibunAddress: "서울 강남구 역삼동 737",
  bname: "역삼동", buildingName: "모도타워", apartment: "N"
};

test("도로명 주소에는 법정동을, 아파트면 건물명도 괄호로 덧붙인다", () => {
  assert.deepEqual(postcodeAddress(road), { postalCode: "06236", address: "서울 강남구 테헤란로 123 (역삼동)" });
  assert.deepEqual(postcodeAddress({ ...road, apartment: "Y", buildingName: "모도아파트" }),
    { postalCode: "06236", address: "서울 강남구 테헤란로 123 (역삼동, 모도아파트)" });
  assert.equal(postcodeAddress({ ...road, bname: "역삼1리" }).address, "서울 강남구 테헤란로 123");
});

test("지번 주소를 고르면 지번 주소를 쓴다", () => {
  assert.deepEqual(postcodeAddress({ ...road, userSelectedType: "J" }), { postalCode: "06236", address: "서울 강남구 역삼동 737" });
});

test("수정 폼은 저장된 연락처를 하이픈 넣은 형태로 채운다", () => {
  const input = toAddressInput({
    id: "a1", label: "집", recipientName: "홍길동", phone: "01012345678", postalCode: "06236",
    address: "서울 강남구 테헤란로 123", addressDetail: "101호", defaultAddress: true
  });
  assert.equal(input.phone, "010-1234-5678");
  assert.equal(input.makeDefault, true);
});
