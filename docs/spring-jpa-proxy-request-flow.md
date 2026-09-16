# Spring MVC 요청이 JPA를 거쳐 DB까지 가는 객체와 프록시 흐름

이 문서는 이 프로젝트의 주문 생성 요청이 컨트롤러, 서비스, Repository, `EntityManager`, Hibernate와 JDBC를 거쳐 PostgreSQL까지 도달하는 과정을 설명한다.

핵심 질문은 다음 세 가지다.

1. 각 객체의 생성자나 필드에는 어떤 의존성 참조가 들어가며, 그것은 프록시인가 대상 객체인가?
2. 각 프록시는 왜 필요하고 어떤 대상 객체 또는 실행 전략에 위임하는가?
3. 트랜잭션과 영속성 컨텍스트는 어느 시점에 만들어지고 종료되는가?

프로젝트에서 사용하는 실제 코드는 다음 파일에서 확인할 수 있다.

- [OrderController](../src/main/java/com/example/payment/order/OrderController.java)
- [OrderService](../src/main/java/com/example/payment/order/OrderService.java)
- [OrderRepository](../src/main/java/com/example/payment/order/OrderRepository.java)
- [PurchaseOrder](../src/main/java/com/example/payment/order/PurchaseOrder.java)

## 1. 먼저 구분해야 하는 세 가지

빈, 싱글톤, 프록시는 같은 개념이 아니다.

```text
Bean
→ Spring 컨테이너가 생성하고 관리하는 객체인가?

Singleton
→ 컨테이너 안에 그 빈을 기본적으로 한 개만 두는가?

Proxy
→ 대상 객체를 호출하기 전에 중간 객체가 호출을 가로채는가?
```

프록시도 JVM 메모리에 존재하는 실제 Java 객체다. 따라서 이 문서에서는 프록시와 구분되는 안쪽 객체를 가리킬 때 `실제 객체`보다 `대상 객체(target)`라는 표현을 사용한다.

특히 이 문서에서 **대상 `EntityManager`**란 **공유 `EntityManager` 프록시가 호출을 위임하는 실제 `EntityManager` 인스턴스**를 뜻한다. 추상적인 대상이나 설정 정보가 아니라, 트랜잭션을 시작할 때 `EntityManagerFactory`가 실제로 생성하는 Java 객체다.

프록시가 적용되면 외부에 노출되는 프록시와 내부의 대상 객체는 서로 다른 Java 객체다.

`OrderService`는 기본적으로 singleton scope의 빈이다. Spring은 실제 `OrderService` 대상 객체를 생성하고 의존성을 주입하며 초기화한다. 트랜잭션 AOP가 적용되면 해당 빈의 외부 노출 객체는 대상 객체를 감싼 싱글톤 프록시가 된다.

대상 객체가 별도의 이름을 가진 두 번째 빈으로 등록되는 것은 아니다. 일반적인 자동 프록시 생성에서는 프록시가 사용하는 `SingletonTargetSource`가 동일한 대상 객체를 보관하고 호출을 위임한다.

```text
BeanDefinition
└─ orderService (singleton scope)

Spring Singleton Registry
└─ "orderService" ───────────────┐
                                  ▼
                     트랜잭션 프록시 객체
                                  │
                                  │ SingletonTargetSource
                                  ▼
                     실제 OrderService 대상 객체
```

따라서 `orderService`를 기준으로 세면 BeanDefinition, 빈 이름, Singleton Registry 등록 항목은 각각 하나다. 하지만 JVM에 존재하는 Java 인스턴스를 기준으로 세면 프록시 객체 하나와 대상 객체 하나, 총 두 개다. 대상 객체도 Spring이 생성하고 초기화·소멸 생명주기를 관리하지만, 별도의 빈 이름으로 조회할 수 있는 독립적인 두 번째 빈은 아니다.

```text
컨테이너에 두 개의 독립적인 빈이 등록됨
├─ orderServiceProxy
└─ orderServiceTarget
→ 아님

컨테이너에 하나의 빈이 프록시로 등록·노출됨
└─ orderService → 프록시 → 대상 객체
→ 맞음
```

`applicationContext.getBean("orderService")`로 조회하거나 다른 빈에 `OrderService`를 주입하면 대상 객체가 아니라 프록시가 전달된다.

`@Service`, `@Component`, `@Controller`는 우선 클래스를 Spring Bean으로 등록한다. 이 어노테이션 자체가 프록시를 만드는 것은 아니다. 트랜잭션, 캐시, 비동기, 보안 등의 부가 기능이 필요할 때 프록시가 추가된다.

## 2. 의존성 참조와 런타임 호출 흐름

먼저 다음 두 가지를 구분해야 한다.

```text
의존성 참조 관계
→ 어떤 객체의 필드에 어떤 참조가 들어 있는가?

런타임 호출 관계
→ 요청이 들어왔을 때 어떤 객체와 자원을 거쳐 DB까지 가는가?
```

### 2.1 객체가 보관하는 의존성 참조

애플리케이션이 시작되어 싱글톤 빈들이 준비된 뒤의 참조 관계는 다음과 같다.

```text
OrderController 객체
  └─ orderService 필드
       → OrderService AOP 프록시
            └─ target
                 → OrderService 대상 객체
                      └─ orderRepository 필드
                           → OrderRepository 프록시
                                └─ 기본 CRUD 호출의 대상
                                     → SimpleJpaRepository
                                          └─ entityManager 필드
                                               → 공유 EntityManager 프록시
                                                    └─ 호출 시점에 대상을 찾음
                                                         → 현재 트랜잭션의 대상 EntityManager
                                                           (공유 프록시가 호출을 위임하는 실제 EntityManager 인스턴스)
```

여기서 대상 `EntityManager`는 싱글톤 서비스나 Repository 필드에 고정되어 있지 않다. 트랜잭션이 시작될 때 준비되어 현재 실행 문맥에 연결되고, 공유 `EntityManager` 프록시가 호출 시점에 찾아 사용한다.

### 2.2 요청이 들어왔을 때의 호출 흐름

주문 생성 요청 `POST /orders`의 큰 흐름은 다음과 같다.

