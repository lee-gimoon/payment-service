/**
 * 파일 역할: 스토어의 주문 생성·조회·결제창 실행을 연결하는 첫 화면이다.
 * 흐름: CheckoutCard의 버튼 → 이 파일의 이벤트 처리 함수 → paymentApi 또는 tossPayments.
 */
import { useEffect, useRef, useState } from "react";
import {
  createOrder,
  getOrder,
  getPaymentConfig,
  reconcilePayment
} from "../api/paymentApi";
import { AppShell } from "../components/AppShell";
import { CheckoutCard } from "../components/CheckoutCard";
import { OrderLookup } from "../components/OrderLookup";
import { OrderResultCard } from "../components/OrderResultCard";
import { ProductCard } from "../components/ProductCard";
import { readLocalValue, writeLocalValue } from "../lib/storage";
import { openTossPayment } from "../payments/tossPayments";
import type { Order, PaymentConfig } from "../types/payment";

const LAST_ORDER_ID_KEY = "lastOrderId";

/** 잡힌 오류에서 사용자에게 보여줄 메시지를 꺼내고, 오류 형식을 모르면 기본 안내를 반환한다. */
function errorMessage(error: unknown): string {
  return error instanceof Error
    ? error.message
    : "연결을 확인한 뒤 주문 결과를 조회해주세요.";
}

/** 주문과 화면 상태를 관리하고, 하위 컴포넌트에 표시할 데이터와 버튼 동작을 전달한다. */
export function StorePage() {
  const [currentOrder, setCurrentOrder] = useState<Order | null>(null);
  const [paymentConfig, setPaymentConfig] = useState<PaymentConfig | null>(null);
  const [lookupOrderId, setLookupOrderId] = useState("");
  // state 변경은 다음 렌더링에 반영된다. busy는 버튼 비활성화 등 화면 표시에 사용한다.
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState("");
  // ref는 렌더링 사이에 유지되며 current를 바꾸면 즉시 읽을 수 있다. 변경 자체가 렌더링을 요청하지는 않는다.
  // 초기 설정 조회가 끝날 때까지 true로 두고, 이후에는 이 화면의 작업이 겹쳐 실행되는 것을 막는다.
  const busyRef = useRef(true);

  /** 받은 주문을 화면과 조회 입력란에 반영하고, 다음 방문에서 찾을 수 있도록 주문번호를 기억한다. */
  function showOrder(order: Order) {
    setCurrentOrder(order);
    setLookupOrderId(order.orderId);
    writeLocalValue(LAST_ORDER_ID_KEY, order.orderId);
  }

  /**
   * 전달받은 비동기 작업을 실행하면서 중복 진입, 버튼 비활성화, 오류 표시를 공통 처리한다.
   * work는 지금 실행할 주문 생성·조회 등의 함수이며, 성공과 실패 모두 finally에서 잠금을 해제한다.
   */
  async function runAction(work: () => Promise<void>) {
    if (busyRef.current) {
      return;
    }

    busyRef.current = true;
    setBusy(true);
    setError("");

    try {
      await work();
    } catch (actionError) {
      setError(errorMessage(actionError));
    } finally {
      busyRef.current = false;
      setBusy(false);
    }
  }

  // 화면이 마운트되면 초기 데이터를 읽는다. active는 화면을 떠난 뒤 도착한 응답의 반영을 막는다.
  useEffect(() => {
    let active = true;
    document.title = "한 장의 티셔츠 · 테스트 스토어";

    /** 공개 결제 설정을 읽고, 브라우저에 마지막 주문번호가 있으면 서버에서 최신 주문을 조회한다. */
    async function initializeStore() {
      try {
        const config = await getPaymentConfig();
        if (!active) {
          return;
        }
        setPaymentConfig(config);

        const savedOrderId = readLocalValue(LAST_ORDER_ID_KEY);
        if (!savedOrderId) {
          return;
        }

        setLookupOrderId(savedOrderId);
        try {
          const savedOrder = await getOrder(savedOrderId);
          if (active) {
            showOrder(savedOrder);
          }
        } catch {
          // 삭제되었거나 다른 개발 DB의 주문번호라면 직접 새 주문을 만들 수 있다.
        }
      } catch (initializationError) {
        if (active) {
          setPaymentConfig({ enabled: false, clientKey: "" });
          setError(errorMessage(initializationError));
        }
      } finally {
        if (active) {
          busyRef.current = false;
          setBusy(false);
        }
      }
    }

    void initializeStore();

    // effect 정리 함수: 네트워크 요청을 취소하는 대신 이 실행에서 받은 결과의 화면 반영을 중단한다.
    return () => {
      active = false;
    };
  }, []);

  /** 주문 만들기 버튼의 시작점: createOrder()로 POST /orders를 호출하고 반환된 주문을 표시한다. */
  function handleCreateOrder() {
    void runAction(async () => {
      showOrder(await createOrder());
    });
  }

  /** 입력한 주문번호의 앞뒤 공백을 제거한 뒤, 서버에 저장된 주문과 결제 상태를 조회한다. */
  function handleLookup() {
    void runAction(async () => {
      showOrder(await getOrder(lookupOrderId.trim()));
    });
  }

  /** 현재 화면에 표시된 주문을 서버 DB 기준으로 다시 읽는다. 토스에 직접 조회하지는 않는다. */
  function handleRefresh() {
    if (!currentOrder) {
      return;
    }

    void runAction(async () => {
      showOrder(await getOrder(currentOrder.orderId));
    });
  }

  /** 결과가 불명확한 주문에 대해 서버가 토스의 결제 결과를 조회하고 저장하도록 요청한다. */
  function handleReconcile() {
    if (!currentOrder) {
      return;
    }

    void runAction(async () => {
      showOrder(await reconcilePayment(currentOrder.orderId));
    });
  }

  /** 결제 버튼의 시작점: 주문을 다시 조회해 READY인지 확인한 후 토스 카드 인증창을 연다. */
  function handlePayment() {
    if (!currentOrder) {
      return;
    }

    void runAction(async () => {
      if (!paymentConfig?.enabled) {
        throw new Error("테스트 결제 설정을 확인해주세요.");
      }

      const latestOrder = await getOrder(currentOrder.orderId);
      showOrder(latestOrder);

      if (latestOrder.payment.status === "READY") {
        await openTossPayment(latestOrder, paymentConfig.clientKey);
      }
    });
  }

  return (
    <AppShell footerText="주문 → 카드 인증 → 결제 승인 → 결과 확인">
      <section className="intro">
        <p className="eyebrow">작은 주문, 완전한 결제 경험</p>
        <h1>
          시작은 한 장이면
          <br />
          충분하니까.
        </h1>
        <p>주문부터 결제 확인까지, 직접 경험하는 작은 스토어입니다.</p>
      </section>

      <div className="shop-grid">
        <ProductCard />
        <CheckoutCard
          order={currentOrder}
          paymentConfig={paymentConfig}
          busy={busy}
          onCreateOrder={handleCreateOrder}
          onPay={handlePayment}
        />
      </div>

      <OrderLookup
        orderId={lookupOrderId}
        busy={busy}
        onOrderIdChange={setLookupOrderId}
        onSubmit={handleLookup}
      />

      {currentOrder && (
        <OrderResultCard
          order={currentOrder}
          busy={busy}
          onRefresh={handleRefresh}
          onReconcile={handleReconcile}
        />
      )}

      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
    </AppShell>
  );
}
