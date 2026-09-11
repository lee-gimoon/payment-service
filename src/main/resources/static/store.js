const byId = (id) => document.getElementById(id);
const labels = { READY: '결제 대기', PROCESSING: '처리 중', SUCCEEDED: '결제 완료', FAILED: '결제 실패', UNKNOWN: '확인 필요' };
let currentOrder;
let paymentConfig;
let busy = false;

async function loadPaymentSdk() {
  if (typeof window.TossPayments === 'function') return;
  await new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src = 'https://js.tosspayments.com/v2/standard';
    script.onload = resolve;
    script.onerror = () => {
      script.remove();
      reject(new Error('결제창을 불러오지 못했습니다. 연결을 확인하고 다시 시도해주세요.'));
    };
    document.head.appendChild(script);
  });
  if (typeof window.TossPayments !== 'function') throw new Error('결제창 초기화에 실패했습니다. 잠시 후 다시 시도해주세요.');
}

function showError(message = '') {
  byId('error').textContent = message;
  byId('error').hidden = !message;
}

async function api(path, options) {
  const response = await fetch(path, options);
  const data = await response.json();
  if (!response.ok && !(response.status === 422 && data.payment)) throw new Error(data.message || '요청을 처리하지 못했습니다. 저장된 결과를 조회해주세요.');
  return data;
}

function render(order) {
  currentOrder = order;
  try { localStorage.setItem('lastOrderId', order.orderId); } catch { /* 저장소 사용이 제한되어도 주문은 계속 진행한다. */ }
  byId('order-id').value = order.orderId;
  byId('order-result').hidden = false;
  byId('status-title').textContent = labels[order.payment.status] || '결제 상태';
  byId('status-badge').textContent = labels[order.payment.status] || order.payment.status;
  byId('status-message').textContent = order.payment.message;
  byId('result-order-id').textContent = order.orderId;
  byId('result-product').textContent = `${order.productName} ${order.quantity}장 · ${order.amount.toLocaleString('ko-KR')}원`;
  byId('result-approved-at').textContent = order.payment.approvedAt ? new Date(order.payment.approvedAt).toLocaleString('ko-KR') : '—';
  byId('result-checked-at').textContent = order.payment.checkedAt ? new Date(order.payment.checkedAt).toLocaleString('ko-KR') : '—';
  byId('reconcile').hidden = !order.payment.canReconcile;
  byId('pay').hidden = order.payment.status !== 'READY';
  byId('pay').disabled = !paymentConfig?.enabled || busy;
  byId('create-order').hidden = ['READY', 'PROCESSING', 'UNKNOWN'].includes(order.payment.status);
  byId('create-order').textContent = '새 주문 만들기';
}

async function action(work) {
  if (busy) return;
  busy = true;
  showError();
  document.querySelectorAll('button').forEach((button) => { button.disabled = true; });
  try { await work(); }
  catch (error) { showError(error.message || '연결을 확인한 뒤 주문 결과를 조회해주세요.'); }
  finally {
    busy = false;
    document.querySelectorAll('button').forEach((button) => { button.disabled = false; });
    byId('pay').disabled = !paymentConfig?.enabled;
  }
}

byId('create-order').addEventListener('click', () => action(async () => render(await api('/orders', { method: 'POST' }))));
byId('lookup-form').addEventListener('submit', (event) => {
  event.preventDefault();
  action(async () => render(await api(`/orders/${encodeURIComponent(byId('order-id').value.trim())}`)));
});
byId('refresh').addEventListener('click', () => action(async () => render(await api(`/orders/${encodeURIComponent(currentOrder.orderId)}`))));
byId('reconcile').addEventListener('click', () => action(async () => render(await api(`/payments/${encodeURIComponent(currentOrder.orderId)}/reconcile`, { method: 'POST' }))));
byId('pay').addEventListener('click', () => action(async () => {
  if (!paymentConfig?.enabled) throw new Error('테스트 결제 설정을 확인해주세요.');
  await loadPaymentSdk();
  const order = await api(`/orders/${encodeURIComponent(currentOrder.orderId)}`);
  render(order);
  if (order.payment.status !== 'READY') return;
  const payment = TossPayments(paymentConfig.clientKey).payment({ customerKey: order.orderId });
  const base = `${location.origin}/result.html`;
  await payment.requestPayment({ method: 'CARD', amount: { currency: order.currency, value: order.amount },
    orderId: order.orderId, orderName: `${order.productName} ${order.quantity}장`,
    successUrl: `${base}?flow=success`, failUrl: `${base}?flow=fail&requestedOrderId=${encodeURIComponent(order.orderId)}`,
    card: { flowMode: 'DEFAULT' } });
}));

async function initialize() {
  paymentConfig = await api('/payment-config');
  byId('config-notice').textContent = paymentConfig.enabled ? '테스트 카드 결제를 사용할 수 있습니다.' : '테스트 결제 준비 중입니다. 주문 생성과 결과 조회는 사용할 수 있습니다.';
  let saved;
  try { saved = localStorage.getItem('lastOrderId'); } catch { /* 선택적 편의 기능 */ }
  if (saved) {
    byId('order-id').value = saved;
    try { render(await api(`/orders/${encodeURIComponent(saved)}`)); } catch { /* 오래된 주문번호는 직접 새로 조회할 수 있다. */ }
  }
}
action(initialize);