```text
HTTP 요청
  ↓
DispatcherServlet과 Spring MVC
  ↓
OrderController 싱글톤 객체
  ↓
OrderService AOP 프록시
  ├─ 호출 전: TransactionInterceptor
  │    └─ JpaTransactionManager
  │         ├─ EntityManagerFactory로 대상 EntityManager 준비
  │         └─ 트랜잭션 시작 및 현재 스레드의 트랜잭션 자원 저장소에 등록
  ├─ proceed: OrderService 대상 객체
  └─ 호출 후: JpaTransactionManager에 commit 또는 rollback 요청
  ↓
OrderRepository 프록시
  ↓
SimpleJpaRepository 또는 쿼리 실행기
  ↓
공유 EntityManager 프록시
  ↓
현재 트랜잭션의 대상 EntityManager
  ↓
영속성 컨텍스트
  ↓
Hibernate SQL 실행 계층
  ↓
JDBC Connection과 PostgreSQL 드라이버
  ↓
PostgreSQL
```

각 프록시는 목적이 다르다.

```text
Service AOP 프록시
→ 대상 메서드 호출 앞뒤에 트랜잭션 처리를 추가한다.

Repository 프록시
→ 호출된 Repository 메서드에 맞는 구현이나 쿼리 실행 방법을 선택한다.

공유 EntityManager 프록시
→ 현재 스레드와 트랜잭션에 맞는 대상 EntityManager를 선택한다.

Hibernate 지연 로딩 프록시
→ 아직 조회하지 않은 엔티티 데이터를 실제 접근 시점에 조회한다.
```

Hibernate 지연 로딩 프록시는 모든 조회에서 반드시 나타나는 단계가 아니다. 지연 로딩 연관관계나 `getReference()` 등을 사용할 때 나타난다. 현재 `PurchaseOrder`에는 지연 로딩 연관관계가 없으므로 주문 저장 흐름의 필수 단계가 아니다.

## 3. 객체별 역할과 수명

| 객체 또는 계층 | 호출되거나 사용되는 객체 | 위임 대상 또는 내부 상태 | 역할 | 일반적인 수명 |
| --- | --- | --- | --- | --- |
| Controller | `OrderController` 객체 | 자신의 요청 처리 메서드 | HTTP 요청을 서비스 호출로 변환 | 애플리케이션 동안 싱글톤 |
| Service AOP | `OrderService` 프록시 | `OrderService` 대상 객체 | 트랜잭션 등 메서드 앞뒤의 부가 기능 | 프록시는 싱글톤 빈으로 노출되고 동일한 대상 인스턴스를 계속 사용 |
| 트랜잭션 처리 | 프록시 내부의 `TransactionInterceptor` | `JpaTransactionManager` | 트랜잭션 속성을 읽고 시작·참여·완료를 요청 | 보통 싱글톤 인프라 |
| 트랜잭션 관리자 | `JpaTransactionManager` | `EntityManagerFactory`, 현재 트랜잭션 자원 | 대상 `EntityManager` 준비, 바인딩, commit·rollback | 보통 싱글톤 |
| Repository | `OrderRepository` 프록시 | `SimpleJpaRepository`, 쿼리 실행기, 사용자 구현 | 인터페이스 구현 및 메서드별 실행 전략 선택 | 보통 싱글톤 |
| JPA 팩토리 | `EntityManagerFactory` | Hibernate 팩토리 구현 | 대상 `EntityManager` 생성 | 애플리케이션 동안 싱글톤, 스레드 안전 |
| JPA 접근 창구 | 공유 `EntityManager` 프록시 | 현재 트랜잭션의 대상 `EntityManager` | 현재 실행 문맥의 대상 선택 | 프록시는 장기간 재사용 |
| JPA 작업 단위 | 대상 `EntityManager` | Hibernate에서는 `Session` 구현 | 영속성 컨텍스트를 조작하고 SQL 실행을 준비 | 보통 트랜잭션 또는 요청 범위 |
| 상태 관리 | 영속성 컨텍스트 | 관리 엔티티, 식별 정보, 스냅샷 | 동일성 보장, 변경 감지, 쓰기 지연 | 대상 `EntityManager`와 연결 |
| ORM | Hibernate | SQL 생성·flush·JDBC 호출 코드 | 객체 상태를 관계형 DB 작업으로 변환 | 애플리케이션 설정과 작업 단위에 따라 다름 |
| DB 연결 | JDBC `Connection` | 커넥션 풀에서 빌린 실제 연결 | SQL을 DB에 전달하고 DB 트랜잭션 수행 | 트랜잭션 동안 대여 후 반환 |

## 4. Controller: 보통 일반 싱글톤 객체

프로젝트의 컨트롤러는 다음과 같다.

```java
@RestController
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> create() {
        OrderResponse order = orderService.create();
        // 응답 생성
    }
}
```

`@RestController`는 이 객체를 Spring MVC의 요청 처리 대상으로 등록한다. 트랜잭션이나 별도의 AOP 기능이 없다면 `OrderController` 자체는 일반 싱글톤 객체다.

컨트롤러가 하는 일은 다음과 같다.

```text
HTTP 요청을 Java 메서드 호출로 받기
→ 입력값 변환과 검증
→ 서비스 호출
→ 서비스 결과를 HTTP 응답으로 변환
```

컨트롤러의 `orderService` 필드에는 `OrderService` 대상 객체가 아니라 Spring이 외부에 노출한 `OrderService` 프록시가 주입된다.

## 5. Service AOP 프록시: 같은 대상을 호출하면서 앞뒤를 감싼다

프로젝트의 `OrderService.create()`에는 `@Transactional`이 있다.

```java
@Service
public class OrderService {

    @Transactional
    public OrderResponse create() {
        PurchaseOrder order = new PurchaseOrder("티셔츠", 1, 10_000);
        order = orderRepository.save(order);
        return OrderResponse.of(order, null);
    }
}
```

이 프로젝트에서는 트랜잭션 기능이 활성화되어 있다. Spring은 적용 가능한 `@Transactional` 메서드를 발견하면 `OrderService` 호출을 가로챌 AOP 프록시를 만든다.

```text
OrderController.orderService
  ↓ 참조하는 객체
OrderService AOP 프록시
  ↓ 항상 위임하는 대상
OrderService 대상 객체
```

프록시 안의 `TransactionInterceptor`가 하는 일은 개념적으로 다음과 같다.

