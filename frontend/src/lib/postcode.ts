import type { PostcodeResult } from "./addresses";

// 카카오(다음) 우편번호 서비스. 키 없이 쓰는 무료 서비스이며, 처음 찾을 때만 스크립트를 불러온다.
const SCRIPT_URL = "https://t1.daumcdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js";

interface PostcodeOptions {
  oncomplete: (result: PostcodeResult) => void;
  width: string;
  height: string;
}

declare global {
  interface Window {
    daum?: { Postcode: new (options: PostcodeOptions) => { embed: (element: HTMLElement) => void } };
  }
}

let loading: Promise<void> | null = null;

function loadScript(): Promise<void> {
  if (window.daum?.Postcode) return Promise.resolve();
  loading ??= new Promise<void>((resolve, reject) => {
    const script = document.createElement("script");
    script.src = SCRIPT_URL;
    script.async = true;
    script.onload = () => resolve();
    script.onerror = () => {
      // 다음에 다시 누르면 새로 불러온다.
      loading = null;
      script.remove();
      reject(new Error("우편번호 찾기를 불러오지 못했습니다."));
    };
    document.head.append(script);
  });
  return loading;
}

/** 팝업 차단을 피하려고 새 창 대신 화면 안의 영역에 우편번호 찾기를 띄운다. */
export async function embedPostcodeSearch(container: HTMLElement, onSelect: (result: PostcodeResult) => void): Promise<void> {
  await loadScript();
  const Postcode = window.daum?.Postcode;
  if (!Postcode) throw new Error("우편번호 찾기를 불러오지 못했습니다.");
  new Postcode({ oncomplete: onSelect, width: "100%", height: "100%" }).embed(container);
}
