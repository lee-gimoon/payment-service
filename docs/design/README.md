# 티셔츠 10종 쇼핑몰 시안

**새로운 캐릭터 방향:** [MODO CLUB 아바타 쇼핑몰 시안](avatar/README.md). 랜딩에는 캐릭터 아바타를, 목록에는 디자인이 다른 티셔츠 상품 10개를 보여준다. 이 폴더 바로 아래의 SVG는 이전 크림색·녹색 시안이며 비교용으로 남겨두었다.

현재 React 화면은 단일 상품 결제 학습용 스토어다. 이 폴더의 SVG는 다음 단계에서 상품 목록·상세·장바구니를 구현하기 위한 **시안**이며, 화면 기능이나 서버 상품 데이터가 추가된 것은 아니다.

| 화면 | 파일 |
| --- | --- |
| PC 상품 목록 · 티셔츠 10종 | [catalog-desktop.svg](catalog-desktop.svg) |
| 모바일 상품 목록 · 티셔츠 10종 | [catalog-mobile.svg](catalog-mobile.svg) |
| PC 상품 상세 | [product-detail.svg](product-detail.svg) |
| PC 장바구니·주문 요약 | [cart.svg](cart.svg) |

상품명·가격·색상·재고·장바구니 수량은 시안을 위한 예시다. 상품 이미지는 티셔츠 벡터 일러스트를 사용한다. 실제 구현에서는 상품 사진과 서버의 상품·가격·재고 데이터를 연결해야 한다. 테스트 결제의 인증과 최종 승인 상태는 기존 결제 규칙을 따른다.

기본 팔레트는 루트 [DESIGN.md](../../DESIGN.md)를 따르며, 시안 생성 코드는 [generate-mockups.mjs](generate-mockups.mjs)다. 파일을 수정한 뒤 `node docs/design/generate-mockups.mjs`로 SVG를 다시 만들 수 있다.

[이전 Figma 작업 파일](https://www.figma.com/design/x41TO6RtcDtY7VIESYZgOn)에는 크림색·녹색 방향의 색상·타이포그래피 기준, 상품 카드·버튼 컴포넌트, PC·모바일 상품 목록 화면이 있다. 새 아바타 디자인은 [별도 시안](avatar/README.md)으로 확인할 수 있다.