```java
// 1. @Transactional 설정을 바탕으로 새 트랜잭션을 시작하거나 기존 트랜잭션에 참여한다.
TransactionStatus status =
        transactionManager.getTransaction(settings);

try {
    // 2. 실제 OrderService 대상 객체의 메서드를 실행한다.
    Object result = targetOrderService.create();

    // 3. 대상 메서드가 정상적으로 끝났으면 커밋을 요청한다.
    transactionManager.commit(status);

    return result;
} catch (Throwable exception) {
    // 4. 예외 종류와 @Transactional의 롤백 규칙을 확인한다.
    if (rollbackRules.requireRollback(exception)) {
        transactionManager.rollback(status);
    } else {
        transactionManager.commit(status);
    }

    // 5. 트랜잭션을 정리한 뒤 예외를 호출자에게 다시 전달한다.
    throw exception;
}
```

여기서 `settings`는 전파 속성, 격리 수준, 제한 시간, 읽기 전용 여부, 롤백 규칙 같은 `@Transactional` 설정을 뜻한다. `getTransaction(settings)`이 항상 새 트랜잭션을 만드는 것은 아니다.

기본 전파 속성은 `REQUIRED`다.

```text
기존 트랜잭션 없음
→ 새 물리적 트랜잭션 시작
→ 이 메서드의 종료 과정에서 실제 commit 또는 rollback 수행

기존 트랜잭션 있음
→ 새 트랜잭션을 만들지 않고 기존 물리적 트랜잭션에 참여
→ 이 메서드가 정상 종료되어도 즉시 DB commit하지 않음
→ 가장 바깥쪽 트랜잭션 경계에서 최종 commit 또는 rollback 수행
```

따라서 위 코드의 `commit(status)`는 무조건 즉시 DB에 물리적 커밋을 실행하라는 뜻이 아니라, 현재 트랜잭션 참여 구간이 정상적으로 끝났음을 트랜잭션 관리자에게 알리는 요청이다. 이 메서드가 새 트랜잭션을 시작했다면 실제 커밋으로 이어지고, 기존 트랜잭션에 참여했다면 최종 완료는 바깥쪽 트랜잭션 경계가 담당한다.

기본 롤백 규칙은 다음과 같다.

```text
대상 메서드 정상 반환
→ commit 요청

RuntimeException 또는 Error 발생
→ rollback 요청
→ 예외를 호출자에게 다시 전달

체크 예외 발생
→ 기본적으로 commit 요청
→ 예외는 호출자에게 다시 전달
```

체크 예외도 롤백하려면 `@Transactional(rollbackFor = SomeCheckedException.class)`처럼 `rollbackFor`를 설정한다. 기존 트랜잭션에 참여한 메서드에서 롤백을 요청한 경우에는 공유 중인 물리적 트랜잭션이 rollback-only 상태가 되어, 바깥쪽 트랜잭션 경계에서 최종적으로 롤백될 수 있다.

서비스 프록시는 보통 여러 서비스 대상 객체 중 하나를 고르는 라우터가 아니다. 동일한 대상 객체를 호출하되, 적용할 인터셉터 파이프라인을 고른다.

```text
proxy.create()
→ TransactionInterceptor
→ 대상 객체의 create()

proxy.somePlainMethod()
→ 트랜잭션 인터셉터 적용 없음
→ 대상 객체의 somePlainMethod()
```

서비스 클래스에 대상이 되는 `@Transactional` 메서드가 하나만 있어도 빈 전체가 프록시로 노출될 수 있다. 하지만 트랜잭션 처리는 설정이 적용된 메서드에만 수행된다.

## 6. Service 대상 객체: 비즈니스 작업의 순서를 정한다

`OrderService` 대상 객체는 프록시 안쪽에서 비즈니스 흐름을 실행한다.

```text
주문 객체 생성
→ 주문 저장
→ 응답 객체 생성
```

서비스 대상 객체 자체가 트랜잭션을 시작하는 것은 아니다. 외부의 AOP 프록시와 트랜잭션 인프라가 트랜잭션을 준비한 뒤 대상 메서드를 호출한다.

같은 객체 내부에서 `this.inner()`를 호출하면 대상 객체가 자신의 메서드를 직접 호출한다.

```text
외부 → Service 프록시 → 대상 객체의 outer()
                         └─ this.inner()
                            대상 객체의 inner() 직접 호출
```

따라서 `inner()`에 별도의 `@Transactional` 전파 설정이 있더라도 자기 호출에서는 적용되지 않는다. 다만 `outer()`에서 이미 트랜잭션이 시작되었다면 `inner()`의 코드는 그 기존 트랜잭션 안에서 실행된다.

## 7. Repository 프록시: 메서드에 맞는 실행 방법을 선택한다

프로젝트의 Repository는 구현 클래스가 없는 인터페이스다.

```java
public interface OrderRepository
        extends JpaRepository<PurchaseOrder, String> {
}
```

인터페이스는 직접 생성할 수 없다.

```java
new OrderRepository(); // 불가능
```

Spring Data JPA는 애플리케이션 초기화 과정에서 `OrderRepository`를 구현하는 프록시 객체를 만든다. 이 프록시 자체가 Spring Bean으로 서비스 대상 객체에 주입된다.

```text
OrderService 대상 객체의 orderRepository
  ↓ 참조하는 객체
OrderRepository 프록시
```

Repository 프록시는 호출된 메서드를 기준으로 실행 전략을 선택한다.

```text
save(entity)
→ SimpleJpaRepository.save()

findById(id)
→ SimpleJpaRepository.findById()

findByEmail(email)
→ 메서드 이름으로 준비된 쿼리 실행

@Query 메서드
→ 지정된 JPQL 또는 native query 실행

사용자 정의 메서드
→ Repository fragment 구현으로 위임
```

Repository 프록시에는 트랜잭션과 JPA 예외 변환 인터셉터도 포함될 수 있다. 서비스에서 이미 트랜잭션이 시작됐다면 Repository의 기본 `REQUIRED` 트랜잭션은 새 트랜잭션을 만들지 않고 기존 트랜잭션에 참여한다.

```text
OrderService.create() 트랜잭션 시작
  ↓
OrderRepository.save()
  ↓ 기존 트랜잭션 참여
SimpleJpaRepository.save()
```

Repository 생성과 `save()` 내부의 더 자세한 구현은 [OrderRepository.save()가 DB까지 도달하는 전체 코드 경로](spring-data-jpa-save-flow.md)에서 확인할 수 있다.

