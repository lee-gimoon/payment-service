/**
 * 파일 역할: 스토어의 주문 생성·조회·결제창 실행을 연결하는 첫 화면이다.
 * 흐름: CheckoutCard의 버튼 → 이 파일의 이벤트 처리 함수 → paymentApi 또는 tossPayments.
 */
import { useEffect, useRef, useState } from "react";
import {
  createOrder,
  getOrder,
  getPaymentConfig
} from "../api/paymentApi";
import { AppShell } from "../components/AppShell";
import { CheckoutCard } from "../components/CheckoutCard";
import { OrderLookup } from "../components/OrderLookup";
import { OrderResultCard } from "../components/OrderResultCard";
import { ProductCard } from "../components/ProductCard";
import { readLocalValue, writeLocalValue } from "../lib/storage";
import { openTossPayment } from "../payments/tossPayments";
import type { Order, PaymentConfig } from "../types/payment";

/** 마지막 주문번호를 localStorage에 저장하거나 찾을 때 쓰는 항목 이름이다. 실제 주문번호는 이 항목의 값으로 저장한다. */
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
  // 화면용 상태: true이면 JSX의 disabled={busy} 때문에 버튼이 비활성화된다.
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState("");
  // ref로 값 참조하기: useRef()는 current에 값을 담는 객체를 반환하며, 값을 바꿔도 재렌더링하지 않고 다음 렌더링에서도 그 값을 유지한다.
  // ref로 DOM 조작하기: JSX 요소에 ref를 전달하면 React가 그 DOM 요소를 ref.current에 넣어 focus() 같은 메서드로 조작할 수 있다.
  // ref 콘텐츠 재생성 피하기: 초기값은 첫 렌더링에만 저장되지만 초기값을 만드는 식은 매번 실행되므로, 비용 큰 객체는 current가 null일 때만 생성한다.
  const busyRef = useRef(true);
  const paymentAbortRef = useRef<AbortController | null>(null);

  /** 받은 주문을 화면과 조회 입력란에 반영하고, 다음 방문에서 찾을 수 있도록 주문번호를 기억한다. */
  function showOrder(order: Order) {
    setCurrentOrder(order);
    setLookupOrderId(order.orderId);
    writeLocalValue(LAST_ORDER_ID_KEY, order.orderId);
  }

  /**
   * 주문 생성·조회·결제 등 버튼을 눌렀을 때 각 handle 함수가 호출하는 공통 작업 처리 함수다.
   * handle 함수가 전달한 work()를 실행하되, 이미 다른 작업이 진행 중이면 이번 호출을 건너뛴다.
   * 실행 중에는 버튼을 비활성화하고, 실패하면 오류를 표시하며, 끝나면 다시 버튼을 사용할 수 있게 한다.
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
      if (!paymentAbortRef.current?.signal.aborted) setError(errorMessage(actionError));
    } finally {
      if (!paymentAbortRef.current?.signal.aborted) {
        busyRef.current = false;
        setBusy(false);
      }
    }
  }

  /**
   * useEffect는 화면을 렌더링한 뒤 실행할 작업을 React에 지정하는 Hook이다.
   * []이므로 StorePage가 처음 마운트된 뒤 실행하고, 상태 변경에 따른 재렌더링에서는 다시 실행하지 않는다.
   * StorePage의 탭 제목과 결제 설정(사용 가능 여부·브라우저용 키)을 준비하고, 저장된 주문번호가 있으면 주문을 조회한다.
   * 초기 처리가 끝나면 busy를 false로 바꾸며, 화면을 떠난 뒤 도착한 응답은 active로 무시한다.
   */
  useEffect(() => {
    // 서버 응답이 도착했을 때 StorePage가 아직 열려 있는지 확인하는 변수다.
    // 화면을 떠나면 false로 바꿔, 늦게 온 응답으로 setPaymentConfig() 등을 호출하지 않는다.
    let active = true;
    const paymentAbort = new AbortController();
    paymentAbortRef.current = paymentAbort;
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
          setPaymentConfig({ enabled: false, clientKey: "", paymentMethodVariantKey: "", agreementVariantKey: "" });
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

    // useEffect 콜백이 반환하는 정리 함수다. React는 효과를 다시 실행하기 전이나 StorePage가 제거될 때 자동 호출한다.
    // 여기서는 active를 false로 바꿔, 나중에 도착한 서버 응답이 상태를 바꾸지 못하게 한다.
    return () => {
      active = false;
      paymentAbort.abort();
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

  /** 결제 버튼의 시작점: 주문을 다시 조회해 READY인지 확인한 후 토스 결제수단 인증창을 연다. */
  function handlePayment() {
    if (!currentOrder) {
      return;
    }

    void runAction(async () => {
      if (!paymentConfig?.enabled) {
        throw new Error("테스트 결제 설정을 확인해주세요.");
      }

      const latestOrder = await getOrder(currentOrder.orderId);
      const signal = paymentAbortRef.current?.signal;
      if (!signal || signal.aborted) return;
      showOrder(latestOrder);

      if (latestOrder.payment.status === "READY") {
        await openTossPayment(latestOrder, paymentConfig, signal);
      }
    });
  }

  return (
    <AppShell footerText="주문 → 결제수단 인증 → 결제 승인 → 결과 확인">
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
