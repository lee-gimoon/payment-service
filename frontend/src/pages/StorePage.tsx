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

function errorMessage(error: unknown): string {
  return error instanceof Error
    ? error.message
    : "연결을 확인한 뒤 주문 결과를 조회해주세요.";
}

export function StorePage() {
  const [currentOrder, setCurrentOrder] = useState<Order | null>(null);
  const [paymentConfig, setPaymentConfig] = useState<PaymentConfig | null>(null);
  const [lookupOrderId, setLookupOrderId] = useState("");
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState("");
  const busyRef = useRef(true);

  function showOrder(order: Order) {
    setCurrentOrder(order);
    setLookupOrderId(order.orderId);
    writeLocalValue(LAST_ORDER_ID_KEY, order.orderId);
  }

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

  useEffect(() => {
    let active = true;
    document.title = "한 장의 티셔츠 · 테스트 스토어";

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

    return () => {
      active = false;
    };
  }, []);

  function handleCreateOrder() {
    void runAction(async () => {
      showOrder(await createOrder());
    });
  }

  function handleLookup() {
    void runAction(async () => {
      showOrder(await getOrder(lookupOrderId.trim()));
    });
  }

  function handleRefresh() {
    if (!currentOrder) {
      return;
    }

    void runAction(async () => {
      showOrder(await getOrder(currentOrder.orderId));
    });
  }

  function handleReconcile() {
    if (!currentOrder) {
      return;
    }

    void runAction(async () => {
      showOrder(await reconcilePayment(currentOrder.orderId));
    });
  }

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