## 8. SimpleJpaRepository: 기본 CRUD의 대상 구현

`JpaRepository`가 제공하는 기본 CRUD의 대표 구현체가 `SimpleJpaRepository`다.

JPA의 `EntityManager`에는 `save()`라는 메서드가 없다. `save()`는 Spring Data JPA가 제공하는 상위 수준의 메서드로, 엔티티 상태를 보고 `persist()`와 `merge()` 중 하나를 선택한다.

```text
repository.save(entity)
    ↓
Spring Data가 객체의 Version·ID로 새 엔티티 여부를 판정
    ├─ 새 엔티티(isNew(entity) == true)
    │    → EntityManager.persist(entity)
    │    → flush 때 INSERT
    │         └─ 보통 commit 직전에 자동 flush
    │
    └─ 기존 엔티티(isNew(entity) == false)
         → EntityManager.merge(entity)
         → 필요하면 같은 ID의 DB 행 확인
         → flush 때 INSERT 또는 UPDATE
              └─ 보통 commit 직전에 자동 flush

결론
→ save()가 persist() 또는 merge()를 호출해 저장 상태를 준비한다.
→ flush 시점에 Hibernate가 실제 INSERT 또는 UPDATE SQL을 만들어 JDBC로 DB에 실행한다.
→ 트랜잭션이 commit되어야 최종 저장이 확정된다.
```

`save()`는 개념적으로 다음과 같다.

```java
if (entityInformation.isNew(entity)) {
    entityManager.persist(entity);
    return entity;
}

return entityManager.merge(entity);
```

여기서 `isNew()`는 같은 ID의 행이 DB에 있는지 조회하지 않는다. Spring Data JPA는 기본적으로 객체의 `@Version` 값과 ID 값으로 새 엔티티 여부를 판정한다.

```text
nullable 타입의 @Version 필드가 있음
→ version == null이면 새 엔티티

@Version 필드가 없음
→ id == null이면 새 엔티티
→ id != null이면 기존 엔티티로 판정
```

현재 프로젝트의 `PurchaseOrder`는 `@Version` 필드가 없고, 생성자에서 문자열 UUID를 ID에 미리 넣는다.

```text
new PurchaseOrder(...)
→ 생성자에서 UUID 할당
→ PurchaseOrder.id != null
→ isNew(order) == false
→ entityManager.merge(order)
```

따라서 방금 만든 새 Java 객체이고 DB에 아직 행이 없어도 Spring Data의 기본 판정에서는 `merge()` 경로를 선택한다. Spring Data의 새 엔티티 판정은 DB 조회 결과가 아니라 객체의 Version·ID 값을 이용한 추정이기 때문이다.

`persist()`는 전달받은 새 객체 자체를 관리 상태로 만든다. 반면 `merge()`는 전달받은 객체의 상태를 관리 객체에 복사하고 그 관리 객체를 반환한다. Hibernate는 `merge()`를 처리하면서 필요하면 같은 ID의 행을 조회하며, 행이 없으면 `INSERT`, 있으면 `UPDATE`로 이어질 수 있다.

현재처럼 `new`로 만든 비관리 객체를 전달하는 경우 두 메서드의 차이는 다음과 같다.

```text
persist 전
entity → 비관리 객체 A

persist 후
entity → 관리 객체 A

같은 객체 A가 영속성 컨텍스트의 관리 대상이 된다.
```

```text
merge 전
entity → 비관리 객체 A

merge 후
entity  → 비관리 객체 A
managed → 관리 객체 B
             ↑
       A의 필드 값을 복사

merge()는 관리 객체 B를 반환한다.
```

```text
Spring Data의 isNew()
→ Version·ID 값만 확인
→ DB 조회 없음
→ persist() 또는 merge() 선택

Hibernate의 merge()
→ 필요하면 같은 ID의 DB 행 확인
→ 행이 없으면 INSERT
→ 행이 있으면 UPDATE 가능
```

관리 객체는 영속성 컨텍스트가 열려 있는 동안 다음과 같은 기능을 제공받는다.

- 필드 변경을 감지하여 flush 때 필요한 `UPDATE`를 자동으로 준비한다.
- `@Version` 필드가 있으면 낙관적 락 검사와 버전 증가를 관리한다.
- 생성 ID처럼 JPA 제공자가 생성하거나 반영하는 값을 관리 객체에서 사용할 수 있다.
- 연관관계와 지연 로딩 프록시·컬렉션을 영속성 컨텍스트와 연결하여 사용할 수 있다.

`merge()` 경로에서는 반환된 객체가 영속성 컨텍스트의 관리 객체이므로 `save()`의 반환값을 사용하는 것이 중요하다.

```java
order = orderRepository.save(order);
```

위 코드에서 `order`는 처음 만든 비관리 객체 A 대신 `merge()`가 반환한 관리 객체 B를 가리키게 된다.

이때 `SimpleJpaRepository`의 `entityManager` 필드에는 특정 대상 `EntityManager`가 아니라 공유 프록시가 들어 있다. 공유 프록시가 현재 트랜잭션의 대상 `EntityManager`를 찾는 과정은 9.5절에서 설명한다.

## 9. EntityManager 프록시와 대상 EntityManager의 전체 흐름

먼저 애플리케이션 시작부터 트랜잭션 종료까지의 흐름을 한 번에 보면 다음과 같다. 공유 `EntityManager` 프록시는 애플리케이션 초기화 때 준비되고, 실제 JPA 작업을 수행하는 대상 `EntityManager`는 트랜잭션이 시작될 때 준비된다는 차이가 핵심이다. 아래 HTTP 요청 구간은 기존 트랜잭션이 없는 상태에서 `OrderService.create()`를 외부 호출하는 경우를 기준으로 하며, 내부 클래스와 메서드 이름은 현재 프로젝트가 사용하는 Spring ORM 7.0.9를 기준으로 한다.

