# 기본 결제 흐름과 MVP 범위

이 문서는 현재 구현을 설명합니다. 예전의 작업 ID, 처리 기한, 복구 배치 설계는 기본 결제 흐름에 포함하지 않습니다.

## 1. 누가 무엇을 하나요?

| 참여자 | 역할 |
| --- | --- |
| 사용자 | 상품을 주문하고 결제수단을 인증합니다. |
| React | 주문 화면과 결과 화면을 표시하고 서버 API를 호출하며, 주문 정보를 토스 JS SDK에 전달합니다. |
| 토스 JS SDK | React가 넘긴 주문 정보로 토스 결제창 요청을 시작합니다. |
| Spring Boot | 주문과 금액을 검증하고 토스에 승인을 요청한 뒤 결과를 저장합니다. |
| 토스페이먼츠 | 결제창에서 결제수단 인증을 처리합니다. 인증 성공 시 브라우저를 `successUrl`로 이동시키고, 실패 시 `failUrl`로 이동시킵니다.<br>인증에 성공하면 Spring Boot가 토스 승인 API를 호출하고, 토스페이먼츠는 승인 결과를 Spring Boot에 반환합니다. |

토스 JS SDK는 독립된 외부 서버가 아니라 React와 함께 사용자 브라우저에서 실행되는 라이브러리입니다. 결제창 요청이라는 별도 책임이 있어 학습 흐름에서는 참여자로 구분합니다. 카드 번호·비밀번호를 우리 서버가 받는 구조가 아니며, 인증은 토스 결제창과 카드·간편결제 서비스에서 진행합니다.

## 2. 주문부터 결제 완료까지

1. **주문 생성**: 서버가 티셔츠 1장, 10,000원 주문과 고유한 `orderId`를 DB에 저장합니다.
2. **결제 요청**: React가 결제창형 클라이언트 키로 SDK의 `widgets()`를 초기화하고, `setAmount()`로 금액을 설정한 뒤 `renderPaymentWindow()`로 결제창을 엽니다. 구매자가 결제수단을 선택하면 `paymentRequest` 이벤트에서 `widgets.requestPayment()`를 호출합니다.
3. **결제수단 인증**: 사용자가 결제창에서 카드·간편결제를 인증합니다. 성공하면 토스가 브라우저를 `successUrl`로 이동시키면서 최종 승인에 필요한 `paymentKey`, `orderId`, `amount`를 전달합니다. 실패하면 `failUrl`로 이동시키면서 `code`, `message`를 전달합니다.
4. **서버 검증**: React가 세 값을 `POST /payments/confirm`에 보냅니다. 서버가 저장된 주문 금액과 비교하고 결제 키를 먼저 보관합니다.
5. **최종 승인**: 서버가 시크릿 키로 토스 `POST /v1/payments/confirm`을 호출합니다.
6. **결과 저장·조회**: 토스 응답의 주문번호·키·금액·통화와 `DONE`·승인 시각을 확인하고 `SUCCEEDED`로 저장합니다.

