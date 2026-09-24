import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const outputDir = dirname(fileURLToPath(import.meta.url));
const C = {
  page: "#f6f5ef", paper: "#fffef9", text: "#253831", muted: "#717a72",
  forest: "#304d3b", hover: "#42684f", border: "#dedfd5", sand: "#eae5da",
  sage: "#edf2e8", white: "#ffffff"
};

const products = [
  ["에브리데이 크루넥", "REGULAR / IVORY", "10,000원", "#fffdf7", "#eae5da"],
  ["헤비웨이트 코튼", "HEAVY / CHARCOAL", "18,000원", "#414945", "#e2e2db"],
  ["릴랙스 핏 티", "RELAXED / SAGE", "16,000원", "#8fa489", "#e8e8dd"],
  ["오버사이즈 티", "OVERSIZED / BLUE", "17,000원", "#aebfd0", "#e4e9e8"],
  ["컴팩트 코튼", "COMPACT / SAND", "14,000원", "#ceb99b", "#e9e1d6"],
  ["피그먼트 워시", "WASHED / BRICK", "22,000원", "#ae7460", "#e9dfd6"],
  ["소프트 터치 티", "SOFT / CREAM", "15,000원", "#f0e8d9", "#e8e5da"],
  ["올리브 데일리 티", "DAILY / OLIVE", "19,000원", "#6e8064", "#e3e5d9"],
  ["드라이 퍼포먼스", "ACTIVE / SLATE", "20,000원", "#82969c", "#e2e8e7"],
  ["클래식 블랙 티", "REGULAR / BLACK", "12,000원", "#303534", "#e1e1da"]
];

