/**
 * 브라우저의 localStorage와 sessionStorage에서 값을 읽고 쓰는 함수들을 모았다.
 * 저장소 접근에 실패하면 읽기는 null을 반환하고, 저장·삭제는 오류 없이 건너뛴다.
 *
 * 두 저장소는 브라우저가 기본으로 제공하므로 별도로 만들 필요가 없다.
 * localStorage는 저장한 값을 브라우저를 닫은 뒤에도 유지하며, 같은 사이트의 다른 탭과 공유한다.
 * sessionStorage는 탭마다 별도로 저장하고 새로고침 후에도 유지하지만, 탭을 닫으면 사라진다.
 */

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
