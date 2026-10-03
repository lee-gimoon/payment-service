import { formatPhone } from "../lib/addresses";
import type { Address } from "../types/myPage";
import type { ShippingInfo } from "../types/payment";

/** 저장한 배송지 한 개. 마이페이지와 결제 전 배송지 고르기에서 같은 모양으로 보여준다. */
export function AddressLines({ address }: { address: Address }) {
  return <>
    <div className="address-card-title">
      <strong>{address.label}</strong>
      {address.defaultAddress && <span className="status-badge">기본 배송지</span>}
    </div>
    <p>{address.recipientName} · {formatPhone(address.phone)}</p>
    <p>({address.postalCode}) {address.address}{address.addressDetail && `, ${address.addressDetail}`}</p>
  </>;
}

/** 주문에 복사해 둔 배송지. */
export function ShippingLines({ shipping }: { shipping: ShippingInfo }) {
  return <>
    <p>{shipping.recipientName} · {formatPhone(shipping.phone)}</p>
    <p>({shipping.postalCode}) {shipping.address}{shipping.addressDetail && `, ${shipping.addressDetail}`}</p>
    {shipping.memo && <p className="subtle">배송 메모: {shipping.memo}</p>}
  </>;
}