const esc = value => String(value).replace(/[&<>"]/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[c]);
const rect = (x, y, w, h, fill, r = 0, stroke = "none") =>
  `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${r}" fill="${fill}" stroke="${stroke}"/>`;
const text = (x, y, value, size, color = C.text, weight = 400, opts = "") =>
  `<text x="${x}" y="${y}" fill="${color}" font-family="Noto Sans KR,Malgun Gothic,Segoe UI,sans-serif" font-size="${size}" font-weight="${weight}" ${opts}>${esc(value)}</text>`;
const line = (x1, y1, x2, y2, color = C.border) =>
  `<line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" stroke="${color}" stroke-width="1"/>`;
const pill = (x, y, w, label, selected = false) =>
  rect(x, y, w, 38, selected ? C.forest : C.paper, 19, selected ? C.forest : C.border) +
  text(x + w / 2, y + 25, label, 13, selected ? C.white : C.text, 600, 'text-anchor="middle"');

function tee(x, y, width, color) {
  const scale = width / 360;
  const seam = color.toLowerCase() === "#fffdf7" || color.toLowerCase() === "#f0e8d9" ? "#b8b5ab" : "#59615b";
  return `<g transform="translate(${x} ${y}) scale(${scale})">
    <ellipse cx="180" cy="276" rx="106" ry="10" fill="#cfc8bc" opacity=".65"/>
    <path d="M127 42 81 63 30 134 79 167 107 129 102 260Q180 277 258 260L253 129 281 167 330 134 279 63 233 42Q180 66 127 42Z" fill="${color}" stroke="${seam}" stroke-width="3"/>
    <path d="M146 48Q180 94 214 48M127 42 146 48M214 48 233 42" fill="none" stroke="${seam}" stroke-width="3" opacity=".7"/>
    <path d="M104 248Q180 262 256 248M45 135 78 156M282 156 315 135" fill="none" stroke="${seam}" stroke-width="2" opacity=".35"/>
  </g>`;
}

function card(x, y, w, product, mobile = false) {
  const [name, meta, price, color, stage] = product;
  const imageH = mobile ? 176 : 210;
  const shirtW = mobile ? 136 : 166;
  const artX = x + (w - shirtW) / 2;
  const artY = y + (imageH - shirtW * 300 / 360) / 2 - 4;
  return `<g>
    ${rect(x, y, w, imageH, stage, 12)}
    ${tee(artX, artY, shirtW, color)}
    ${text(x, y + imageH + 25, meta, mobile ? 9 : 10, C.muted, 600, 'letter-spacing="1.1"')}
    ${text(x, y + imageH + 49, name, mobile ? 14 : 16, C.text, 700)}
    ${text(x, y + imageH + 75, price, mobile ? 14 : 16, C.text, 600)}
  </g>`;
}

function svgStart(w, h, title) {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}" role="img" aria-label="${esc(title)}">
  <title>${esc(title)}</title>${rect(0, 0, w, h, C.page)}`;
}
const svgEnd = "</svg>";

function topBar(width, mobile = false) {
  const h = mobile ? 32 : 36;
  return rect(0, 0, width, h, C.forest) +
    text(width / 2, mobile ? 21 : 24, "TEST STORE  ·  테스트 결제는 실제로 청구되지 않습니다", mobile ? 10 : 12, C.white, 500, 'text-anchor="middle"');
}

function desktopHeader() {
  return `${rect(0, 36, 1440, 92, C.page)}
  ${text(160, 88, "한 장의 티셔츠", 24, C.text, 700)}
  ${text(161, 109, "PAYMENT LAB  /  TEE STORE", 9, C.muted, 600, 'letter-spacing="1.3"')}
  ${text(537, 86, "전체 상품", 15, C.text, 600)}
  ${text(650, 86, "베이직", 15, C.muted, 500)}
  ${text(743, 86, "릴랙스", 15, C.muted, 500)}
  ${text(844, 86, "브랜드 이야기", 15, C.muted, 500)}
  ${rect(1060, 57, 135, 42, C.paper, 8, C.border)}
  ${text(1080, 84, "상품 검색", 13, C.muted)}
  ${rect(1210, 57, 105, 42, C.sage, 8)}
  ${text(1262, 84, "장바구니  0", 13, C.text, 600, 'text-anchor="middle"')}
  ${line(0, 128, 1440, 128)}`;
}

function mobileHeader() {
  return `${rect(0, 32, 390, 76, C.page)}
  ${text(20, 73, "한 장의 티셔츠", 18, C.text, 700)}
  ${text(21, 92, "PAYMENT LAB", 9, C.muted, 600, 'letter-spacing="1.1"')}
  ${rect(281, 52, 89, 36, C.sage, 8)}
  ${text(325, 76, "장바구니 0", 11, C.text, 600, 'text-anchor="middle"')}
  ${line(0, 108, 390, 108)}`;
}

function desktopCatalog() {
  let out = svgStart(1440, 1810, "Payment Lab 티셔츠 10종 데스크톱 상품 목록");
  out += topBar(1440) + desktopHeader();
  out += rect(0, 129, 1440, 445, C.sage);
  out += text(160, 209, "THE EVERYDAY TEE EDIT", 13, C.forest, 700, 'letter-spacing="2.4"');
  out += text(160, 294, "매일 입고 싶은", 52, C.text, 700);
  out += text(160, 368, "티셔츠의 기준.", 52, C.text, 700);
  out += text(160, 417, "하루의 시작부터 끝까지 편안하게.", 17, C.muted);
  out += text(160, 446, "핏과 색을 고르는 즐거움을 10가지 티셔츠에 담았습니다.", 17, C.muted);
  out += rect(160, 484, 198, 50, C.forest, 8);
  out += text(259, 517, "10가지 상품 보기  ↗", 15, C.white, 700, 'text-anchor="middle"');
  out += rect(850, 171, 430, 355, C.sand, 22);
  out += tee(919, 195, 300, "#fffdf7");
  out += text(1065, 503, "NEW SEASON  /  10 TEES", 12, C.muted, 600, 'text-anchor="middle" letter-spacing="1.2"');
  out += text(180, 641, "THE TEE EDIT", 12, C.forest, 700, 'letter-spacing="2.1"');
  out += text(180, 693, "취향대로 고르는 10가지 티셔츠", 35, C.text, 700);
  out += text(180, 730, "일상의 모든 순간에 자연스럽게 어울리는 한 장을 찾아보세요.", 15, C.muted);
  out += pill(180, 765, 94, "전체 10", true) + pill(286, 765, 89, "베이직") + pill(387, 765, 89, "릴랙스") +
    pill(488, 765, 115, "헤비웨이트") + pill(615, 765, 89, "기능성");
  out += text(1260, 790, "추천순  ▾", 13, C.text, 600, 'text-anchor="end"');
  out += line(180, 818, 1260, 818);
  for (let i = 0; i < products.length; i++) {
    const col = i % 5, row = Math.floor(i / 5);
    out += card(180 + col * 220, 850 + row * 325, 200, products[i]);
  }
  out += rect(180, 1540, 1080, 165, C.sage, 16);
  out += text(228, 1598, "EVERYDAY, WELL MADE", 11, C.forest, 700, 'letter-spacing="1.8"');
  out += text(228, 1645, "좋은 하루는 편한 옷에서 시작됩니다.", 27, C.text, 700);
  out += text(228, 1674, "좋은 기본을 오래 입는 즐거움.", 14, C.muted);
  out += rect(1040, 1594, 167, 48, C.forest, 8);
  out += text(1123, 1625, "브랜드 이야기  ↗", 13, C.white, 700, 'text-anchor="middle"');
  out += line(0, 1741, 1440, 1741);
  out += text(160, 1776, "PAYMENT LAB", 12, C.text, 700, 'letter-spacing="1.2"');
  out += text(1280, 1776, "티셔츠 10종 쇼핑몰 시안  ·  상품·가격은 예시", 11, C.muted, 400, 'text-anchor="end"');
  return out + svgEnd;
}

function mobileCatalog() {
  let out = svgStart(390, 2390, "Payment Lab 티셔츠 10종 모바일 상품 목록");
  out += topBar(390, true) + mobileHeader();
  out += rect(0, 109, 390, 354, C.sage);
  out += text(20, 156, "THE EVERYDAY TEE EDIT", 10, C.forest, 700, 'letter-spacing="1.7"');
  out += text(20, 204, "매일 입고 싶은", 32, C.text, 700);
  out += text(20, 250, "티셔츠의 기준.", 32, C.text, 700);
  out += text(20, 286, "입을수록 손이 가는 10가지 기본.", 13, C.muted);
  out += rect(20, 314, 147, 42, C.forest, 8);
  out += text(93, 341, "상품 둘러보기  ↗", 12, C.white, 700, 'text-anchor="middle"');
  out += rect(230, 293, 133, 145, C.sand, 14);
  out += tee(243, 302, 109, "#fffdf7");
  out += text(20, 508, "THE TEE EDIT", 10, C.forest, 700, 'letter-spacing="1.7"');
  out += text(20, 543, "10가지 티셔츠", 26, C.text, 700);
  out += text(20, 571, "나에게 맞는 한 장을 골라보세요.", 12, C.muted);
  out += pill(20, 590, 76, "전체 10", true) + pill(104, 590, 72, "베이직") + pill(184, 590, 72, "릴랙스");
  out += text(370, 661, "추천순  ▾", 12, C.text, 600, 'text-anchor="end"');
  out += line(20, 680, 370, 680);
  for (let i = 0; i < products.length; i++) {
    const col = i % 2, row = Math.floor(i / 2);
    out += card(20 + col * 180, 702 + row * 277, 170, products[i], true);
  }
  out += rect(20, 2110, 350, 170, C.sage, 14);
  out += text(40, 2150, "EVERYDAY, WELL MADE", 10, C.forest, 700, 'letter-spacing="1.2"');
  out += text(40, 2190, "좋은 기본을 오래 입는 즐거움.", 18, C.text, 700);
  out += text(40, 2221, "편안한 한 장으로 시작해보세요.", 12, C.muted);
  out += line(0, 2320, 390, 2320);
  out += text(20, 2354, "PAYMENT LAB", 11, C.text, 700, 'letter-spacing="1.1"');
  out += text(370, 2354, "상품·가격은 예시", 10, C.muted, 400, 'text-anchor="end"');
  return out + svgEnd;
}

function productDetail() {
  let out = svgStart(1440, 1360, "Payment Lab 티셔츠 상품 상세 데스크톱 시안");
  out += topBar(1440) + desktopHeader();
  out += text(160, 178, "전체 상품   /   베이직   /   에브리데이 크루넥", 13, C.muted);
  out += rect(160, 211, 560, 614, C.sand, 18) + tee(217, 297, 448, "#fffdf7");
  out += text(160, 861, "01 / 03", 12, C.muted, 600) + text(720, 861, "○   ●   ○", 12, C.muted, 600, 'text-anchor="end"');
  out += text(788, 252, "REGULAR / IVORY", 12, C.forest, 700, 'letter-spacing="1.8"');
  out += text(788, 316, "에브리데이 크루넥", 38, C.text, 700);
  out += text(788, 360, "입는 순간 편안한 매일의 기본 티셔츠", 16, C.muted);
  out += text(788, 431, "10,000원", 30, C.text, 700) + line(788, 461, 1280, 461);
  out += text(788, 508, "색상", 14, C.text, 700) + text(843, 508, "아이보리", 13, C.muted);
  for (const [i, color] of ["#fffdf7", "#414945", "#8fa489", "#aebfd0"].entries()) {
    out += `<circle cx="${808 + i * 44}" cy="550" r="16" fill="${color}" stroke="${i===0?C.forest:C.border}" stroke-width="${i===0?2:1}"/>`;
  }
  out += text(788, 621, "사이즈", 14, C.text, 700);
  for (const [i, size] of ["S", "M", "L", "XL"].entries()) {
    out += rect(788 + i * 67, 642, 56, 46, i === 1 ? C.forest : C.paper, 8, i === 1 ? C.forest : C.border);
    out += text(816 + i * 67, 672, size, 14, i === 1 ? C.white : C.text, 600, 'text-anchor="middle"');
  }
  out += rect(788, 728, 103, 50, C.paper, 8, C.border) + text(840, 760, "−    1    +", 16, C.text, 600, 'text-anchor="middle"');
  out += rect(905, 728, 375, 50, C.forest, 8) + text(1092, 761, "장바구니 담기", 15, C.white, 700, 'text-anchor="middle"');
  out += text(788, 823, "면 100%  ·  부드러운 촉감  ·  여유로운 일상 핏", 14, C.muted);
  out += text(788, 854, "시안용 상품 정보입니다. 실제 재고·옵션은 구현 시 연결합니다.", 12, C.muted);
  out += line(160, 936, 1280, 936);
  out += text(160, 997, "함께 보면 좋은 티셔츠", 28, C.text, 700);
  for (let i = 1; i <= 4; i++) out += card(160 + (i - 1) * 280, 1020, 220, products[i]);
  return out + svgEnd;
}

function cartScreen() {
  let out = svgStart(1440, 1020, "Payment Lab 장바구니와 주문 요약 데스크톱 시안");
  out += topBar(1440) + desktopHeader();
  out += text(160, 205, "장바구니", 40, C.text, 700);
  out += text(160, 240, "담은 상품을 확인하고 테스트 결제를 진행해보세요.", 15, C.muted);
  out += text(160, 316, "담은 상품  1", 21, C.text, 700) + line(160, 339, 870, 339);
  out += rect(160, 366, 165, 175, C.sand, 12) + tee(180, 388, 126, "#fffdf7");
  out += text(353, 405, "에브리데이 크루넥", 21, C.text, 700);
  out += text(353, 437, "아이보리 / M", 14, C.muted);
  out += text(353, 492, "10,000원", 18, C.text, 700);
  out += rect(708, 482, 118, 42, C.paper, 8, C.border) + text(766, 509, "−    1    +", 14, C.text, 600, 'text-anchor="middle"');
  out += line(160, 576, 870, 576);
  out += rect(927, 300, 353, 400, C.paper, 14, C.border);
  out += text(959, 351, "주문 요약", 23, C.text, 700);
  out += text(959, 407, "상품 금액", 14, C.muted) + text(1248, 407, "10,000원", 14, C.text, 600, 'text-anchor="end"');
  out += text(959, 447, "배송비", 14, C.muted) + text(1248, 447, "0원", 14, C.text, 600, 'text-anchor="end"');
  out += line(959, 472, 1248, 472);
  out += text(959, 516, "결제 예정 금액", 15, C.text, 700) + text(1248, 516, "10,000원", 22, C.text, 700, 'text-anchor="end"');
  out += rect(959, 552, 289, 48, C.forest, 8) + text(1103, 583, "테스트 결제 진행", 15, C.white, 700, 'text-anchor="middle"');
  out += text(959, 629, "토스페이먼츠 테스트 환경으로 연결됩니다.", 12, C.muted);
  out += text(959, 652, "인증 후 서버 승인이 완료되어야 결제 완료입니다.", 12, C.muted);
  out += rect(160, 644, 710, 104, C.sage, 12);
  out += text(184, 683, "테스트 스토어 안내", 15, C.text, 700);
  out += text(184, 711, "상품·가격·재고는 시안용 예시이며 실제 금액은 청구되지 않습니다.", 13, C.muted);
  out += line(0, 944, 1440, 944);
  out += text(160, 982, "PAYMENT LAB", 12, C.text, 700, 'letter-spacing="1.2"');
  out += text(1280, 982, "티셔츠 10종 쇼핑몰 시안", 11, C.muted, 400, 'text-anchor="end"');
  return out + svgEnd;
}

mkdirSync(outputDir, { recursive: true });
for (const [name, build] of [
  ["catalog-desktop.svg", desktopCatalog],
  ["catalog-mobile.svg", mobileCatalog],
  ["product-detail.svg", productDetail],
  ["cart.svg", cartScreen]
]) {
  writeFileSync(join(outputDir, name), build(), "utf8");
}
