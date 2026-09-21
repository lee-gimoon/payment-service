/**
 * 파일 역할: 토스 결제창에서 돌아온 /payment/result 화면의 승인 요청과 결과 확인을 담당한다.
 * 흐름: paymentRedirect로 인증 결과 읽기 → paymentApi로 서버 승인 요청 → 주문·결제 상태 표시.
 */
import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import {
  confirmPayment,
  getOrder,
  reconcilePayment
} from "../api/paymentApi";
import { AppShell } from "../components/AppShell";
import {
  formatAmount,
  formatDateTime,
  paymentStatusLabel
} from "../lib/formatters";
import { removeSessionValue, writeLocalValue } from "../lib/storage";
import { readPaymentRedirect } from "../payments/paymentRedirect";
import type { ConfirmPaymentCommand, Order } from "../types/payment";

const LAST_ORDER_ID_KEY = "lastOrderId";

/** 통신 등에서 발생한 오류를 화면에 표시할 문장으로 바꾸고, 해석할 수 없으면 기본 안내를 쓴다. */
function errorMessage(error: unknown): string {
  return error instanceof Error
    ? error.message
    : "결과를 확인하지 못했습니다. 다시 결제하지 말고 저장된 결과를 조회해주세요.";
}

/** 카드 인증 결과로 승인을 요청하고, 주문 조회·PG 결과 재확인·승인 요청 재전송 버튼을 관리한다. */
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
  // 화면용 상태: true이면 JSX의 disabled={busy} 때문에 결과 조회와 재확인 버튼이 비활성화된다.
  const [busy, setBusy] = useState(true);
  // ref로 값 참조하기: useRef()는 current에 값을 담는 객체를 반환하며, 값을 바꿔도 재렌더링하지 않고 다음 렌더링에서도 그 값을 유지한다.
  // ref로 DOM 조작하기: JSX 요소에 ref를 전달하면 React가 그 DOM 요소를 ref.current에 넣어 focus() 같은 메서드로 조작할 수 있다.
  // ref 콘텐츠 재생성 피하기: 초기값은 첫 렌더링에만 저장되지만 초기값을 만드는 식은 매번 실행되므로, 비용 큰 객체는 current가 null일 때만 생성한다.
  const busyRef = useRef(true);

  /**
   * 서버가 반환한 상태와 안내를 표시한다. 결제 시도가 이미 기록되었다면 임시 승인 요청 정보를 지운다.
   * UNKNOWN도 서버에 시도가 있는 상태이므로 이후에는 주문 조회와 PG 결과 재확인으로 이어진다.
   */
  function showOrder(nextOrder: Order) {
    setOrder(nextOrder);
    setTitle(paymentStatusLabel(nextOrder.payment.status));
    setMessage(nextOrder.payment.message);
    setError("");
    writeLocalValue(LAST_ORDER_ID_KEY, nextOrder.orderId);

    if (nextOrder.payment.status !== "READY") {
      setConfirmation(null);
      if (redirect.storageKey) {
        removeSessionValue(redirect.storageKey);
      }
    }
  }

  /** 요청 오류를 결제 실패로 단정하지 않고, 저장된 결과부터 조회하도록 안내한다. */
  function showRequestError(requestError: unknown) {
    setTitle("결제 결과 확인이 필요합니다");
    setMessage(
      "통신 오류만으로 결제 실패를 판단할 수 없습니다. 주문 결과를 먼저 조회해주세요."
    );
    setError(errorMessage(requestError));
  }

  /**
   * 결과 조회·PG 재확인·승인 요청 버튼을 눌렀을 때 각 handle 함수가 호출하는 공통 작업 처리 함수다.
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
    } catch (requestError) {
      showRequestError(requestError);
    } finally {
      busyRef.current = false;
      setBusy(false);
    }
  }

  // 화면 진입 시 인증 결과를 처리한다. 화면을 떠나면 active로 늦게 도착한 응답의 반영을 막는다.
  useEffect(() => {
    let active = true;
    document.title = "결제 결과 · 한 장의 티셔츠";

    /**
     * 인증 실패로 돌아오면 저장된 주문만 조회한다. 승인 정보가 있으면 서버에 최종 승인을 요청한다.
     * 인증 성공 리다이렉트 자체는 결제 완료가 아니며, 승인 정보가 없는 재방문에서는 주문을 조회한다.
     */
    async function initializeResult() {
      try {
        if (redirect.flow === "fail") {
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
          throw new Error("주문번호가 없습니다. 스토어에서 주문번호로 조회해주세요.");
        }
        if (redirect.flow === "success" && !redirect.confirmation) {
          throw new Error("인증 결과의 결제 키 또는 금액이 올바르지 않습니다. 주문 결과를 조회해주세요.");
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

    // effect 정리 함수: 진행 중인 요청의 완료 후 이 화면 상태를 갱신하지 않게 한다.
    return () => {
      active = false;
    };
  }, []);

  /** 주문번호로 우리 서버 DB에 기록된 결과를 다시 읽어 화면에 반영한다. */
  function handleRefresh() {
    if (!redirect.orderId) {
      return;
    }

    void runAction(async () => {
      showOrder(await getOrder(redirect.orderId as string));
    });
  }

  /** 서버가 기존 paymentKey로 토스의 결과를 조회하고 DB와 맞추도록 요청한다. 재승인 요청은 아니다. */
  function handleReconcile() {
    if (!redirect.orderId) {
      return;
    }

    void runAction(async () => {
      showOrder(await reconcilePayment(redirect.orderId as string));
    });
  }

  /**
   * 임시 승인 정보가 남아 있을 때 동일한 승인 요청을 서버에 다시 전달한다.
   * 서버는 이미 기록된 결제 시도가 있으면 저장된 결과를 반환하므로 기존 시도를 새로 만들지 않는다.
   */
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

      <section className="result-card" aria-live="polite" aria-busy={busy}>
        <dl className="result-details">
          <div>
            <dt>주문번호</dt>
            <dd>{order?.orderId || redirect.orderId || "확인 중"}</dd>
          </div>
          <div>
            <dt>결제 금액</dt>
            <dd>{order ? formatAmount(order.amount) : "—"}</dd>
          </div>
          <div>
            <dt>결제 상태</dt>
            <dd>{order ? paymentStatusLabel(order.payment.status) : "—"}</dd>
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
              저장된 결과 조회
            </button>
          )}
          {order?.payment.canReconcile && (
            <button type="button" disabled={busy} onClick={handleReconcile}>
              PG 결과 재확인
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

      <Link className="back-link" to="/">
        ← 스토어로 돌아가기
      </Link>
    </AppShell>
  );
}
