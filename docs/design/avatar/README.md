# MODO CLUB 아바타 쇼핑몰 시안

랜딩 화면에는 캐릭터 아바타 한 명을 보여주고, 아래에는 디자인이 다른 티셔츠 상품 10개를 옷 이미지 중심으로 나열한 시안이다. 실제 React 화면에서는 상품을 고르면 그 옷이 아바타에 적용되고, 사이즈 선택·장바구니·서버 주문으로 이어진다.

| 화면 | 파일 |
| --- | --- |
| PC 상품 목록 | [catalog-desktop.svg](catalog-desktop.svg) |
| 모바일 상품 목록 | [catalog-mobile.svg](catalog-mobile.svg) |

시각 규칙은 [DESIGN.md](DESIGN.md)에, 생성 코드는 [generate.mjs](generate.mjs)에 있다. `node docs/design/avatar/generate.mjs`로 SVG를 다시 만들 수 있다.

React 화면은 이 방향으로 구현되었다. [기존 Figma 파일](https://www.figma.com/design/x41TO6RtcDtY7VIESYZgOn)은 이전 크림색·녹색 시안으로 남아 있다.