```text
애플리케이션 시작
  ↓
EntityManagerFactory 준비
  ↓
공유 EntityManager 프록시 생성
  ↓
SimpleJpaRepository.entityManager 필드에 주입

────────────────────────────────────────

HTTP 요청 시작
  ↓
OrderController
  ↓
OrderService AOP 프록시
  ↓ TransactionInterceptor가 @Transactional 설정 확인
JpaTransactionManager
  ↓ TransactionSynchronizationManager에서 기존 자원 확인
기존 EntityManagerHolder 없음
  ↓ 새 트랜잭션을 시작해야 하므로 대상 EntityManager 생성 필요
JpaTransactionManager.doBegin()
  ├─ 대상 EntityManager A 생성
  │    └─ 공유 프록시가 호출을 위임하는 실제 EntityManager 인스턴스
  ├─ EntityManagerHolder A 생성
  │    └─ 대상 EntityManager A 보관
  ├─ JpaDialect.beginTransaction(대상 EntityManager A, ...)
  │    └─ Hibernate가 JDBC·DB 트랜잭션과 연결
  │         (JDBC Connection 획득은 필요한 시점까지 지연될 수 있음)
  └─ TransactionSynchronizationManager.bindResource(
         EntityManagerFactory, EntityManagerHolder A)
       ↓ 현재 요청 처리 스레드의 자원 Map에 등록

여기까지가 OrderService.create() 대상 메서드 본문 실행 전
→ 대상 EntityManager A는 이미 생성·등록된 상태
→ orderRepository.save(order)가 EntityManager를 생성하는 것이 아님

       ↓
TransactionInterceptor가 대상 호출을 계속 진행
  ↓
OrderService 대상 객체의 create() 본문 실행 시작
  ↓ orderRepository.save(order)
OrderRepository 프록시
  ↓ 기존 트랜잭션에 참여하고 기본 CRUD 구현으로 위임
SimpleJpaRepository.save(order)
  ↓ isNew(order) == false
entityManager.merge(order)
  ↓ entityManager 필드가 가리키는 객체
공유 EntityManager 프록시
  ↓ JDK 프록시의 호출 처리기가 merge(order)를 가로챔
SharedEntityManagerInvocationHandler.invoke()
  ↓ EntityManagerFactoryUtils.doGetTransactionalEntityManager(
       EntityManagerFactory, ...)
EntityManagerFactoryUtils
  ↓ TransactionSynchronizationManager.getResource(EntityManagerFactory)
현재 스레드의 자원 Map
  ↓ 같은 EntityManagerFactory 키로 앞서 등록한 Holder 조회
EntityManagerHolder A
  ↓ EntityManagerHolder.getEntityManager()
앞서 생성해 둔 대상 EntityManager A
  ↓ 동일한 merge(order) 호출을 실제로 실행
영속성 컨텍스트
  ↓ 엔티티 상태 관리 및 변경 사항 기록

────────────────────────────────────────

OrderService 대상 메서드 정상 반환
  ↓
TransactionInterceptor가 JpaTransactionManager.commit(status) 요청
  ↓
JpaTransactionManager.doCommit()
  ↓ EntityManagerHolder A에서 대상 EntityManager A 획득
대상 EntityManager A의 트랜잭션에 commit() 호출
  ↓
Hibernate가 commit 직전에 flush
  ↓ 필요한 INSERT 또는 UPDATE SQL 생성
JDBC가 SQL을 PostgreSQL에 실행
  ↓
DB 트랜잭션 commit
  ↓
JpaTransactionManager.doCleanupAfterCompletion()
  ├─ TransactionSynchronizationManager에서 EntityManagerHolder A 제거
  └─ 대상 EntityManager A close
```

현재 `PurchaseOrder`는 8장에서 설명한 새 엔티티 판정 규칙 때문에 `merge()` 경로를 사용한다. 위 그림은 공유 `EntityManager` 프록시의 라우팅과 직접 관련된 경로를 중심으로 나타냈으며, JDBC `ConnectionHolder` 같은 부가 자원의 등록은 생략했다. 이제 위 흐름을 위에서부터 나누어 살펴본다.

### 9.1 흐름에 참여하는 객체의 역할

서비스 AOP 프록시가 혼자 트랜잭션의 모든 작업을 처리하는 것은 아니다. 각 객체의 역할은 다음과 같이 나뉜다.

```text
OrderService AOP 프록시
→ 호출을 가로채고 인터셉터 체인을 실행

TransactionInterceptor
→ @Transactional 설정을 읽고 트랜잭션 실행 절차를 적용

JpaTransactionManager
→ 기존 트랜잭션 참여 여부 판단
→ 새 트랜잭션이면 대상 EntityManager 준비
→ TransactionSynchronizationManager에 EntityManagerHolder 등록
→ commit 또는 rollback

EntityManagerFactory
→ 대상 EntityManager를 생성하는 스레드 안전한 팩토리

공유 EntityManager 프록시
→ 현재 실행 문맥의 대상 EntityManager를 찾아 호출을 위임

대상 EntityManager
→ 영속성 컨텍스트를 사용하여 실제 JPA 작업 수행
```

### 9.2 애플리케이션 시작: 공유 EntityManager 프록시 준비

애플리케이션이 시작되면 `EntityManagerFactory`와 공유 `EntityManager` 프록시가 준비된다. Spring Data JPA는 이 공유 프록시를 `SimpleJpaRepository`의 `entityManager` 필드에 넣는다.

```text
EntityManagerFactory 준비
  ↓
공유 EntityManager 프록시 생성
  ↓
SimpleJpaRepository.entityManager 필드에 주입
```

이때 만들어지는 공유 프록시는 특정 대상 `EntityManager`를 고정해서 보관하지 않는다. 여러 요청이 하나의 프록시를 함께 사용하며, 실제 메서드가 호출되는 시점에 현재 실행 문맥에 맞는 대상을 찾는다.

`@PersistenceContext`로 애플리케이션 코드에 `EntityManager`를 주입받을 때도 일반적으로 같은 성격의 컨테이너 관리 공유 프록시가 주입된다.

```java
@PersistenceContext
private EntityManager entityManager;
```

이는 특정 대상 `EntityManager`를 고정해서 넣으라는 뜻이 아니라, 현재 영속성 컨텍스트에 접근할 수 있는 컨테이너 관리 참조를 넣으라는 뜻이다. 현재 프로젝트의 `OrderService`는 이를 직접 사용하지 않지만, Spring Data JPA가 `SimpleJpaRepository` 내부에서 사용한다.

### 9.3 HTTP 요청과 트랜잭션 시작: 대상 EntityManager 준비

