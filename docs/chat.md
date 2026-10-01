# 1:1 상담

로그인한 고객과 쇼핑몰 관리자가 메시지를 주고받는 상담 기능의 규칙과 구현을 설명한다. 요청·응답 계약은 [API 문서](api.md#상담), 관리자 역할은 [로그인과 회원](authentication.md#로컬-계정)을 참고한다.

고객은 모든 쇼핑몰 화면 오른쪽 아래 **문의하기** 창에서, 관리자는 **상담 관리**(`/admin/chat`) 화면에서 대화한다. 메시지는 HTTP API로 보내고, 저장된 새 메시지는 WebSocket으로 상대에게 바로 전달된다.

```text
보내기:  브라우저 ── POST /chat/messages ──▶ Spring ──▶ DB 저장·커밋
알림:                                         Spring ── WebSocket(STOMP) ──▶ 고객 본인·관리자 화면
다시 연결: 브라우저 ── GET /chat/messages?after={조회로 확인한 마지막 번호} ──▶ 놓친 메시지
```

## 상담 방식

고객 한 명당 대화창 하나를 둔다(카카오톡 채널 방식). 고객이 언제 문의하든 같은 대화에 이어서 쌓이고, 상담 종료나 문의 건별 구분은 없다.

| 참여자 | 할 수 있는 일 |
| --- | --- |
| 고객 (로그인한 회원) | 자기 대화 조회, 메시지 보내기, 관리자 답변 읽음 표시 |
| 쇼핑몰 관리자 (`shop-admin` 역할) | 전체 상담 목록·대화 조회, 답장, 고객 메시지 읽음 표시. 먼저 말을 걸 수는 없다. 고객 창구(`/chat/**`, 문의하기)는 쓰지 않는다 |

## 핵심 규칙

| 규칙 | 지키는 장치 |
| --- | --- |
| 고객당 상담방은 하나 | `chat_rooms.customer_id` 유니크 제약, 방 생성은 `INSERT ... ON CONFLICT DO NOTHING` |
| 고객은 자기 대화만 본다 | 고객 API는 방 ID를 받지 않고 토큰의 `sub`로 방을 찾음 |
| 관리자 API는 관리자만 | `/admin/**` 경로에 `shop-admin` 역할 요구([Spring 접근 규칙](authentication.md#spring-api-서버의-접근-규칙)) |
| 같은 메시지를 다시 보내도 한 번만 저장 | 브라우저가 만든 `clientMessageId`와 `(room_id, client_message_id)` 유니크 제약. 재전송이면 저장된 메시지를 `200`으로 반환 |
| 같은 방의 메시지 번호는 저장 순서와 같다 | 메시지를 저장하기 전에 방 행을 잠금 |
| 메시지는 바뀌지 않는다 | 수정·삭제 API 없음. 상담 기록으로 보존 |
| 관리자 개인은 고객에게 드러나지 않는다 | 응답에 보낸 사람 ID를 넣지 않음. 누가 답했는지는 `sender_id`로 DB에만 남김 |
| 커밋된 메시지만 알린다 | 저장 트랜잭션이 커밋된 뒤 이벤트 리스너(`@TransactionalEventListener`)가 WebSocket으로 전송 |
| 관리자는 고객으로 문의하지 않는다 | `/chat/**`는 `shop-admin`이면 `403 FORBIDDEN`. 화면도 관리자에게는 문의하기 대신 상담 관리 바로가기를 보여줌. 관리자 계정의 상담방이 고객 목록에 섞이지 않게 한다 |
| 알림은 본인과 관리자에게만 | 연결할 때 access token을 검증하고, 구독할 수 있는 주소를 제한([실시간 전달](#실시간-전달)) |
| 알림이 빠져도 메시지는 잃지 않는다 | DB가 기준. 다시 연결하면 대화 조회로 확인한 마지막 번호 이후를 채우고, 화면은 번호로 중복을 없앰 |

## 데이터 모델

| 테이블 | 한 행 | 주요 컬럼 |
| --- | --- | --- |
| `chat_rooms` | 고객 한 명의 대화창 | `customer_id`, 관리자 목록용 `customer_name`·`customer_email`, 마지막 메시지 요약(`last_message_id`·`last_message_at`·`last_sender_type`), 읽음 위치(`customer_read_message_id`·`admin_read_message_id`) |
| `chat_messages` | 말풍선 하나 | 증가 번호 `id`, `room_id`, `sender_type`(`CUSTOMER`·`ADMIN`), `sender_id`, `client_message_id`, `content`(1~1000자), `created_at` |

스키마는 [V6 마이그레이션](../src/main/resources/db/migration/V6__add_chat.sql)에 있다.

### 예시

고객이 묻고 관리자가 답한 뒤, 고객이 다시 물은 상태다.

| `chat_messages.id` | `sender_type` | `content` |
| --- | --- | --- |
| 1 | `CUSTOMER` | M이랑 L 중 뭐가 커요? |
| 2 | `ADMIN` | L이 가슴단면 3cm 더 커요. |
| 3 | `CUSTOMER` | 결제했는데 대기로 나와요 |

| `last_message_id` | `last_sender_type` | `customer_read_message_id` | `admin_read_message_id` |
| --- | --- | --- | --- |
| 3 | `CUSTOMER` | 3 | 2 |

- **답변 대기**: 마지막 메시지를 고객이 보냈다(`last_sender_type = CUSTOMER`).
- **관리자가 안 읽은 수 1**: 관리자는 2번까지 읽었고, 그 뒤 고객 메시지는 3번 하나다.
- **고객이 안 읽은 수 0**: 고객은 3번까지 읽었고, 그 뒤 관리자 메시지가 없다.

### 안 읽은 수 세기

메시지에는 읽음 상태가 없다. 조회할 때마다 방의 읽음 위치보다 번호가 큰 **상대방** 메시지를 센다. 메시지 번호는 모든 방이 함께 쓰는 증가 번호이고 내가 보낸 메시지는 세지 않으므로, 번호끼리 빼서 구하지 않는다.

```sql
-- 상담방별로 관리자가 아직 읽지 않은 고객 메시지 수를 세는 쿼리
SELECT m.room_id, COUNT(*)
FROM chat_messages m
JOIN chat_rooms r ON r.id = m.room_id
WHERE m.sender_type = 'CUSTOMER'           -- 고객이 보낸 것만
  AND m.id > r.admin_read_message_id       -- 관리자가 읽은 번호보다 뒤
GROUP BY m.room_id;
```

```sql
-- 고객이 아직 읽지 않은 관리자 답변 수를 세는 쿼리 (내 상담방 하나)
-- 1. 로그인한 고객의 상담방과 읽은 번호를 찾는다
SELECT id, customer_read_message_id
FROM chat_rooms
WHERE customer_id = '<토큰의 sub>';

-- 2. 그 방에서 읽은 번호 뒤의 관리자 답변을 센다
SELECT COUNT(*)
FROM chat_messages
WHERE room_id = '<1에서 찾은 방 id>'
  AND sender_type = 'ADMIN'                        -- 관리자가 보낸 것만
  AND id > <1에서 찾은 customer_read_message_id>;  -- 고객이 읽은 번호보다 뒤
```

- 첫 블록은 관리자 상담 목록의 **안 읽음 N**이다. 실제 쿼리([countUnreadByAdmin](../src/main/java/com/example/payment/chat/persistence/ChatMessageRepository.java))는 목록에 보일 방(최대 100개)으로 범위를 좁혀 한 번에 센다. 안 읽은 메시지가 없는 방은 결과에 없으므로 0으로 본다.
- 두 번째 블록은 고객 문의 창의 **새 답변 N**이다. 대화 조회(`GET /chat/messages`)에서 방을 먼저 찾고, 방이 없으면 0으로 끝낸다. 관리자가 대화 하나를 열 때도 조건만 바꿔(`CUSTOMER`, `admin_read_message_id`) 같은 방식으로 센다.
- 위 예시에 대입하면 첫 블록은 3번 하나라서 1, 두 번째 블록은 3번 뒤 관리자 메시지가 없어서 0이다.

## 메시지 저장 순서

메시지 하나를 한 트랜잭션에서 저장한다.

1. 고객 메시지는 방이 없으면 만든다(`ON CONFLICT DO NOTHING`). 관리자 답장은 지정한 방이 없으면 `404 CHAT_ROOM_NOT_FOUND`다.
2. 방 행을 잠근다.
3. 같은 방에 같은 `clientMessageId`가 있으면 재전송이다. 보낸 사람과 내용이 같으면 저장된 메시지를 `200`으로 돌려주고, 다르면 `409 CHAT_MESSAGE_CONFLICT`다.
4. 메시지를 저장한다. 번호는 DB가 매긴다.
5. 방의 마지막 메시지 요약과 보낸 사람의 읽음 위치를 갱신한다.

- **방 만들기에 `ON CONFLICT`를 쓰는 이유**: 첫 메시지를 동시에 두 번 보내면 두 요청이 모두 방을 만들려 한다. 일반 `INSERT`는 한쪽이 유니크 제약 위반으로 실패하고, PostgreSQL에서는 실패한 트랜잭션을 더 쓸 수 없다. `ON CONFLICT DO NOTHING`은 이미 있으면 조용히 넘어가므로 두 요청 모두 같은 방에 메시지를 저장한다.
- **방을 잠그는 이유**: 번호는 INSERT할 때 매겨지지만 다른 요청에 보이는 건 커밋한 뒤라서, 번호 순서와 커밋 순서가 다를 수 있다. 잠그지 않으면 다음처럼 4번을 놓친다.

  | 순서 | 요청 A | 요청 B | 그때 다른 화면이 조회하면 |
  | --- | --- | --- | --- |
  | 1 | INSERT → 4번 | | 1~3 |
  | 2 | (지연) | INSERT → 5번, 커밋 | 1~3, 5. 화면은 "5번까지 받음"으로 기록 |
  | 3 | 커밋 | | 다음 조회가 `after=5`라서 4번을 놓침 |

  방을 잠그면 B는 A가 커밋할 때까지 기다리므로, 같은 방의 메시지는 번호 순서대로 커밋된다. 5번이 보이면 4번도 이미 보인다. 마지막 메시지 요약도 늦게 끝난 요청이 덮어쓰지 않는다.
- **읽음 위치**: 앞으로만 움직이고, 아직 없는 번호로는 앞서가지 않는다. 메시지를 보낸 사람은 자기 메시지까지 읽은 것으로 본다. 읽음 표시도 방을 잠근 뒤 바꾼다.

## 목록과 대화 조회

- 대화는 최근 50개를 오래된 순으로 준다. `hasMore`가 `true`면 받은 첫 메시지 번호를 `before`로 보내 이전 대화를 더 불러온다.
- `after`를 보내면 그 번호 이후 메시지를 오래된 순으로 50개까지 준다. 다시 연결한 화면이 놓친 메시지를 채울 때 쓰며, `hasMore`가 `true`면 받은 마지막 번호로 이어서 조회한다.
- 관리자 상담 목록은 마지막 메시지 번호가 큰 순으로 최대 100개다. `waiting=true`면 답변 대기인 방을 먼저 찾은 뒤 최대 100개를 준다. 화면의 답변 대기 목록과 관리자 바로가기 배지는 이 조회를 사용하므로, 전체 목록의 최근 100개 밖에 있는 미답변 상담도 표시한다.
- 목록의 안 읽은 수는 방마다 따로 세지 않고 쿼리 한 번으로 센다([안 읽은 수 세기](#안-읽은-수-세기)).

## 실시간 전달

메시지 보내기는 HTTP API가 맡고, WebSocket은 **저장된 메시지를 알리는 데만** 쓴다([보내기를 HTTP로 하는 이유](#보내기를-http로-하는-이유)).

| 항목 | 값 |
| --- | --- |
| 연결 주소 | `ws://127.0.0.1:8080/ws` (개발 서버에서는 Vite 프록시 `ws://127.0.0.1:5173/ws`) |
| 프로토콜 | WebSocket 위의 STOMP. Spring 내장 메시지 브로커(simple broker), heartbeat 10초 |
| 고객 구독 주소 | `/user/queue/chat`. 자기 상담방의 새 메시지(`ChatMessageResponse`)만 온다 |
| 관리자 구독 주소 | `/topic/admin/chat`. 모든 상담방의 새 메시지가 `{ roomId, message }`로 온다 |
| 서버 구현 | [ChatWebSocketConfiguration](../src/main/java/com/example/payment/chat/infrastructure/websocket/ChatWebSocketConfiguration.java), [ChatSocketAuthorization](../src/main/java/com/example/payment/chat/infrastructure/websocket/ChatSocketAuthorization.java), [ChatNotifier](../src/main/java/com/example/payment/chat/infrastructure/websocket/ChatNotifier.java) |
| 설정이 적용되는 순서 | [Spring이 WebSocket 설정을 읽는 방식](websocket-configuration.md) |
| 화면 구현 | [chatSocket.ts](../frontend/src/chat/chatSocket.ts) (`@stomp/stompjs`) |

### 보내기를 HTTP로 하는 이유

WebSocket 하나로 보내기와 받기를 모두 하는 채팅도 많지만, 이 서비스는 보내기를 HTTP API로 하고 WebSocket은 전달에만 쓴다. 디스코드도 메시지 보내기는 HTTP API로, 받기는 WebSocket(Gateway)으로 나눈다.

| 이유 | 설명 |
| --- | --- |
| 보낸 결과를 안다 | 응답 상태로 저장(`201`), 재전송(`200`), 내용 오류(`400`), 충돌(`409`), 로그인 만료(`401`)를 구분한다. 응답이 없으면 같은 `clientMessageId`로 다시 보낸다. WebSocket으로 보내면 확인 응답, 오류 형식, 재전송 규칙을 따로 설계해야 한다 |
| 기존 장치를 그대로 쓴다 | Spring Security의 토큰·권한 검사, `@Valid` 입력 검증, `{ code, message }` 오류 응답, 요청당 트랜잭션, Swagger 문서와 MockMvc 테스트를 주문·결제 API와 똑같이 쓴다 |
| 보안이 단순하다 | 브라우저의 `SEND`를 모두 거부하므로, WebSocket으로 오가는 메시지는 서버가 DB에 저장한 뒤 보낸 것뿐이다. 브라우저가 가짜 메시지를 뿌릴 길이 없다 |
| 상담은 메시지가 적다 | HTTP는 요청마다 헤더와 토큰을 다시 보내 메시지당 비용이 조금 크지만, 1:1 상담의 전송 빈도에서는 문제가 되지 않는다 |

반대로 게임이나 실시간 공동 편집처럼 초당 여러 번 보내는 경우, 메시지가 아주 많은 단체 채팅, 타이핑 중 표시처럼 저장하지 않고 흘려보내는 신호는 WebSocket으로 보내는 편이 낫다.

### STOMP

STOMP(Simple Text Oriented Messaging Protocol)는 WebSocket 같은 양방향 연결 위에서 메시지를 주고받는 형식을 정한 텍스트 프로토콜이다([STOMP 1.2 명세](https://stomp.github.io/stomp-specification-1.2.html)). WebSocket은 데이터를 주고받는 통로만 제공하고, STOMP는 그 위에서 "어느 주소를 구독하고, 그 주소로 온 메시지를 받는다"는 규칙을 정한다.

주고받는 한 단위를 **프레임**이라 한다. 첫 줄은 명령, 그다음은 `이름:값` 헤더, 빈 줄 뒤는 본문이다. 고객 화면이 관리자 답변을 받기까지 오가는 프레임은 다음과 같다.

**① 연결**: 브라우저가 로그인 토큰을 헤더에 담아 연결한다. 서버는 토큰을 확인하고 `CONNECTED`로 답한다. 토큰이 없거나 틀리면 `ERROR`를 보내고 연결을 닫는다.

```text
CONNECT
accept-version:1.2
heart-beat:10000,10000
Authorization:Bearer eyJhbGciOi...
```

**② 구독**: 내 상담 알림을 받을 주소를 구독한다. `id`는 브라우저가 정한 구독 번호다.

```text
SUBSCRIBE
id:sub-0
destination:/user/queue/chat
```

**③ 받기**: 관리자가 HTTP로 답장해 DB에 커밋되면, 서버가 구독한 주소로 메시지를 보낸다. `subscription`은 ②의 구독 번호다.

```text
MESSAGE
destination:/user/queue/chat
subscription:sub-0
content-type:application/json

{"id":6,"sender":"ADMIN","content":"L이 가슴단면 3cm 더 커요.", ...}
```

브라우저가 메시지를 보내는 `SEND` 프레임은 받지 않는다([연결 인증과 구독 권한](#연결-인증과-구독-권한)).

#### 주소

- `/user/queue/chat`: 고객이 구독한다. 모든 고객이 같은 주소를 구독하지만, Spring이 연결에 붙은 회원(`sub`)별로 따로 전달하므로 자기 메시지만 받는다. 같은 회원이 탭을 여러 개 열면 모든 탭이 받는다.
- `/topic/admin/chat`: 관리자가 구독한다. 구독한 모든 관리자 연결이 같은 메시지를 받는다.

서버는 주소만 정해 보내고, 어느 연결로 보낼지는 Spring이 찾는다([ChatNotifier](../src/main/java/com/example/payment/chat/infrastructure/websocket/ChatNotifier.java)).

```java
messaging.convertAndSendToUser(customerId, "/queue/chat", message);      // 그 고객이 구독한 /user/queue/chat으로
messaging.convertAndSend("/topic/admin/chat", new ChatAdminEvent(...));  // 관리자 주소를 구독한 모든 연결로
```

STOMP 없이 WebSocket만 쓰면 메시지 형식, 회원별 연결 관리, 연결 인증, 끊김 감지(heartbeat)를 직접 만들어야 한다. 서버를 여러 대로 늘릴 때는 STOMP를 지원하는 외부 브로커(RabbitMQ 등)로 바꿀 수 있다([아직 없는 것](#아직-없는-것)).

### 연결 인증과 구독 권한

브라우저 WebSocket은 연결 요청에 `Authorization` 헤더를 붙일 수 없다. 그래서 `/ws` 연결 요청 자체는 Spring Security가 통과시키고, 연결 직후 보내는 STOMP `CONNECT` 프레임의 `Authorization: Bearer <access token>` 헤더를 [ChatSocketAuthorization](../src/main/java/com/example/payment/chat/infrastructure/websocket/ChatSocketAuthorization.java)이 검사한다.

| 프레임 | 검사 |
| --- | --- |
| `CONNECT` | HTTP API와 같은 `JwtDecoder`(서명·발급처·대상·만료)와 역할 변환기로 토큰을 검증하고 연결에 회원을 붙인다. 토큰이 없거나 틀리면 거부 |
| `SUBSCRIBE` | 고객은 `/user/queue/chat`만, `shop-admin` 역할이 있으면 `/topic/admin/chat`만 구독할 수 있다. 다른 회원의 개인 주소를 직접 구독하는 것도 거부 |
| `SEND` | 모두 거부. 브로커 주소로 바로 보내면 다른 구독자에게 가짜 메시지를 뿌릴 수 있기 때문이다 |

거부하면 서버가 STOMP `ERROR` 프레임을 보내고 연결을 닫는다.

### 끊김과 다시 연결

- 화면은 연결이 끊기면 3초 뒤 **새 access token으로** 다시 연결한다. 토큰은 5분만 유효하므로 처음 토큰을 재사용하지 않는다.
- 끊긴 동안 저장된 메시지는 알림으로 다시 오지 않는다. 다시 연결되면 화면이 `after={대화 조회로 확인한 마지막 번호}`로 조회해 채운다. 전송 응답이나 실시간 알림으로 더 큰 번호를 받아도 이 복구 위치는 옮기지 않는다. 예를 들어 1번까지 조회한 뒤 2번 답변을 놓치고 내 3번 전송 응답을 받아도, 복구 조회는 `after=1`부터 시작한다.
- 겹친 복구 조회는 순서대로 실행하고 성공한 페이지까지만 위치를 갱신한다. 빈 대화를 조회한 경우에는 0부터 시작한다. 중간 페이지 조회에 실패해도 다음 요청은 마지막으로 채운 위치부터 다시 이어 간다.
- 같은 메시지가 HTTP 응답, 알림, 다시 연결한 뒤 조회로 여러 번 와도 화면은 번호로 합쳐 한 번만 보여준다.
- 연결 중에 access token이 만료돼도 이미 맺은 연결은 유지된다. 로그아웃하면 화면이 연결을 닫는다.

## 화면

| 화면 | 위치 | 동작 |
| --- | --- | --- |
| 고객 문의 창 | 모든 쇼핑몰 화면 오른쪽 아래 **문의하기** ([ChatWidget](../frontend/src/components/ChatWidget.tsx)) | 로그인하지 않았으면 로그인 안내. 로그인하면 대화·보내기·이전 대화 보기. 창을 닫아도 연결을 유지해 **새 답변 N** 표시. 창을 열면 읽음으로 기록 |
| 관리자 바로가기 | 같은 자리, 쇼핑몰 관리자에게만 **상담 관리** ([ChatWidget](../frontend/src/components/ChatWidget.tsx)) | 누르면 상담 관리 화면으로 이동. 관리자 알림을 구독해 **답변 대기 N**을 실시간으로 표시 |
| 관리자 상담 관리 | `/admin/chat`, `/admin/chat/{roomId}` ([AdminChatPage](../frontend/src/pages/AdminChatPage.tsx)) | 헤더의 **상담 관리**는 `shop-admin`에게만 보인다. 왼쪽 목록(전체·답변 대기, 안 읽음 수), 오른쪽 대화와 답장. 새 메시지가 오면 목록을 다시 불러온다 |

- 보내는 중인 메시지는 흐리게 보이고, 실패하면 **다시 보내기**가 나온다. 다시 보내기는 같은 `clientMessageId`를 쓰므로 실제로는 저장됐던 메시지라도 한 번만 남는다.
- 고객 화면은 관리자 메시지를 **MODO CLUB**으로 표시한다.
- **배지와 읽음 요청**: 읽음 요청이 완료되면 확인된 번호 이하만 읽은 것으로 반영한다. 요청을 기다리다가 창을 닫고 새 답변을 받았더라도, 그 답변의 배지는 남는다. 읽음 응답 순서가 뒤바뀌어도 확인된 읽음 위치는 뒤로 가지 않는다.
- **배지와 대화 조회**: 대화를 조회하는 동안 새 답변이나 읽음 완료가 오면, 늦게 도착한 조회 결과로 배지를 덮어쓰지 않는다. 이어서 다시 조회해 서버의 최신 안 읽은 수로 맞춘다.
- 메뉴 표시는 편의 기능일 뿐이다. 권한은 서버가 `/admin/**`와 WebSocket 구독에서 검사한다.
- 대화 상태 관리는 [useChatThread](../frontend/src/chat/useChatThread.ts), 목록 합치기·중복 제거는 [chatMessages.ts](../frontend/src/lib/chatMessages.ts)에 있다.

## 아직 없는 것

| 기능 | 계획 |
| --- | --- |
| 상담 종료·문의 건별 구분 | 필요해지면 방에 상태를 더한다. 메시지 테이블은 그대로 쓴다 |
| 서버 여러 대 | 내장 브로커는 서버 한 대 안에서만 알린다. 서버를 늘리면 RabbitMQ 같은 외부 브로커나 Redis pub/sub으로 서버 간에 알림을 나눈다 |
| 연결 중 토큰 만료 처리 | 운영 전에는 토큰 만료 시각에 서버가 연결을 닫도록 한다 |
| 관리자끼리의 읽음 구분 | 관리자 읽음 위치는 방마다 하나라, 한 관리자가 읽으면 모든 관리자에게 읽은 것으로 보인다 |
