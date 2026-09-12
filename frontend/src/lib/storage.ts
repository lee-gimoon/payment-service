/** 파일 역할: 브라우저 저장소가 차단되더라도 화면 동작을 계속할 수 있도록 저장소 접근 오류를 처리한다. */

/** 브라우저를 닫아도 유지되는 localStorage에서 값을 읽고, 접근할 수 없으면 null을 반환한다. */
export function readLocalValue(key: string): string | null {
  try {
    return window.localStorage.getItem(key);
  } catch {
    return null;
  }
}

/** 마지막 주문번호처럼 다음 방문에 사용할 값을 localStorage에 보관한다. */
export function writeLocalValue(key: string, value: string): void {
  try {
    window.localStorage.setItem(key, value);
  } catch {
    // 브라우저 저장소가 제한되어도 결제 흐름은 계속 진행한다.
  }
}

/** 같은 탭의 새로고침에도 유지되는 sessionStorage에서 임시 값을 읽는다. */
export function readSessionValue(key: string): string | null {
  try {
    return window.sessionStorage.getItem(key);
  } catch {
    return null;
  }
}

/** 승인 정보처럼 현재 탭에서 복구에 사용할 임시 값을 sessionStorage에 보관한다. */
export function writeSessionValue(key: string, value: string): void {
  try {
    window.sessionStorage.setItem(key, value);
  } catch {
    // 현재 화면의 메모리에는 값이 남으므로 승인 요청을 계속할 수 있다.
  }
}

/** 서버에 결제 시도가 기록된 뒤 더 이상 필요하지 않은 임시 승인 정보를 제거한다. */
export function removeSessionValue(key: string): void {
  try {
    window.sessionStorage.removeItem(key);
  } catch {
    // 선택적인 복구 데이터이므로 제거 실패가 결제 결과를 바꾸지 않는다.
  }
}
