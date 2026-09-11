export function readLocalValue(key: string): string | null {
  try {
    return window.localStorage.getItem(key);
  } catch {
    return null;
  }
}

export function writeLocalValue(key: string, value: string): void {
  try {
    window.localStorage.setItem(key, value);
  } catch {
    // 브라우저 저장소가 제한되어도 결제 흐름은 계속 진행한다.
  }
}

export function readSessionValue(key: string): string | null {
  try {
    return window.sessionStorage.getItem(key);
  } catch {
    return null;
  }
}

export function writeSessionValue(key: string, value: string): void {
  try {
    window.sessionStorage.setItem(key, value);
  } catch {
    // 현재 화면의 메모리에는 값이 남으므로 승인 요청을 계속할 수 있다.
  }
}

export function removeSessionValue(key: string): void {
  try {
    window.sessionStorage.removeItem(key);
  } catch {
    // 선택적인 복구 데이터이므로 제거 실패가 결제 결과를 바꾸지 않는다.
  }
}
