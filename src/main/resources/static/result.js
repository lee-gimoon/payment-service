const byId = (id) => document.getElementById(id);
const params = new URLSearchParams(location.search);
const orderId = params.get('orderId') || params.get('requestedOrderId');
const flow = params.get('flow');
const labels = { READY: '결제 대기', PROCESSING: '처리 중', SUCCEEDED: '결제 완료', FAILED: '결제 실패', UNKNOWN: '확인 필요' };
let confirmation;
let busy = false;
const storageKey = `pendingConfirmation:${orderId}`;

// URL의 paymentKey가 다른 페이지의 Referer로 전달되지 않도록 바로 제거한다.
if (flow === 'success' && params.get('paymentKey') && /^\d{1,12}$/.test(params.get('amount') || '')) {
  confirmation = { orderId, paymentKey: params.get('paymentKey'), amount: Number(params.get('amount')) };
  try { sessionStorage.setItem(storageKey, JSON.stringify(confirmation)); } catch { /* 현재 화면에서는 계속 진행 가능 */ }
} else if (flow !== 'fail') {
  try { confirmation = JSON.parse(sessionStorage.getItem(storageKey)); } catch { /* 저장소 제한 시 조회만 사용 */ }
}
history.replaceState(null, '', `/result.html?orderId=${encodeURIComponent(orderId || '')}`);

function render(order) {
  byId('order-id').textContent = order.orderId;
  byId('amount').textContent = `${order.amount.toLocaleString('ko-KR')}원`;
  byId('status').textContent = labels[order.payment.status] || order.payment.status;
  byId('title').textContent = labels[order.payment.status] || '결제 결과';
  byId('message').textContent = order.payment.message;
  byId('approved-at').textContent = order.payment.approvedAt ? new Date(order.payment.approvedAt).toLocaleString('ko-KR') : '—';
  byId('refresh').hidden = false;
  byId('reconcile').hidden = !order.payment.canReconcile;
  if (order.payment.attemptId) {
    confirmation = null;
    try { sessionStorage.removeItem(storageKey); } catch { /* 저장소 제한 */ }
  }
  byId('retry-confirm').hidden = !confirmation;
  try { localStorage.setItem('lastOrderId', order.orderId); } catch { /* 저장소 제한 */ }
}

async function request(path, options) {
  const response = await fetch(path, options);
  const data = await response.json();
  if (!response.ok && !(response.status === 422 && data.payment)) throw new Error(data.message || '결과를 확인하지 못했습니다. 다시 결제하지 말고 저장된 결과를 조회해주세요.');
  render(data);
  return data;
}

async function action(work) {
  if (busy) return;
  busy = true;
  byId('error').hidden = true;
  document.querySelectorAll('button').forEach((button) => { button.disabled = true; });
  try { await work(); }
  catch (error) {
    byId('title').textContent = '결제 결과 확인이 필요합니다';
    byId('message').textContent = '통신 오류만으로 결제 실패를 판단할 수 없습니다. 주문 결과를 먼저 조회해주세요.';
    byId('error').textContent = error.message;
    byId('error').hidden = false;
    byId('refresh').hidden = !orderId;
    byId('retry-confirm').hidden = !confirmation;
  } finally {
    busy = false;
    document.querySelectorAll('button').forEach((button) => { button.disabled = false; });
  }
}

async function confirm() {
  await request('/payments/confirm', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(confirmation) });
}
byId('refresh').addEventListener('click', () => action(() => request(`/orders/${encodeURIComponent(orderId)}`)));
byId('reconcile').addEventListener('click', () => action(() => request(`/payments/${encodeURIComponent(orderId)}/reconcile`, { method: 'POST' })));
byId('retry-confirm').addEventListener('click', () => action(confirm));

action(async () => {
  if (!orderId) throw new Error('주문번호가 없습니다. 스토어에서 주문번호로 조회해주세요.');
  byId('order-id').textContent = orderId;
  if (flow === 'fail') {
    const order = await request(`/orders/${encodeURIComponent(orderId)}`);
    if (order.payment.status === 'READY') {
      byId('title').textContent = '카드 인증이 완료되지 않았습니다';
      byId('message').textContent = '결제창에서 인증이 취소되었거나 실패했습니다. 스토어로 돌아가 다시 진행할 수 있습니다.';
    }
  } else if (confirmation) {
    await confirm();
  } else {
    await request(`/orders/${encodeURIComponent(orderId)}`);
  }
});
