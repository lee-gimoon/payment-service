import { useSyncExternalStore } from "react";
import { isWaking, subscribeWaking } from "../lib/serverWakeup";

/**
 * 운영 배포에서 잠든 백엔드·Keycloak을 깨우는 동안 공지 띠 아래에 보여준다. 로컬 개발에서는 나타나지 않는다.
 * 화면 읽기 프로그램이 안내를 읽도록 상태 영역은 항상 두고 내용만 바꾼다.
 */
export function ServerWakeupNotice() {
  const waking = useSyncExternalStore(subscribeWaking, isWaking);
  return <div className="wake-notice" role="status" hidden={!waking}>
    {waking && "서버를 깨우고 있습니다. 방문이 없을 때는 서버를 재워 두어 처음 연결에 30초쯤 걸릴 수 있습니다."}
  </div>;
}
