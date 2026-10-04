import type { MouseEvent } from "react";

/**
 * 운영 배포(Railway)는 방문이 없으면 백엔드와 Keycloak을 재운다. 잠든 서버는 첫 요청에 502로 답하거나 늦게 답하고,
 * 깨어나는 데 수십 초가 걸린다. 그래서 한동안 응답이 없던 서버에는 가벼운 GET 확인 요청을 되풀이해 먼저 깨우고,
 * 주문·결제 같은 실제 요청은 서버가 깨어난 뒤 한 번만 보낸다.
 * 서버를 재우지 않는 로컬 개발(VITE_SERVERS_SLEEP 미설정)에서는 기다리지 않는다.
 */

/** Railway는 마지막 통신 뒤 5~10분 사이에 재운다. 이보다 짧은 시간 안에 응답한 서버는 깨어 있다고 본다. */
const AWAKE_WINDOW_MS = 4 * 60_000;

export interface WakeTiming {
  /** 확인 요청 사이 간격 */
  probeIntervalMs: number;
  /** 확인 요청 하나를 기다리는 시간 */
  probeTimeoutMs: number;
  /** 이 시간이 지나도 깨어나지 않으면 기다리기를 멈추고 실제 요청이 평소처럼 실패를 알리게 한다 */
  giveUpAfterMs: number;
  /** 금방 끝나는 확인에는 안내를 띄우지 않는다 */
  noticeDelayMs: number;
}

const DEFAULT_TIMING: WakeTiming = { probeIntervalMs: 2_000, probeTimeoutMs: 10_000, giveUpAfterMs: 120_000, noticeDelayMs: 800 };

// 안내를 띄운 채 깨우는 중인 서버 수. 화면의 안내 띠(ServerWakeupNotice)가 구독한다.
let wakingCount = 0;
const listeners = new Set<() => void>();

function changeWaking(delta: number): void {
  wakingCount += delta;
  listeners.forEach(listener => listener());
}

export function subscribeWaking(listener: () => void): () => void {
  listeners.add(listener);
  return () => { listeners.delete(listener); };
}

export function isWaking(): boolean {
  return wakingCount > 0;
}

export class SleepingServer {
  /** 서버를 재우는 배포인지. false면 아무것도 기다리지 않는다. */
  readonly sleeps: boolean;
  private readonly probeUrl: string;
  private readonly timing: WakeTiming;
  private lastSeen = 0;
  private waking: Promise<void> | null = null;

  /** probeUrl은 로그인 없이 200으로 답하는 가벼운 GET 주소다. */
  constructor(probeUrl: string, sleeps = import.meta.env?.VITE_SERVERS_SLEEP === "true", timing = DEFAULT_TIMING) {
    this.probeUrl = probeUrl;
    this.sleeps = sleeps;
    this.timing = timing;
  }

  /** 서버가 실제로 응답했을 때 부른다. 그 뒤 한동안은 확인 없이 요청한다. */
  markAwake(): void {
    this.lastSeen = Date.now();
  }

  /**
   * 깨어 있다고 볼 수 없으면 확인 요청으로 깨운다. 여러 요청이 동시에 불러도 확인은 한 줄로 한다.
   * force는 방금 실패한 요청처럼 서버가 잠들었을 수 있다고 이미 아는 경우에 쓴다.
   */
  whenAwake(force = false): Promise<void> {
    if (!this.needsWake(force)) return Promise.resolve();
    this.waking ??= this.wake().finally(() => { this.waking = null; });
    return this.waking;
  }

  /** 이 서버의 화면(Keycloak 계정 설정 등)으로 가는 링크의 onClick. 깨어 있지 않으면 깨운 뒤 이동한다. */
  followWhenAwake = (event: MouseEvent<HTMLAnchorElement>): void => {
    // 새 탭 열기 같은 보조 클릭은 브라우저에 맡긴다.
    if (!this.needsWake(false) || event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
    event.preventDefault();
    const href = event.currentTarget.href;
    void this.whenAwake().then(() => window.location.assign(href));
  };

  private needsWake(force: boolean): boolean {
    return this.sleeps && (force || Date.now() - this.lastSeen >= AWAKE_WINDOW_MS);
  }

  private async wake(): Promise<void> {
    let noticed = false;
    const notice = setTimeout(() => {
      noticed = true;
      changeWaking(1);
    }, this.timing.noticeDelayMs);
    const giveUpAt = Date.now() + this.timing.giveUpAfterMs;
    try {
      while (!(await this.probe()) && Date.now() < giveUpAt) {
        await new Promise(resolve => setTimeout(resolve, this.timing.probeIntervalMs));
      }
    } finally {
      clearTimeout(notice);
      if (noticed) changeWaking(-1);
    }
  }

  private async probe(): Promise<boolean> {
    try {
      const response = await fetch(this.probeUrl, { cache: "no-store", signal: AbortSignal.timeout(this.timing.probeTimeoutMs) });
      // 본문을 끝까지 읽어 둔다. 읽지 않은 응답은 시간 제한이 지날 때 중단된 요청으로 남는다.
      await response.text();
      if (response.ok) this.markAwake();
      return response.ok;
    } catch {
      // 잠든 서버의 502에는 CORS 헤더가 없어 Keycloak 확인은 네트워크 오류로 끝난다. 둘 다 아직 깨지 않은 것으로 본다.
      return false;
    }
  }
}
