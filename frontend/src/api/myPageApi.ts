import { signedIn } from "../auth/keycloak";
import type { Address, AddressInput, OrderSummary } from "../types/myPage";
import { jsonBody, request } from "./http";

// 마이페이지 API는 쇼핑몰 관리자가 아닌 회원만 쓴다. 관리자 토큰이면 서버가 403으로 거부한다.
export async function getMyOrders(): Promise<OrderSummary[]> {
  return request<OrderSummary[]>("/me/orders", await signedIn());
}

export async function getAddresses(): Promise<Address[]> {
  return request<Address[]>("/me/addresses", await signedIn());
}

export async function createAddress(input: AddressInput): Promise<Address> {
  return request<Address>("/me/addresses", await signedIn(jsonBody("POST", input)));
}

export async function updateAddress(addressId: string, input: AddressInput): Promise<Address> {
  return request<Address>(`/me/addresses/${encodeURIComponent(addressId)}`, await signedIn(jsonBody("PUT", input)));
}

export async function makeDefaultAddress(addressId: string): Promise<Address> {
  return request<Address>(`/me/addresses/${encodeURIComponent(addressId)}/default`,
    await signedIn({ method: "PUT" }));
}

export async function deleteAddress(addressId: string): Promise<void> {
  await request<void>(`/me/addresses/${encodeURIComponent(addressId)}`, await signedIn({ method: "DELETE" }));
}