**인증 성공은 아직 결제 완료가 아닙니다.** 승인 단계가 있어야 끝납니다. 인증 후 결과 페이지가 바로 승인을 요청하며, 공식 연동 가이드는 결제 요청 완료 후 10분 이내 승인을 안내합니다. [토스 공식 연동 절차](https://docs.tosspayments.com/guides/v2/payment-widget/integration-window)

## 3. 세 가지 식별자를 구분하세요

| 값 | 역할 | 이 프로젝트 |
| --- | --- | --- |
| orderId | 우리 주문 식별 | 서버에서 UUID 생성 |
| paymentKey | 토스 결제 식별 | 인증 성공 후 `successUrl`로 받고 서버에 저장 |
| customerKey | 구매자 식별 | 비회원이므로 ANONYMOUS 사용 |

클라이언트 키는 SDK 초기화에 사용합니다. 시크릿 키는 서버의 HTTP Basic 인증에 사용하며, `시크릿 키 + :`를 Base64로 인코딩한 형식입니다. Java의 `setBasicAuth(secretKey, "")`가 이를 처리합니다. [인증과 멱등키](https://docs.tosspayments.com/reference/using-api/authorization)

## 4. 어떤 결제창을 사용하나요?

현재 코드는 신규 권장 제품인 **결제창형 결제(기존 결제위젯)**를 SDK v2의 `widgets()`와 `renderPaymentWindow()`로 엽니다. 사용자가 결제수단을 고른 뒤 `paymentRequest` 이벤트에서 인증을 요청하며, 결제창이 열린 동안 다른 작업과 중복 요청을 막습니다. 창을 닫으면 같은 주문으로 다시 열 수 있고, 오류나 화면 이탈 때도 창을 정리합니다. [SDK 명세](https://docs.tosspayments.com/sdk/v2/js/payment-window)

클라이언트와 서버는 함께 발급된 **주문서형·결제창형 테스트 키(`test_gck_`, `test_gsk_`)**를 사용합니다. `TOSS_PAYMENT_METHOD_VARIANT_KEY`, `TOSS_AGREEMENT_VARIANT_KEY`로 결제수단·약관 UI를 지정할 수 있으며, 빈 값이면 SDK 기본 UI를 사용합니다. 상점 결제 어드민의 UI에는 카드·국내 간편결제만 노출하세요. 다른 수단을 선택하면 이 MVP는 인증 전에 중단합니다.

간편결제의 계좌·포인트 결제에는 `card` 객체가 없을 수 있으므로 이를 필수 성공 조건으로 삼지 않습니다. 서버는 응답의 `method`가 카드·간편결제인지도 확인합니다. [간편결제 응답](https://docs.tosspayments.com/guides/v2/easypay-response)

서버 승인·조회 API는 신규 제품에서도 `POST /v1/payments/confirm`, `GET /v1/payments/{paymentKey}`입니다. SDK v2, 결제창 제품, 서버 API 버전은 별개입니다. 공식 문서상 결제창형 키의 API 응답 버전은 `2022-11-16`으로 고정되며 URL을 `/v2`로 바꾸지 않습니다. [API 키와 버전](https://docs.tosspayments.com/reference/using-api/api-keys), [코어 API](https://docs.tosspayments.com/reference)

## 5. 실패와 중복 요청

인증 실패는 `failUrl`의 `code`, `message`를 화면에 표시합니다. 사용자가 창을 닫으면 토스가 `orderId`를 생략할 수 있어, 우리가 지정한 `requestedOrderId`로 주문을 찾습니다. 이 URL의 값만으로 DB를 실패 처리하지 않습니다.

승인 결과는 다음처럼 구분합니다.

| 상황 | 처리 |
| --- | --- |
| 일치하는 DONE 응답 | SUCCEEDED |
| 명시적인 카드 거절, 조회에서 ABORTED·EXPIRED | FAILED |
| 타임아웃, 해석 불가, 이미 처리된 결제 오류 등 | UNKNOWN으로 두고 조회 |
| 같은 주문·결제 키로 승인 재요청 | 저장된 결과 반환 |
| 이미 연결된 주문에 다른 결제 키 요청 | 409 오류 |

`UNKNOWN`은 토스의 상태명이 아니라 **우리 서버가 결과를 아직 모른다**는 뜻입니다. 토스의 원본 오류 코드는 화면에 표시합니다. `NOT_FOUND_PAYMENT_SESSION`도 만료 외에 키 설정·요청 불일치 등을 확인해야 하므로 코드만으로 새 결제를 허용하지 않습니다. [토스 오류 코드](https://docs.tosspayments.com/reference/error-codes)

같은 승인 요청에는 주문 UUID를 `Idempotency-Key`로 보냅니다. 토스의 멱등키 유효기간은 15일이며, 이 프로젝트가 15일 동안 자동 재시도한다는 뜻은 아닙니다. 새 멱등키로 불명확한 결제를 재승인하는 기능도 없습니다.

## 6. 결과가 불명확하면

먼저 `GET /orders/{orderId}`로 DB의 결과를 조회합니다. PROCESSING 또는 UNKNOWN이면 `POST /payments/{orderId}/reconcile`로 서버가 토스의 `GET /v1/payments/{paymentKey}`를 호출합니다.

이 보조 기능은 조회만 합니다. 조회해도 인증 단계에 머물러 있거나 결과가 불명확하면 UNKNOWN을 유지합니다. 서버가 승인 요청 전에 중단된 경우까지 자동 복구하는 기능은 없습니다.

## 7. 학습 범위

주문당 결제 한 건, 비회원, 원화 10,000원, 테스트 키만 사용합니다. 주문 생성·인증·승인·결과 조회를 먼저 익히세요.

DB 저장과 토스 HTTP 호출은 하나의 원자적 트랜잭션이 아닙니다. 그래서 결제 키를 먼저 저장하고 결과를 나중에 저장합니다. DB의 유일성 제약과 JPA `@Version`으로 충돌을 검사하며, 자세한 코드는 [코드 읽는 순서](code-reading-guide.md)를 참고하세요.

로그인·접근 권한, 취소·환불, 가상계좌, 빌링, 웹훅, 자동 복구, 배송·정산은 이후 범위입니다. **결제 승인 완료가 배송 완료나 판매자 계좌의 정산 입금을 뜻하지는 않습니다.**

자동 테스트는 모의 토스 응답을 사용합니다. 최종 수동 확인은 [README 실행 방법](../README.md#실행하기)에 따라 본인의 테스트 상점으로 결제하고, 화면·DB·개발자센터의 결과를 비교합니다.
