import type { Address, AddressInput } from "../types/myPage";

/** 서버와 같은 한도. 넘으면 서버가 409 ADDRESS_LIMIT_EXCEEDED로 거부한다. */
export const MAX_ADDRESSES = 10;

/** 서버의 연락처 검사와 같은 형식. 하이픈은 있어도 없어도 된다. */
export const PHONE_PATTERN = "0\\d{1,2}-?\\d{3,4}-?\\d{4}";

/** 숫자만 저장된 연락처에 하이픈을 넣는다. 서울 지역번호 02만 두 자리로 나눈다. */
export function formatPhone(digits: string): string {
  if (!/^0\d{8,10}$/.test(digits)) return digits;
  const head = digits.startsWith("02") ? 2 : 3;
  const tail = digits.length - 4;
  return `${digits.slice(0, head)}-${digits.slice(head, tail)}-${digits.slice(tail)}`;
}

/** 카카오(다음) 우편번호 찾기가 돌려주는 값 중 이 화면에서 쓰는 것. */
export interface PostcodeResult {
  zonecode: string;
  userSelectedType: "R" | "J";
  roadAddress: string;
  jibunAddress: string;
  bname: string;
  buildingName: string;
  apartment: "Y" | "N";
}

/** 고른 주소를 우편번호와 주소로 바꾼다. 도로명 주소에는 법정동과 아파트 이름을 괄호로 덧붙인다. */
export function postcodeAddress(result: PostcodeResult): { postalCode: string; address: string } {
  if (result.userSelectedType === "J" && result.jibunAddress) {
    return { postalCode: result.zonecode, address: result.jibunAddress };
  }
  const extras = [
    /[동로가]$/.test(result.bname) ? result.bname : "",
    result.apartment === "Y" ? result.buildingName : ""
  ].filter(Boolean);
  return {
    postalCode: result.zonecode,
    address: extras.length > 0 ? `${result.roadAddress} (${extras.join(", ")})` : result.roadAddress
  };
}

export function emptyAddressInput(): AddressInput {
  return { label: "", recipientName: "", phone: "", postalCode: "", address: "", addressDetail: "", makeDefault: false };
}

export function toAddressInput(address: Address): AddressInput {
  return {
    label: address.label,
    recipientName: address.recipientName,
    phone: formatPhone(address.phone),
    postalCode: address.postalCode,
    address: address.address,
    addressDetail: address.addressDetail,
    makeDefault: address.defaultAddress
  };
}