HTTP 요청이 들어와 `OrderService` AOP 프록시를 호출하면 `TransactionInterceptor`가 `@Transactional` 설정을 읽는다. 기본 전파 속성 `REQUIRED`에서 현재 스레드에 참여할 기존 트랜잭션이 없다면 `JpaTransactionManager`가 새 트랜잭션을 시작한다.

```text
OrderController
  ↓
OrderService AOP 프록시
  ↓ TransactionInterceptor가 트랜잭션 요청
JpaTransactionManager.getTransaction(...)
  ↓ 내부에서 호출
JpaTransactionManager.doGetTransaction()
  ↓ TransactionSynchronizationManager.getResource(EntityManagerFactory)
기존 EntityManagerHolder 없음
  ↓
JpaTransactionManager.doBegin()
  ├─ createEntityManagerForTransaction()
  │    └─ 대상 EntityManager A 생성
  ├─ new EntityManagerHolder(대상 EntityManager A)
  ├─ JpaDialect.beginTransaction(대상 EntityManager A, ...)
  │    └─ Hibernate가 JDBC·DB 트랜잭션과 연결
  │         (JDBC Connection 획득은 필요한 시점까지 지연될 수 있음)
  └─ TransactionSynchronizationManager.bindResource(
         EntityManagerFactory, EntityManagerHolder A)
```

여기서 `대상 EntityManager A`는 추상적인 대상을 뜻하지 않는다. `EntityManagerFactory`가 생성했으며, 이후 공유 `EntityManager` 프록시가 `persist()`나 `merge()` 호출을 실제로 넘겨주는 `EntityManager` 인스턴스다.

현재 프로젝트에서 정확한 생성 시점은 **`OrderService.create()` 대상 메서드 본문에 진입하기 전**이다. `TransactionInterceptor`가 새 트랜잭션이 필요하다고 판단하고, 현재 스레드에서 기존 `EntityManagerHolder`를 찾지 못했을 때 `JpaTransactionManager.doBegin()`이 대상 `EntityManager`를 생성한다.

```text
대상 EntityManager 생성 트리거
→ 새 트랜잭션을 시작해야 함
→ 현재 스레드에 재사용할 EntityManagerHolder가 없음

대상 EntityManager 생성 시점
→ OrderService.create() 본문 실행 전
→ new PurchaseOrder(...) 실행 전
→ orderRepository.save(order) 호출 전
```

따라서 현재 흐름에서 `save()`는 대상 `EntityManager`를 생성하지 않는다. `save()` 안에서 호출되는 공유 `EntityManager` 프록시가 앞서 생성·등록된 대상 `EntityManager A`를 찾아 사용한다.

단, 바깥 서비스 트랜잭션이 없다면 Repository 프록시가 `save()`의 트랜잭션을 시작하면서 대상 `EntityManager`를 생성할 수 있다. 이 경우에도 `SimpleJpaRepository.save()`의 본문에 들어가기 전에 생성된다. 공통 기준은 `save()` 호출 자체가 아니라 **새 트랜잭션을 시작하는 경계**다.

`EntityManagerFactory`는 애플리케이션에서 보통 하나를 장기간 공유해도 되는 스레드 안전한 팩토리다. 반면 이 팩토리가 만드는 대상 `EntityManager`는 스레드 안전하지 않으므로 트랜잭션 같은 작업 단위별로 분리한다.

기존 트랜잭션이 있다면 `REQUIRED`는 대상 `EntityManager`를 새로 만들지 않고 기존 트랜잭션과 그 자원에 참여한다.

### 9.4 현재 스레드의 트랜잭션 자원 저장소에 바인딩

Spring MVC에서는 일반적으로 하나의 요청을 하나의 스레드가 처리한다.

```text
HTTP 요청 A → http-nio-8080-exec-1 스레드
HTTP 요청 B → http-nio-8080-exec-2 스레드
```

Spring은 `TransactionSynchronizationManager`를 사용하여 스레드별 트랜잭션 자원을 관리한다. 내부 구조를 단순화하면 다음과 같이 스레드마다 별도의 자원 Map이 있는 형태다.

```java
ThreadLocal<Map<Object, Object>> resources;
```

`JpaTransactionManager`는 생성한 대상 `EntityManager`를 `EntityManagerHolder`로 감싼 다음, `TransactionSynchronizationManager.bindResource()`를 호출하여 `EntityManagerFactory`를 키로 현재 스레드의 자원 저장소에 등록한다.

```text
현재 스레드: http-nio-8080-exec-1

TransactionSynchronizationManager의 자원 저장소
└─ EntityManagerFactory
     → EntityManagerHolder
          → 대상 EntityManager A
```

이 등록 과정을 흔히 **대상 `EntityManager`를 현재 스레드에 바인딩한다**고 표현한다. 서비스 프록시 안에 `EntityManager`를 넣는다는 뜻이 아니라, 같은 스레드에서 실행될 공유 프록시가 나중에 찾을 수 있는 별도의 저장소에 등록한다는 뜻이다.

### 9.5 Repository 호출: 공유 EntityManager 프록시의 라우팅

트랜잭션 준비가 끝나면 `TransactionInterceptor`가 `OrderService` 대상 메서드 호출을 계속 진행한다. `OrderService`가 `orderRepository.save(order)`를 호출하면 Repository 프록시는 기존 트랜잭션에 참여하고 `SimpleJpaRepository.save()`로 위임한다.

`SimpleJpaRepository`가 자신의 `entityManager` 필드에서 `persist()` 또는 `merge()`를 호출하면 실제로 먼저 호출되는 객체는 공유 `EntityManager` 프록시다. 이 프록시는 `SharedEntityManagerCreator`가 만든 JDK 동적 프록시이며, 내부 호출 처리기가 다음 순서로 현재 대상을 찾는다.

```text
SimpleJpaRepository
  ↓ entityManager.persist(entity) 또는 merge(entity)
공유 EntityManager 프록시
  ↓ 메서드 호출을 가로챔
SharedEntityManagerCreator.SharedEntityManagerInvocationHandler.invoke()
  ↓ EntityManagerFactoryUtils.doGetTransactionalEntityManager(
       EntityManagerFactory, properties, true)
EntityManagerFactoryUtils
  ↓ TransactionSynchronizationManager.getResource(EntityManagerFactory)
현재 스레드의 자원 Map
  ↓ 같은 EntityManagerFactory 키로 조회
EntityManagerHolder A
  ↓ EntityManagerHolder.getEntityManager()
현재 트랜잭션의 대상 EntityManager A
  ↓ invocation handler가 동일한 메서드를 대상에 호출
대상 EntityManager A.persist(entity) 또는 merge(entity)
```

