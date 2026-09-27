import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import {
  confirmPayment,
  getOrder,
  recordAuthenticationResult
} from "../api/paymentApi";
import { AppShell } from "../components/AppShell";
import {
  formatAmount,
  formatDateTime,
  paymentStatusLabel
} from "../lib/formatters";
import { removeLocalValue, removeSessionValue, writeLocalValue } from "../lib/storage";
import { readPaymentRedirect } from "../payments/paymentRedirect";
import type { ConfirmPaymentCommand, Order } from "../types/payment";

const LAST_ORDER_ID_KEY = "lastOrderId";

function errorMessage(error: unknown): string {
  return error instanceof Error
    ? error.message
    : "결과를 확인하지 못했습니다. 다시 결제하지 말고 저장된 결과를 조회해주세요.";
}

export function PaymentResultPage() {
  const [redirect] = useState(readPaymentRedirect);
  const [order, setOrder] = useState<Order | null>(null);
  const [confirmation, setConfirmation] = useState<ConfirmPaymentCommand | null>(
    redirect.confirmation
  );
  const [title, setTitle] = useState("결제 결과를 확인하고 있습니다.");
  const [message, setMessage] = useState(
    "결제수단 인증 후 서버에서 최종 승인 결과를 확인합니다."
  );
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(true);
  const busyRef = useRef(true);

  function showOrder(nextOrder: Order) {
    setOrder(nextOrder);
    setTitle(nextOrder.payment.status === "SUCCEEDED" ? "결제가 완료되었습니다." : paymentStatusLabel(nextOrder.payment.status));
    setMessage(nextOrder.payment.status === "SUCCEEDED"
      ? "결제가 정상적으로 승인되었습니다. 주문 내역에서 상품과 결제 정보를 확인하세요."
      : nextOrder.payment.message);
    setError("");
    writeLocalValue(LAST_ORDER_ID_KEY, nextOrder.orderId);
    if (nextOrder.payment.status === "SUCCEEDED") {
      removeLocalValue("pendingOrderId");
    }

    // 서버에 기록된 결제는 재승인 대신 저장된 결과를 조회한다.
    if (nextOrder.payment.status !== "READY") {
      setConfirmation(null);
      if (redirect.storageKey) {
        removeSessionValue(redirect.storageKey);
      }
    }
  }

  function showRequestError(requestError: unknown) {
    setTitle("결제 결과 확인이 필요합니다");
    setMessage(
      "요청 결과를 받지 못했습니다. 주문 내역을 조회하고, 확인되지 않으면 주문번호로 문의해주세요."
    );
    setError(errorMessage(requestError));
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
    } catch (requestError) {
      showRequestError(requestError);
    } finally {
      busyRef.current = false;
      setBusy(false);
    }
  }

  useEffect(() => {
    let active = true;
    document.title = "결제 결과 · MODO CLUB";

    async function initializeResult() {
      try {
        if (redirect.flow === "fail") {
          if (redirect.attemptId) {
            await recordAuthenticationResult(redirect.attemptId,
              redirect.errorCode === "PAY_PROCESS_CANCELED" ? "AUTH_CANCELED" : "AUTH_FAILED",
              redirect.errorCode);
          }
          const failedOrder = redirect.orderId ? await getOrder(redirect.orderId) : null;
          if (!active) {
            return;
          }

          if (failedOrder) {
            showOrder(failedOrder);
          }
          if (!failedOrder || failedOrder.payment.status === "READY") {
            setTitle("결제수단 인증이 완료되지 않았습니다");
            setMessage(
              redirect.errorMessage || "결제창에서 인증이 취소되었거나 실패했습니다. 스토어로 돌아가 다시 진행할 수 있습니다."
            );
            setError(redirect.errorCode || "");
          }
          return;
        }

        if (!redirect.orderId) {
          throw new Error("주문번호가 없습니다. 주문 확인 페이지에서 주문번호로 조회해주세요.");
        }
        if (redirect.flow === "success" && !redirect.confirmation) {
          throw new Error("인증 결과의 결제 시도, 결제 키 또는 금액이 올바르지 않습니다. 주문 결과를 조회해주세요.");
        }

        const loadedOrder = redirect.confirmation
          ? await confirmPayment(redirect.confirmation)
          : await getOrder(redirect.orderId);

        if (active) {
          showOrder(loadedOrder);
        }
      } catch (requestError) {
        if (active) {
          showRequestError(requestError);
        }
      } finally {
        if (active) {
          busyRef.current = false;
          setBusy(false);
        }
      }
    }

    void initializeResult();
    return () => {
      active = false;
    };
  }, []);
  function handleRefresh() {
    if (!redirect.orderId) {
      return;
    }

    void runAction(async () => {
      showOrder(await getOrder(redirect.orderId as string));
    });
  }
  function handleRetryConfirmation() {
    if (!confirmation) {
      return;
    }

    void runAction(async () => {
      showOrder(await confirmPayment(confirmation));
    });
  }

  return (
    <AppShell footerText="테스트 결제 결과" mainClassName="result-page">
      <p className="eyebrow">PAYMENT RESULT</p>
      <h1>{title}</h1>
      <p role="status">{message}</p>

      <Link className="primary-button result-cta" to={order?.orderId || redirect.orderId ? `/orders/${order?.orderId ?? redirect.orderId}` : "/orders"}>
        {order?.payment.status === "SUCCEEDED" ? "결제 확인하기" : "주문 상태 확인하기"} <span aria-hidden="true">↗</span>
      </Link>

      <section className="result-card" aria-live="polite" aria-busy={busy}>
        <dl className="result-details">
          <div>
            <dt>주문번호</dt>
            <dd>{order?.orderId || redirect.orderId || "확인 중"}</dd>
          </div>
          <div>
            <dt>주문 금액</dt>
            <dd>{order ? formatAmount(order.amount) : "—"}</dd>
          </div>
          <div>
            <dt>결제 상태</dt>
            <dd>{order ? paymentStatusLabel(order.payment.status) : "—"}</dd>
          </div>
          <div>
            <dt>실제 승인 금액</dt>
            <dd>{order?.payment.paidAmount == null ? "—" : `${order.payment.paidAmount.toLocaleString("ko-KR")} ${order.payment.paidCurrency}`}</dd>
          </div>
          <div>
            <dt>승인 시각</dt>
            <dd>{formatDateTime(order?.payment.approvedAt ?? null)}</dd>
          </div>
          {order?.payment.errorCode && (
            <div>
              <dt>오류 코드</dt>
              <dd>{order.payment.errorCode}</dd>
            </div>
          )}
        </dl>

        <div className="actions">
          {redirect.orderId && (
            <button className="secondary" type="button" disabled={busy} onClick={handleRefresh}>
              주문 내역 새로고침
            </button>
          )}
          {confirmation && (
            <button type="button" disabled={busy} onClick={handleRetryConfirmation}>
              승인 요청 다시 확인
            </button>
          )}
        </div>
      </section>

      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}

      <Link className="back-link" to="/">← 스토어로 돌아가기</Link>
    </AppShell>
  );
}
