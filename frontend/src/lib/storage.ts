// 저장소가 제한된 브라우저에서도 주문·결제 요청은 계속 진행한다.
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
  } catch {}
}

export function removeLocalValue(key: string): void {
  try {
    window.localStorage.removeItem(key);
  } catch {}
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
  } catch {}
}

export function removeSessionValue(key: string): void {
  try {
    window.sessionStorage.removeItem(key);
  } catch {}
}