공유 프록시는 대상 `EntityManager`를 필드에 보관하지 않고, 대상을 찾을 때 사용할 `EntityManagerFactory` 참조를 가지고 있다. `JpaTransactionManager`가 등록할 때 사용한 것과 동일한 `EntityManagerFactory`를 키로 전달하기 때문에 현재 스레드의 `EntityManagerHolder A`를 찾을 수 있다. Holder에서 대상 `EntityManager A`를 꺼낸 호출 처리기는 원래 받은 것과 동일한 `persist()` 또는 `merge()` 호출을 그 대상에 실행한다.

즉, 여기서 **라우팅**은 다음 세 단계다.

1. 공유 프록시가 `EntityManagerFactoryUtils`에 `EntityManagerFactory`를 전달한다.
2. `EntityManagerFactoryUtils`가 현재 스레드에서 그 팩토리 키에 해당하는 `EntityManagerHolder`를 찾는다.
3. Holder 안의 대상 `EntityManager`를 꺼내 원래 메서드 호출을 그대로 넘긴다.

이 트랜잭션 흐름에서는 `JpaTransactionManager`가 Holder를 먼저 등록했으므로 같은 대상 `EntityManager A`를 찾는다. 등록된 Holder가 없는 경우의 동작은 11장에서 별도로 설명한다.

```text
JpaTransactionManager
→ 대상 EntityManager 생성·트랜잭션 시작·현재 스레드에 등록

공유 EntityManager 프록시
→ 현재 스레드에 등록된 대상 EntityManager 검색·호출 위임

대상 EntityManager
→ 전달받은 실제 JPA 작업 수행
```

### 9.6 대상 EntityManager와 영속성 컨텍스트

공유 프록시가 찾은 대상 `EntityManager`가 실제 `persist()`, `merge()`, `find()` 같은 JPA 작업을 수행한다. 대상 `EntityManager`는 영속성 컨텍스트를 조작하는 JPA API이자 관리자다.

```text
대상 EntityManager
  ↓ 관리
영속성 컨텍스트
  ├─ 현재 관리 중인 엔티티
  ├─ 엔티티 ID와 Java 객체의 대응
  ├─ 변경 감지용 상태
  └─ flush 시 반영할 변경 사항
```

주요 메서드의 의미는 다음과 같다.

```text
find()
→ 영속성 컨텍스트에서 먼저 찾고, 없으면 DB 조회 후 관리

persist()
→ 새 엔티티를 관리 상태로 등록

merge()
→ 전달받은 상태를 관리 상태 객체에 복사하고 그 객체를 반환

remove()
→ 관리 엔티티를 삭제 대상으로 표시

flush()
→ 영속성 컨텍스트의 변경 사항을 SQL로 DB에 반영

clear() / close()
→ 관리하던 엔티티를 분리 상태로 전환
```

Hibernate를 JPA 구현체로 사용하면 대상 `EntityManager`의 실제 구현은 Hibernate `Session` 계열 객체다.

```text
JPA EntityManager 인터페이스
  ↓ Hibernate 구현
Hibernate Session
  ↓ 내부 상태
영속성 컨텍스트
```

### 9.7 정상 반환부터 commit과 종료까지

`OrderService` 대상 메서드가 정상 반환하면 `TransactionInterceptor`가 `JpaTransactionManager`에 commit을 요청한다. Hibernate는 보통 commit 직전에 flush하여 필요한 SQL을 만들고 JDBC를 통해 DB에 실행한다. DB commit이 성공하면 최종 저장이 확정된다.

```text
OrderService 대상 메서드 정상 반환
  ↓
TransactionInterceptor가 JpaTransactionManager.commit(status) 요청
  ↓
JpaTransactionManager.doCommit()
  ↓ EntityManagerHolder에서 대상 EntityManager 획득
대상 EntityManager의 트랜잭션에 commit() 호출
  ↓
Hibernate가 commit 직전에 flush
  ↓ 필요한 INSERT 또는 UPDATE SQL 생성
JDBC가 SQL을 DB에 실행
  ↓
DB 트랜잭션 commit
  ↓
JpaTransactionManager.doCleanupAfterCompletion()
  ├─ 현재 스레드에서 EntityManagerHolder 제거
  └─ 대상 EntityManager close
```

현재 프로젝트는 OSIV를 비활성화했으므로 일반적인 서비스 트랜잭션에서는 트랜잭션 종료 과정에서 대상 `EntityManager`가 닫힌다. OSIV가 활성화된 애플리케이션에서는 요청 시작 때 연결된 대상 `EntityManager`가 요청 종료 시점에 닫힐 수 있다.

`EntityManagerFactory.createEntityManager()`로 대상 `EntityManager`를 직접 만들 수도 있지만, 그 경우 생성·트랜잭션·종료와 스레드 간 공유 방지를 개발자가 관리해야 한다. 일반적인 Spring 애플리케이션에서는 컨테이너가 관리하는 공유 프록시를 사용한다.

## 10. 요청이 겹쳐도 안전한 이유

컨트롤러, 서비스 프록시, 서비스 프록시가 참조하는 하나의 대상 객체, Repository 프록시와 공유 `EntityManager` 프록시는 여러 요청이 함께 사용할 수 있다.

```text
공유되는 객체
→ Controller
→ Service 프록시와 프록시가 참조하는 동일한 Service 대상 객체
→ Repository 프록시
→ 공유 EntityManager 프록시
```

반면 상태를 가진 작업 단위의 대상 객체는 분리된다.

```text
요청 A / Thread A / Transaction A
→ 대상 EntityManager A
→ 영속성 컨텍스트 A
→ JDBC Connection A

요청 B / Thread B / Transaction B
→ 대상 EntityManager B
→ 영속성 컨텍스트 B
→ JDBC Connection B
```

프록시는 현재 실행 문맥에 맞는 대상 자원을 선택한다. 대상 `EntityManager`는 스레드 안전하지 않으므로 여러 스레드에서 직접 공유하면 안 된다.

