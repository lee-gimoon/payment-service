# JPA 참고 메모

정상 결제 흐름을 먼저 읽고, Spring이 객체를 만들어 주는 원리가 궁금할 때 보는 문서입니다.

## 저장소 인터페이스

`OrderRepository extends JpaRepository<PurchaseOrder, String>`는 주문 엔티티와 기본 키 타입을 지정합니다. `save()`, `findById()`의 공통 구현은 Spring Data JPA가 제공합니다.

1. Spring Boot가 저장소 기능을 자동 설정합니다.
2. Spring Data JPA가 저장소 인터페이스에 대한 `JpaRepositoryFactoryBean`을 등록합니다.
3. 이 객체가 `EntityManager`를 받아 `JpaRepositoryFactory`를 만듭니다.
4. 팩토리가 저장소 인터페이스로 호출할 수 있는 프록시를 만들고 Spring이 서비스에 주입합니다.

프록시는 실제 객체입니다. 호출받은 메서드를 공통 구현이나 쿼리 실행 코드로 연결합니다. 직접 구현 클래스를 만드는 방법도 가능하지만, Spring Data JPA를 사용하면 반복되는 구현을 작성하지 않아도 됩니다.

## EntityManager

JPA가 제공하는 인터페이스이며, Hibernate가 실제 동작을 구현합니다. 다음 코드는 Member 전용으로 단순화한 예시입니다.

```java
interface EntityManager {
    Member find(long id);           // 조회한 엔티티를 관리한다. 없으면 null.
    void persist(Member member);   // 새 엔티티를 관리 대상으로 등록한다.
    void remove(Member member);    // 관리 중인 엔티티를 삭제 대상으로 표시한다.
    void flush();                  // 변경 내용을 DB로 보낸다. 커밋과는 다르다.
    EntityTransaction getTransaction(); // 직접 트랜잭션을 관리할 때 사용하는 객체.
}

interface EntityTransaction {
    void begin();     // 시작
    void commit();    // 확정
    void rollback();  // 취소
}
```

실제 EntityManager에는 더 많은 메서드가 있으며, `find()`는 엔티티 클래스와 ID를 함께 받습니다. Hibernate는 필요한 SQL을 만들고 JDBC를 통해 DB와 통신합니다.

Spring이 주입하는 공유 EntityManager 프록시는 현재 트랜잭션에 연결된 실제 Hibernate 객체로 호출을 전달합니다. EntityManager 메서드마다 무조건 새 트랜잭션을 만드는 것은 아닙니다.

## 현재 구현에서의 트랜잭션

주문 서비스의 `@Transactional`은 주문 생성·조회 구간을 묶습니다. `PaymentService`와 `PaymentRecoveryService`에서는 저장소의 `saveAndFlush()`가 각 저장을 트랜잭션으로 수행합니다. 토스 HTTP 호출을 기다리는 동안에는 이 DB 트랜잭션이 끝나 있습니다.

이전 구현의 `findForUpdate()`와 비관적 쓰기 잠금은 현재 코드에서 제거했습니다. 결제의 기본 키·결제 키 유일성 제약과 JPA의 `@Version`으로 중복 저장과 오래된 결과의 덮어쓰기를 검사합니다.

자동 복구에서는 `claimRecovery()`로 다음 처리 시각을 5분 뒤로 옮기고 먼저 저장합니다. 같은 버전의 결제를 두 작업자가 읽어도 한 작업자만 이 저장에 성공하므로, 성공한 작업자만 토스에 조회를 보냅니다. 취소가 필요하면 취소 의도와 멱등키도 저장을 끝낸 뒤 외부 API를 호출합니다. 서버가 종료되면 임대 시간이 지난 작업을 다시 처리합니다. [자동 복구·취소의 저장 순서](payment-recovery.md)
