import { SleepingServer } from "../lib/serverWakeup.ts";

/** 백엔드. 운영 배포에서는 방문이 없으면 잠들므로, 공개 설정 조회로 깨어났는지 확인한다. */
export const apiServer = new SleepingServer("/payment-config");

interface ApiErrorResponse {
  code?: string;
  message?: string;
}

export class ApiRequestError extends Error {
  readonly status: number;
  readonly code?: string;

  constructor(status: number, error: ApiErrorResponse) {
    super(error.message || "요청을 처리하지 못했습니다. 저장된 결과를 조회해주세요.");
    this.name = "ApiRequestError";
    this.status = status;
    this.code = error.code;
  }
}

/** 서버에 닿지 못했을 때의 오류. 요청이 처리됐는지 알 수 없으므로 다시 확인하도록 안내한다. */
const UNREACHABLE: ApiErrorResponse = {
  code: "SERVER_UNREACHABLE",
  message: "서버에 연결하지 못했습니다. 잠시 후 다시 확인해주세요."
};

// 개발 서버 프록시는 백엔드에 연결하지 못하면 JSON 없이 이 상태로 답한다.
const GATEWAY_STATUSES = [502, 503, 504];

/**
 * API를 호출하고 JSON 응답을 돌려준다. 실패하면 서버가 보낸 {code, message}로 ApiRequestError를 던진다.
 * 서버에 연결하지 못하면 브라우저 원문 오류("Failed to fetch") 대신 status 0의 한국어 안내를 던진다.
 * 운영 배포에서 백엔드가 잠들었을 수 있으면 먼저 깨운 뒤 한 번만 요청한다.
 */
export async function request<T>(
  path: string,
  options?: RequestInit,
  acceptedErrorStatuses: readonly number[] = []
): Promise<T> {
  await apiServer.whenAwake();
  let response: Response;
  try {
    response = await fetch(path, options);
  } catch {
    throw new ApiRequestError(0, UNREACHABLE);
  }
  if (response.status === 204) {
    apiServer.markAwake();
    return undefined as T;
  }
  const data = (await response.json().catch(() => null)) as T | ApiErrorResponse | null;

  if (data === null && GATEWAY_STATUSES.includes(response.status)) {
    throw new ApiRequestError(response.status, UNREACHABLE);
  }
  apiServer.markAwake();
  const body = data ?? { message: "서버 응답을 읽지 못했습니다. 잠시 후 다시 시도해주세요." };
  if (!response.ok && !acceptedErrorStatuses.includes(response.status)) {
    throw new ApiRequestError(response.status, body as ApiErrorResponse);
  }

  return body as T;
}

export function jsonBody(method: string, body: unknown): RequestInit {
  return { method, headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) };
}