서비스 싱글톤의 개발자 정의 가변 필드도 여러 요청이 공유하므로 별도로 주의해야 한다.

```java
@Service
public class BadService {
    private String currentUser; // 요청들이 공유하므로 위험
}
```

## 11. 트랜잭션이 없을 때 EntityManager 프록시

`@Transactional`이 없는 메서드에서도 주입된 `EntityManager`는 계속 공유 프록시다. 프록시가 대상 객체로 교체되는 것이 아니다.

```text
@Transactional 있음
→ 트랜잭션 AOP와 JpaTransactionManager가 대상 EntityManager와 트랜잭션을 준비
→ 공유 프록시가 그 대상 EntityManager로 위임

@Transactional 없음
→ 공유 프록시는 그대로 존재
→ 현재 연결된 대상 EntityManager가 있는지 확인
```

바깥쪽 호출이 이미 트랜잭션을 시작했다면 어노테이션이 없는 안쪽 메서드도 같은 스레드에서 그 트랜잭션을 사용한다.

트랜잭션과 요청에 연결된 대상 `EntityManager`가 모두 없다면 조회 작업에서 임시 `EntityManager`를 사용할 수 있다. `persist()`, `remove()`, `flush()` 등 트랜잭션이 필요한 작업은 `TransactionRequiredException`이 발생할 수 있다.

## 12. 프록시를 구분하는 기준

세 프록시의 차이를 가장 짧게 정리하면 다음과 같다.

```text
Service AOP 프록시
→ 이 메서드를 호출하기 전후에 무엇을 할까?

Repository 프록시
→ 이 Repository 메서드를 어떤 구현으로 실행할까?

공유 EntityManager 프록시
→ 지금 어느 대상 EntityManager에게 보낼까?
```

조금 더 일반화하면 프록시의 용도는 다음과 같다.

```text
호출 앞뒤 부가 기능
→ 트랜잭션, 보안, 캐시, 비동기, 검증

메서드별 실행 전략 선택
→ Spring Data Repository, MyBatis Mapper, HTTP Client

현재 문맥의 대상 선택
→ EntityManager, 요청 스코프 빈

지연 실행
→ Hibernate lazy proxy, @Lazy 의존성
```

## 13. 흔한 오해 정리

### Service 프록시와 대상 객체가 각각 별도의 빈으로 등록된다

아니다. 일반적인 자동 프록시 생성에서는 `orderService`라는 BeanDefinition과 빈 이름, Singleton Registry 등록 항목이 각각 하나이며, 외부에는 프록시가 빈으로 노출된다. 프록시가 호출을 위임하기 위한 대상 객체까지 포함하면 실제 Java 객체는 두 개지만, 대상 객체가 별도의 이름을 가진 두 번째 빈으로 등록되는 것은 아니다.

### `@Service`이면 항상 프록시다

아니다. `@Service`는 빈 등록용 어노테이션이다. 트랜잭션 등 AOP 기능이 적용될 때 프록시가 만들어진다.

### `@Transactional`은 항상 새 트랜잭션을 만든다

아니다. 기본 `REQUIRED`는 기존 트랜잭션이 있으면 참여하고, 없을 때만 새로 만든다.

### `this.inner()`도 `inner()`의 `@Transactional`이 적용된다

일반적인 Spring 프록시 방식에서는 아니다. 대상 객체 내부 호출이므로 프록시를 우회한다.

### `@PersistenceContext`가 대상 EntityManager에 기능을 추가한다

아니다. 현재 영속성 컨텍스트에 접근할 수 있는 컨테이너 관리 `EntityManager` 참조를 주입하라는 JPA 표준 어노테이션이다.

### 영속성 컨텍스트는 EntityManager 프록시 안에 있다

아니다. 공유 프록시는 현재 대상을 찾는 역할이고, 영속성 컨텍스트는 대상 `EntityManager`와 연결된다.

### Repository 프록시는 트랜잭션만 처리한다

아니다. 인터페이스를 실행 가능한 객체로 구현하고 CRUD, 파생 쿼리, `@Query`, 사용자 구현을 적절한 실행기로 연결하는 것이 핵심 역할이다.

## 14. 코드에서 직접 확인하는 방법

빈의 런타임 클래스를 출력하면 프록시 여부를 확인할 수 있다.

```java
System.out.println(orderService.getClass().getName());
System.out.println(orderRepository.getClass().getName());
System.out.println(entityManager.getClass().getName());
```

환경과 프록시 방식에 따라 다음과 비슷한 이름을 볼 수 있다.

```text
OrderService$$SpringCGLIB$$...
jdk.proxy...$Proxy...
```

Spring AOP 프록시는 다음 유틸리티로도 확인할 수 있다.

```java
AopUtils.isAopProxy(orderService);
AopUtils.isJdkDynamicProxy(orderService);
AopUtils.isCglibProxy(orderService);
```

프록시 객체의 클래스 이름은 구현 세부사항이므로 비즈니스 로직에서 분기 조건으로 사용하지 않는다. 학습과 디버깅 목적으로만 확인한다.

## 15. 추천 학습 순서

1. 이 문서의 전체 흐름과 객체별 역할을 익힌다.
2. `OrderService.create()`에 중단점을 걸어 트랜잭션 안에서 호출되는지 확인한다.
3. `OrderRepository`의 런타임 클래스가 프록시인지 확인한다.
4. [JPA 참고 메모](java-jpa-notes.md)에서 `EntityManager` 기본 동작을 확인한다.
5. [OrderRepository.save()가 DB까지 도달하는 전체 코드 경로](spring-data-jpa-save-flow.md)에서 프레임워크 내부 구현을 따라간다.

마지막으로 전체 흐름을 한 문장으로 정리하면 다음과 같다.

> 컨트롤러가 서비스 프록시를 호출하면 트랜잭션 인프라가 트랜잭션을 준비하고 서비스 대상 객체를 실행한다. 서비스 대상 객체가 Repository 프록시를 호출하면 Repository 프록시가 CRUD 구현이나 쿼리 실행기를 선택한다. 그 구현이 공유 `EntityManager` 프록시를 호출하면 현재 트랜잭션의 대상 `EntityManager`로 연결되고, 대상 `EntityManager`가 영속성 컨텍스트와 Hibernate·JDBC를 통해 DB 작업을 수행한다.
