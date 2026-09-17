# Payment Service JPA 데이터베이스 파이프라인: 실제 소스 읽기 순서

이 문서는 다음 코드 한 줄이 별도의 구현 클래스 없이 어떻게 PostgreSQL까지 도달하는지 실제 소스 파일 순서로 추적한다.

```java
public interface OrderRepository extends JpaRepository<PurchaseOrder, String> {
}
```

추적할 요청은 다음과 같다.

```text
POST /orders
→ OrderController.create()
→ OrderService.create()
→ orderRepository.save(order)
→ PostgreSQL SELECT 및 INSERT
→ 트랜잭션 COMMIT
→ HTTP 201 Created
```

## 0. 먼저 알아야 할 기준

### 0.1 소스 저장소 기준 경로

이 문서에서 `<STACK>`은 다음 폴더를 뜻한다.

```text
<STACK> = C:\Users\yy123\OneDrive\바탕 화면\java-persistence-stack
```

현재 확인된 저장소는 다음과 같다.

```text
<STACK>\spring-boot
<STACK>\spring-data-jpa-main
<STACK>\spring-data-commons
<STACK>\spring-framework
<STACK>\persistence-main
<STACK>\hibernate-orm
<STACK>\jdk
<STACK>\HikariCP
<STACK>\pgjdbc
```

현재 Java 애플리케이션에서 드라이버까지의 소스는 모두 준비되어 있다. PostgreSQL 서버 내부까지 보려면 다음 저장소가 추가로 필요하다.

```text
<STACK>\postgres
```

- PostgreSQL 서버: https://github.com/postgres/postgres

`JpaRepository`의 실제 프록시 생성 공통 코드는 `spring-data-jpa`가 아니라 `spring-data-commons`에 있다. PostgreSQL JDBC 드라이버가 보낸 메시지를 받아 실제 행을 저장하는 코드는 `pgjdbc`가 아니라 PostgreSQL 서버 저장소에 있다.

줄 번호는 현재 클론을 확인한 결과다. 저장소를 업데이트하면 줄 번호는 바뀔 수 있으므로 줄 번호와 함께 클래스·메서드 이름을 기준으로 찾는다.

### 0.2 이 파이프라인의 정확한 의미

개념적으로는 다음처럼 압축할 수 있다.

```text
Spring Data JPA
→ JPA
→ Hibernate
→ JDBC
→ HikariCP
→ PostgreSQL JDBC Driver
→ PostgreSQL
```

하지만 실제 실행 구조는 일렬로 된 독립 단계가 아니다.

```text
Spring Data JPA
└─ Repository 인터페이스를 프록시와 SimpleJpaRepository에 연결

JPA
└─ EntityManager 등의 표준 인터페이스만 정의

Hibernate
└─ EntityManager의 실제 구현체 SessionImpl, SQL 생성과 영속성 컨텍스트 담당

JDBC
└─ DataSource, Connection, PreparedStatement 등의 Java 표준 인터페이스

HikariCP
└─ JDBC Connection과 PreparedStatement를 감싸는 풀 프록시

pgjdbc
└─ JDBC 인터페이스를 PostgreSQL 프로토콜로 구현한 실제 드라이버

PostgreSQL
└─ SQL을 파싱·계획·실행하고 데이터와 WAL을 저장하는 서버
```

Spring Boot는 매 요청마다 SQL을 전달하는 단계가 아니다. 애플리케이션 시작 시 위 구성 요소들을 찾아서 객체로 만들고 연결하는 역할을 한다. Spring Framework는 Bean 생성, MVC 요청 전달, AOP 프록시, 트랜잭션, 공유 `EntityManager`를 담당한다.

### 0.3 런타임의 실제 객체 관계

```text
OrderController
└─ orderService
   └─ OrderService$$SpringCGLIB$$...       ← Spring AOP 서비스 프록시
      └─ target: OrderService
         └─ orderRepository
            └─ jdk.proxy...$Proxy...       ← Spring Data Repository 프록시
               └─ CRUD target: SimpleJpaRepository
                  └─ entityManager
                     └─ JDK 공유 EntityManager 프록시
                        └─ 현재 트랜잭션의 SessionImpl
                           └─ JDBC Connection
                              └─ HikariProxyConnection
                                 └─ PgConnection
```

따라서 다음과 같은 소스 파일은 존재하지 않는다.

```text
OrderRepositoryImpl.java
OrderService$$SpringCGLIB$$....java
HikariProxyConnection.java
```

일부는 실행 시 생성되는 JDK/CGLIB 프록시이고, Hikari 프록시 클래스는 빌드 과정에서 생성된다. 대신 프록시를 만드는 코드와 프록시의 부모 구현을 읽는다.

---

# 1. 애플리케이션 시작 시 객체들이 만들어지는 과정

이 절은 아직 HTTP 요청이 들어오기 전이다. `OrderRepository` 인터페이스가 Bean으로 등록되고, 서비스 프록시·공유 `EntityManager`·Hibernate·Hikari가 서로 연결되는 과정을 본다.

## 1.1 프로젝트 시작점

파일:

```text
C:\JavaSpring\payment-service\src\main\java\com\example\payment\PaymentServiceApplication.java
```

볼 부분:

```text
8줄  : @SpringBootApplication
13줄 : SpringApplication.run(PaymentServiceApplication.class, args)
```

`SpringApplication.run()` 구현:

```text
<STACK>\spring-boot\core\spring-boot\src\main\java\
org\springframework\boot\SpringApplication.java
```

```text
304줄  : run(String... args)
1353줄 : static run(Class<?> primarySource, String... args)
```

여기서 Spring 컨테이너를 만들고 `refresh()`를 호출한다.

## 1.2 Spring Framework가 Bean 컨테이너를 초기화

파일:

```text
<STACK>\spring-framework\spring-context\src\main\java\
org\springframework\context\support\AbstractApplicationContext.java
```

```text
582줄 : refresh()
```

싱글톤 Bean을 미리 만드는 부분:

```text
<STACK>\spring-framework\spring-beans\src\main\java\
org\springframework\beans\factory\support\DefaultListableBeanFactory.java
```

```text
1102줄 : preInstantiateSingletons()
```

실제 Bean 인스턴스 생성 공통 코드:

```text
<STACK>\spring-framework\spring-beans\src\main\java\
org\springframework\beans\factory\support\AbstractAutowireCapableBeanFactory.java
```

```text
488줄 : createBean(String beanName, ...)
556줄 : doCreateBean(...)
```

이 코드는 JPA 전용이 아니라 모든 Spring Bean 생성에 사용하는 공통 기반이다.

## 1.3 Spring Boot 자동 설정 선택

파일:

```text
<STACK>\spring-boot\core\spring-boot-autoconfigure\src\main\java\
org\springframework\boot\autoconfigure\SpringBootApplication.java
```

```text
55줄 : @EnableAutoConfiguration
```

자동 설정 후보를 불러오는 파일:

```text
<STACK>\spring-boot\core\spring-boot-autoconfigure\src\main\java\
org\springframework\boot\autoconfigure\AutoConfigurationImportSelector.java
```

```text
78줄  : AutoConfigurationImportSelector 클래스
147줄 : getCandidateConfigurations(...) 호출
200줄 : getCandidateConfigurations(...)
```

여기서 클래스패스와 설정 조건을 검사하여 DataSource, Hibernate JPA, Spring Data JPA 자동 설정을 선택한다.

## 1.4 HikariDataSource 생성

파일:

```text
<STACK>\spring-boot\module\spring-boot-jdbc\src\main\java\
org\springframework\boot\jdbc\autoconfigure\DataSourceAutoConfiguration.java
```

```text
62줄 : DataSourceAutoConfiguration
79줄 : PooledDataSourceConfiguration
```

Hikari 전용 설정:

```text
<STACK>\spring-boot\module\spring-boot-jdbc\src\main\java\
org\springframework\boot\jdbc\autoconfigure\DataSourceConfiguration.java
```

```text
115줄 : Hikari 내부 설정 클래스
125줄 : HikariDataSource dataSource(...)
128줄 : createDataSource(..., HikariDataSource.class, ...)
```

결과:

```text
Bean 타입   : javax.sql.DataSource
실제 객체   : com.zaxxer.hikari.HikariDataSource
연결 정보   : application.yml의 spring.datasource.*
```

이 시점에 `HikariDataSource`가 준비된다. 실제 `PgConnection`은 풀이 물리 커넥션을 만들 때 생성되며, SQL마다 새로 생성되는 것은 아니다.

## 1.5 JPA EntityManagerFactory와 Hibernate SessionFactory 생성

Spring Boot의 Hibernate 자동 설정:

```text
<STACK>\spring-boot\module\spring-boot-hibernate\src\main\java\
org\springframework\boot\hibernate\autoconfigure\HibernateJpaAutoConfiguration.java
```

```text
51줄 : HibernateJpaAutoConfiguration
```

```text
<STACK>\spring-boot\module\spring-boot-hibernate\src\main\java\
org\springframework\boot\hibernate\autoconfigure\HibernateJpaConfiguration.java
```

```text
71줄 : HibernateJpaConfiguration extends JpaBaseConfiguration
```

JPA 공통 Bean 생성:

```text
<STACK>\spring-boot\module\spring-boot-jpa\src\main\java\
org\springframework\boot\jpa\autoconfigure\JpaBaseConfiguration.java
```

```text
101줄 : transactionManager(...)
125줄 : entityManagerFactoryBuilder(...)
156줄 : entityManagerFactory(...)
```

Spring Framework가 네이티브 `EntityManagerFactory`를 요청하는 지점:

```text
<STACK>\spring-framework\spring-orm\src\main\java\
org\springframework\orm\jpa\LocalContainerEntityManagerFactoryBean.java
```

```text
423줄 : createNativeEntityManagerFactory()
```

JPA 표준이 정의한 공급자 인터페이스를 통해 Hibernate로 넘어간다. Hibernate 구현:

```text
<STACK>\hibernate-orm\hibernate-core\src\main\java\
org\hibernate\jpa\HibernatePersistenceProvider.java
```

```text
57줄  : HibernatePersistenceProvider
171줄 : createContainerEntityManagerFactory(...) 부근
```

최종 주요 객체:

```text
JPA EntityManagerFactory 인터페이스
→ Hibernate SessionFactoryImpl 구현체
```

관련 Hibernate 파일:

```text
org\hibernate\SessionFactory.java
org\hibernate\internal\SessionFactoryImpl.java
```

`PurchaseOrder`의 `@Entity`, `@Table`, `@Id`, `@Column` 정보도 이 시작 과정에서 Hibernate 메타데이터에 등록된다.

## 1.6 트랜잭션 기능과 OrderService 프록시 생성

Spring Boot가 `JpaTransactionManager`를 Bean으로 만든다.

```text
JpaBaseConfiguration.java
101줄 : transactionManager(...)
```

Spring Framework의 트랜잭션 Advisor와 Interceptor:

```text
<STACK>\spring-framework\spring-tx\src\main\java\
org\springframework\transaction\annotation\ProxyTransactionManagementConfiguration.java
```

```text
46줄 : transactionAdvisor(...)
60줄 : transactionInterceptor(...)
```

`@Transactional`을 찾는 객체:

```text
<STACK>\spring-framework\spring-tx\src\main\java\
org\springframework\transaction\annotation\AnnotationTransactionAttributeSource.java
```

```text
60줄 : AnnotationTransactionAttributeSource
```

Spring AOP가 `OrderService`를 프록시로 감싸는 공통 코드:

```text
<STACK>\spring-framework\spring-aop\src\main\java\
org\springframework\aop\framework\autoproxy\AbstractAutoProxyCreator.java
```

```text
285줄 : postProcessAfterInitialization(...)
321줄 : wrapIfNecessary(...)
428줄 : createProxy(...)
```

어떤 종류의 프록시를 만들지 결정:

```text
<STACK>\spring-framework\spring-aop\src\main\java\
org\springframework\aop\framework\DefaultAopProxyFactory.java
```

```text
60줄 : createAopProxy(...)
```

`OrderService`에는 구현 인터페이스가 없으므로 클래스 기반 CGLIB 프록시가 사용된다.

```text
주입되는 객체 : OrderService$$SpringCGLIB$$...
실제 대상     : OrderService
적용 Advice   : TransactionInterceptor
```

## 1.7 Spring Data JPA Repository 기능 활성화

Spring Boot 자동 설정:

```text
<STACK>\spring-boot\module\spring-boot-data-jpa\src\main\java\
org\springframework\boot\data\jpa\autoconfigure\DataJpaRepositoriesAutoConfiguration.java
```

```text
77줄  : DataJpaRepositoriesAutoConfiguration
104줄 : DataJpaRepositoriesRegistrar 선택
```

```text
<STACK>\spring-boot\module\spring-boot-data-jpa\src\main\java\
org\springframework\boot\data\jpa\autoconfigure\DataJpaRepositoriesRegistrar.java
```

```text
41줄 : DataJpaRepositoriesRegistrar
56줄 : getRepositoryConfigurationExtension()
```

Spring Data JPA 설정 확장:

```text
<STACK>\spring-data-jpa-main\spring-data-jpa\src\main\java\
org\springframework\data\jpa\repository\config\JpaRepositoryConfigExtension.java
```

```text
104줄 : JpaRepositoryConfigExtension
121줄 : getRepositoryBaseClassName()
126줄 : getRepositoryFactoryBeanClassName()
```

여기서 다음 두 클래스를 선택한다.

```text
Repository 기본 CRUD 구현 : SimpleJpaRepository
Repository 생성 FactoryBean: JpaRepositoryFactoryBean
```

## 1.8 OrderRepository 인터페이스 검색과 BeanDefinition 등록

이 공통 검색 코드는 `spring-data-commons` 저장소에 있다. 다음 파일을 순서대로 읽는다.

```text
<STACK>\spring-data-commons\src\main\java\
org\springframework\data\repository\config\
├─ RepositoryBeanDefinitionRegistrarSupport.java
├─ RepositoryConfigurationDelegate.java
├─ RepositoryConfigurationSourceSupport.java
├─ RepositoryComponentProvider.java
└─ RepositoryBeanDefinitionBuilder.java
```

메서드 순서:

```text
RepositoryBeanDefinitionRegistrarSupport.java
69/74줄 : registerBeanDefinitions(...)
93줄    : RepositoryConfigurationDelegate 생성
96줄    : delegate.registerRepositoriesIn(...) 호출

→ RepositoryConfigurationDelegate.java
141줄 : registerRepositoriesIn(...)
181줄 : RepositoryBeanDefinitionBuilder.build(...) 호출

→ RepositoryConfigurationSourceSupport.java
66줄 : getCandidates(...)
76줄 : scanner.findCandidateComponents(...) 호출

→ RepositoryComponentProvider.java
119줄 : findCandidateComponents(...)
121줄 : 실제 클래스패스 스캔

→ OrderRepository 발견

→ RepositoryBeanDefinitionBuilder.java
110줄 : build(...)
162줄 : buildMetadata(...)

→ JpaRepositoryFactoryBean용 BeanDefinition 등록
```

프로젝트 파일:

```text
C:\JavaSpring\payment-service\src\main\java\com\example\payment\order\OrderRepository.java
```

```text
6줄 : interface OrderRepository extends JpaRepository<PurchaseOrder, String>
```

상속 구조:

```text
OrderRepository
→ JpaRepository
→ ListCrudRepository
→ CrudRepository
→ Repository
```

`JpaRepository` 파일:

```text
<STACK>\spring-data-jpa-main\spring-data-jpa\src\main\java\
org\springframework\data\jpa\repository\JpaRepository.java
```

```text
41줄 : JpaRepository 인터페이스
```

상위 인터페이스를 실제 파일에서 다음 순서로 확인한다.

```text
<STACK>\spring-data-commons\src\main\java\
org\springframework\data\repository\ListCrudRepository.java
31줄 : ListCrudRepository extends CrudRepository
```

```text
<STACK>\spring-data-commons\src\main\java\
org\springframework\data\repository\CrudRepository.java
33줄 : CrudRepository extends Repository
46줄 : <S extends T> S save(S entity)
```

```text
<STACK>\spring-data-commons\src\main\java\
org\springframework\data\repository\Repository.java
34줄 : 최상위 marker interface Repository<T, ID>
```

따라서 프로젝트에서 호출하는 `save()`의 선언은 `JpaRepository.java`가 아니라 `CrudRepository.java` 46줄에 있다.

## 1.9 공유 EntityManager 프록시 준비

Spring Data JPA는 Repository마다 실제 `SessionImpl`을 고정해서 넣지 않는다. 트랜잭션마다 달라지는 실제 `EntityManager`를 찾아주는 공유 프록시를 넣는다.

공유 `EntityManager` BeanDefinition 등록:

```text
<STACK>\spring-data-jpa-main\spring-data-jpa\src\main\java\
org\springframework\data\jpa\repository\config\JpaRepositoryConfigExtension.java
```

```text
201줄 : registerBeansForRoot(...)
246줄 : EntityManager Bean 참조 결정
254줄 : jpaSharedEM_... Bean 이름 생성
324줄 : SharedEntityManagerCreator BeanDefinition
325줄 : createSharedEntityManager 팩터리 메서드 지정
```

실제 프록시 생성:

```text
<STACK>\spring-framework\spring-orm\src\main\java\
org\springframework\orm\jpa\SharedEntityManagerCreator.java
```

```text
104~165줄 : createSharedEntityManager(...) 오버로드
302줄     : SharedEntityManagerInvocationHandler
317줄     : invoke(...)
```

이 프록시는 요청마다 현재 스레드에 연결된 실제 Hibernate `SessionImpl`을 찾아 호출을 위임한다.

## 1.10 SimpleJpaRepository 대상 객체와 OrderRepository 프록시 생성

FactoryBean:

```text
<STACK>\spring-data-jpa-main\spring-data-jpa\src\main\java\
org\springframework\data\jpa\repository\support\JpaRepositoryFactoryBean.java
```

```text
52줄  : JpaRepositoryFactoryBean
78줄  : setEntityManager(...)
179줄 : doCreateRepositoryFactory()
208줄 : afterPropertiesSet()
```

Repository Factory:

```text
<STACK>\spring-data-jpa-main\spring-data-jpa\src\main\java\
org\springframework\data\jpa\repository\support\JpaRepositoryFactory.java
```

```text
71줄  : JpaRepositoryFactory
210줄 : getTargetRepository(...)
226줄 : getTargetRepository(..., EntityManager)
230줄 부근 : SimpleJpaRepository 대상 객체 생성
238줄 : getRepositoryBaseClass(...)
```

프록시 조립 공통 코드는 `spring-data-commons`의 다음 파일이다.

```text
<STACK>\spring-data-commons\src\main\java\
org\springframework\data\repository\core\support\RepositoryFactoryBeanSupport.java
```

```text
297줄 : getObject()
324줄 : afterPropertiesSet()
```

`afterPropertiesSet()`에서 Repository Factory 초기화를 끝낸 뒤 필요할 때 Repository 프록시를 가져올 준비를 한다.

실제 대상과 프록시 조립:

```text
<STACK>\spring-data-commons\src\main\java\
org\springframework\data\repository\core\support\RepositoryFactorySupport.java
```

```text
282줄      : getRepository(Class<T>)
308줄      : getRepository(Class<T>, RepositoryFragments)
359줄 부근 : 실제 Repository target 생성
369줄      : ProxyFactory 생성
370줄      : setTarget(...)
371줄      : setInterfaces(...)
412줄      : QueryExecutorMethodInterceptor 등록
416줄      : ImplementationMethodExecutionInterceptor 등록
419줄      : getProxy(...)
```

최종 결과:

```text
Bean 이름      : orderRepository
Bean 공개 타입 : OrderRepository
실제 Bean      : JDK 동적 프록시
CRUD 대상      : SimpleJpaRepository<PurchaseOrder, String>
EntityManager  : SharedEntityManagerCreator가 만든 JDK 프록시
```

## 1.11 시작 과정 최종 요약

```text
PaymentServiceApplication.main()
→ SpringApplication.run()
→ AbstractApplicationContext.refresh()
→ Spring Boot 자동 설정 선택

→ DataSourceConfiguration.Hikari
→ HikariDataSource Bean 생성

→ HibernateJpaConfiguration
→ LocalContainerEntityManagerFactoryBean
→ HibernatePersistenceProvider
→ SessionFactoryImpl 생성

→ JpaBaseConfiguration.transactionManager()
→ JpaTransactionManager 생성

→ AbstractAutoProxyCreator
→ OrderService CGLIB 트랜잭션 프록시 생성

→ DataJpaRepositoriesAutoConfiguration
→ DataJpaRepositoriesRegistrar
→ RepositoryConfigurationDelegate
→ OrderRepository 발견
→ JpaRepositoryFactoryBean 등록
→ 공유 EntityManager 프록시 생성
→ SimpleJpaRepository 대상 생성
→ OrderRepository JDK 프록시 생성

→ Controller에 OrderService 프록시 주입
→ 실제 OrderService에 OrderRepository 프록시 주입
```

---

# 2. `POST /orders` 요청이 Repository까지 도달하는 과정

## 2.1 Spring MVC가 Controller를 호출

HTTP 요청이 Servlet 컨테이너에서 Spring MVC로 전달된 뒤 다음 경로를 지난다.

```text
DispatcherServlet.doDispatch()
→ RequestMappingHandlerAdapter.handleInternal()
→ RequestMappingHandlerAdapter.invokeHandlerMethod()
→ ServletInvocableHandlerMethod.invokeAndHandle()
→ InvocableHandlerMethod.doInvoke()
→ OrderController.create()
```

`DispatcherServlet`:

```text
<STACK>\spring-framework\spring-webmvc\src\main\java\
org\springframework\web\servlet\DispatcherServlet.java
```

```text
866줄 : doDispatch(...) 호출
935줄 : doDispatch(...)
```

HandlerAdapter:

```text
<STACK>\spring-framework\spring-webmvc\src\main\java\
org\springframework\web\servlet\mvc\method\annotation\RequestMappingHandlerAdapter.java
```

```text
835줄 : handleInternal(...)
847~857줄 : invokeHandlerMethod(...) 호출
889줄 : invokeHandlerMethod(...)
```

Controller 메서드 호출:

```text
<STACK>\spring-framework\spring-webmvc\src\main\java\
org\springframework\web\servlet\mvc\method\annotation\ServletInvocableHandlerMethod.java
```

```text
114줄 : invokeAndHandle(...)
```

```text
<STACK>\spring-framework\spring-web\src\main\java\
org\springframework\web\method\support\InvocableHandlerMethod.java
```

```text
184줄 : doInvoke(args) 호출
243줄 : doInvoke(...)
```

프로젝트 Controller:

```text
C:\JavaSpring\payment-service\src\main\java\com\example\payment\order\OrderController.java
```

```text
23줄 : @PostMapping("/orders")
25줄 : create()
26줄 : orderService.create()
```

Tomcat 소켓 수신부터 `DispatcherServlet` 이전까지도 보려면 별도로 `apache/tomcat` 저장소가 필요하다. 이 문서는 Spring MVC 진입점부터 추적한다.

## 2.2 OrderService CGLIB 프록시가 트랜잭션을 시작

Controller가 가진 `orderService`는 실제 `OrderService`가 아니라 시작 과정에서 만든 CGLIB 프록시다.

```text
OrderController
→ OrderService$$SpringCGLIB$$...
→ TransactionInterceptor
→ 실제 OrderService
```

CGLIB 프록시 호출 처리:

```text
<STACK>\spring-framework\spring-aop\src\main\java\
org\springframework\aop\framework\CglibAopProxy.java
```

```text
706줄 : DynamicAdvisedInterceptor
715줄 : intercept(...)
```

트랜잭션 Advice:

```text
<STACK>\spring-framework\spring-tx\src\main\java\
org\springframework\transaction\interceptor\TransactionInterceptor.java
```

```text
123줄 : invoke(...)
```

```text
<STACK>\spring-framework\spring-tx\src\main\java\
org\springframework\transaction\interceptor\TransactionAspectSupport.java
```

```text
333줄 : invokeWithinTransaction(...)
408줄 : 정상 반환 후 commitTransactionAfterReturning(...) 호출
682줄 : commitTransactionAfterReturning(...)
```

JPA 트랜잭션 시작:

```text
<STACK>\spring-framework\spring-orm\src\main\java\
org\springframework\orm\jpa\JpaTransactionManager.java
```

```text
386줄 : doBegin(...)
400줄 : createEntityManagerForTransaction() 호출
407줄 : 실제 EntityManager 획득
445줄 : EntityManagerHolder를 현재 스레드에 bindResource(...)
470줄 : createEntityManagerForTransaction()
```

실제 `EntityManager`는 Hibernate의 `SessionImpl`이다. JDBC Connection은 연결 처리 방식에 따라 이 시점 또는 첫 SQL 실행 시점에 지연 획득될 수 있다.

## 2.3 실제 OrderService 코드 실행

파일:

```text
C:\JavaSpring\payment-service\src\main\java\com\example\payment\order\OrderService.java
```

```text
22줄 : @Transactional
23줄 : create()
24줄 : new PurchaseOrder("티셔츠", 1, 10000)
25줄 : orderRepository.save(order)
```

`PurchaseOrder` 생성자:

```text
C:\JavaSpring\payment-service\src\main\java\com\example\payment\order\PurchaseOrder.java
```

```text
11줄 : @Entity
12줄 : @Table(name = "purchase_orders")
14줄 : @Id
34줄 : 새 주문 생성자
35줄 : UUID.randomUUID().toString()으로 ID 선할당
```

ID가 `save()` 전에 이미 들어간다는 점이 이후 `persist()`/`merge()` 분기를 바꾼다.

---

# 3. OrderRepository 프록시에서 SimpleJpaRepository까지

## 3.1 JDK 동적 프록시 진입

`orderRepository.save(order)`를 호출하면 JDK 동적 프록시의 InvocationHandler로 들어간다.

```text
<STACK>\spring-framework\spring-aop\src\main\java\
org\springframework\aop\framework\JdkDynamicAopProxy.java
```

```text
166줄 : invoke(...)
```

Repository 호출의 핵심 인터셉터 순서:

```text
JdkDynamicAopProxy.invoke()
→ ReflectiveMethodInvocation.proceed()
→ TransactionInterceptor.invoke()
→ QueryExecutorMethodInterceptor.invoke()
→ ImplementationMethodExecutionInterceptor.invoke()
→ RepositoryComposition.invoke()
→ RepositoryMethodInvoker.invoke()
→ SimpleJpaRepository.save()
```

Repository 자체에도 트랜잭션 Advice가 있지만 외부 `OrderService.create()` 트랜잭션이 이미 존재하므로 기본 전파 규칙 `REQUIRED`에 따라 기존 트랜잭션에 참여한다.

## 3.2 Spring Data Commons 메서드 선택

다음 파일들을 현재 클론한 `spring-data-commons`에서 순서대로 본다.

```text
<STACK>\spring-data-commons\src\main\java\
org\springframework\data\repository\core\support\
├─ QueryExecutorMethodInterceptor.java
├─ RepositoryFactorySupport.java
├─ RepositoryComposition.java
└─ RepositoryMethodInvoker.java
```

볼 순서:

```text
QueryExecutorMethodInterceptor.java
137줄 : invoke(...)
154줄 : doInvoke(...)
→ save()는 파생 쿼리 메서드가 아니므로 proceed()

RepositoryFactorySupport.java
671줄 : ImplementationMethodExecutionInterceptor 클래스
684줄 : invoke(...)

→ RepositoryComposition.java
272/280줄 : invoke(...)
→ CrudRepository.save()와 호환되는 구현 메서드 검색

→ RepositoryMethodInvoker.java
156줄 : invoke(...)
161줄 : doInvoke(...)

→ SimpleJpaRepository.save() 리플렉션 호출
```

## 3.3 실제 CRUD 구현

파일:

```text
<STACK>\spring-data-jpa-main\spring-data-jpa\src\main\java\
org\springframework\data\jpa\repository\support\SimpleJpaRepository.java
```

```text
111줄 : SimpleJpaRepository 클래스
659줄 : save(...)
663~667줄 : isNew 결과에 따른 persist/merge 분기
```

핵심 코드:

```java
if (entityInformation.isNew(entity)) {
    entityManager.persist(entity);
    return entity;
}
else {
    return entityManager.merge(entity);
}
```

---

# 4. 이 프로젝트가 `persist()`가 아니라 `merge()`로 가는 이유

## 4.1 새 엔티티 판정

파일:

```text
<STACK>\spring-data-jpa-main\spring-data-jpa\src\main\java\
org\springframework\data\jpa\repository\support\JpaMetamodelEntityInformation.java
```

```text
254줄 : isNew(...)
```

사용 가능한 `@Version` 필드가 없으면 Spring Data Commons의 기본 ID 검사로 이동한다.

```text
<STACK>\spring-data-commons\src\main\java\
org\springframework\data\repository\core\support\AbstractEntityInformation.java
```

```text
43줄 : isNew(...)
→ 객체 타입 ID가 null이면 true
→ 객체 타입 ID가 null이 아니면 false
```

이 프로젝트의 실제 판정:

```text
new PurchaseOrder(...)
→ 생성자에서 UUID 문자열 할당
→ id != null
→ @Version 필드 없음
→ Persistable 구현 없음
→ isNew(order) == false
→ entityManager.merge(order)
```

일반적인 “새 엔티티 save는 무조건 persist” 설명과 이 프로젝트의 실제 실행 경로가 다른 이유다.

`merge()`는 전달한 객체 자체가 아닌 영속 상태의 복사본을 반환할 수 있다. 따라서 다음 재대입이 중요하다.

```java
order = orderRepository.save(order);
```

---

# 5. 공유 EntityManager 프록시에서 Hibernate SessionImpl까지

## 5.1 JPA는 실행 코드가 아니라 표준 인터페이스

파일:

```text
<STACK>\persistence-main\api\src\main\java\jakarta\persistence\EntityManager.java
```

```text
219줄 : EntityManager 인터페이스
246줄 : persist(...)
278줄 : merge(...)
```

이 파일에는 Hibernate SQL 실행 코드가 없다. `EntityManager`를 구현한 객체에 대한 계약만 정의한다.

## 5.2 공유 EntityManager가 현재 트랜잭션 객체를 찾음

파일:

```text
<STACK>\spring-framework\spring-orm\src\main\java\
org\springframework\orm\jpa\SharedEntityManagerCreator.java
```

```text
317줄 : SharedEntityManagerInvocationHandler.invoke(...)
368줄 : 현재 EntityManager 검색 시작
370줄 : EntityManagerFactoryUtils.doGetTransactionalEntityManager(...)
413줄 : 실제 대상 EntityManager에 메서드 호출
```

현재 스레드에서 `EntityManagerHolder`를 찾는 파일:

```text
<STACK>\spring-framework\spring-orm\src\main\java\
org\springframework\orm\jpa\EntityManagerFactoryUtils.java
```

```text
181줄 : doGetTransactionalEntityManager(...) 오버로드
201줄 : 실제 doGetTransactionalEntityManager(...)
```

흐름:

```text
SimpleJpaRepository.entityManager.merge(order)
→ SharedEntityManagerInvocationHandler.invoke()
→ TransactionSynchronizationManager에서 EntityManagerHolder 검색
→ JpaTransactionManager가 바인딩한 SessionImpl 획득
→ SessionImpl.merge(order) 호출
```

## 5.3 Hibernate 구현체

Hibernate의 `Session`은 JPA `EntityManager`를 상속한다.

```text
<STACK>\hibernate-orm\hibernate-core\src\main\java\org\hibernate\Session.java
```

```text
192줄 : Session 인터페이스
```

실제 구현:

```text
<STACK>\hibernate-orm\hibernate-core\src\main\java\
org\hibernate\internal\SessionImpl.java
```

```text
160줄 : SessionImpl
805줄 : merge(Object)
816줄 부근 : fireMerge(...)
```

---

# 6. Hibernate `merge()`가 SELECT 후 INSERT를 예약하는 과정

## 6.1 Merge 이벤트 처리

파일:

```text
<STACK>\hibernate-orm\hibernate-core\src\main\java\
org\hibernate\event\internal\DefaultMergeEventListener.java
```

```text
76줄  : onMerge(...)
98줄  : onMerge(..., MergeContext)
196줄 부근 : 엔티티 상태별 분기
280줄 : entityIsTransient(...)
442줄 : entityIsDetached(...)
469줄 : session.find(entityName, id)
487줄 : 조회 결과가 없으면 entityIsTransient(...) 호출
425줄 부근 : saveTransientEntity(...)
```

이 프로젝트의 의미:

```text
ID가 이미 있음
→ Hibernate가 기존 행일 가능성을 확인
→ 같은 ID로 SELECT
→ DB에 행이 없음
→ 새 엔티티로 다시 판단
→ 영속 복사본 생성
→ INSERT 작업을 ActionQueue에 등록
```

`save()` 호출 즉시 최종 INSERT가 반드시 실행되는 것은 아니다. 일반적인 경우 INSERT 작업을 등록해 두고 트랜잭션 커밋 전 flush에서 실행한다.

## 6.2 `merge()` 중 ID 조회 SELECT

현재 Hibernate 체크아웃의 핵심 경로:

```text
DefaultMergeEventListener.entityIsDetached()
→ SharedSessionContract.find()
→ AbstractSharedSessionContract.find()
→ StatefulFindByKeyOperation.findById()
→ DefaultLoadEventListener.onLoad()
→ DefaultLoadEventListener.loadFromDatasource()
→ AbstractEntityPersister.load()
→ SingleIdEntityLoaderStandardImpl.load()
→ SingleIdLoadPlan.load()
→ JdbcSelectExecutorStandardImpl.executeQuery()
→ DeferredResultSetAccess.executeQuery()
→ PreparedStatement.executeQuery()
```

확인할 파일과 위치:

```text
org\hibernate\internal\AbstractSharedSessionContract.java
588줄 : find(String entityName, ...)
```

```text
org\hibernate\internal\find\StatefulFindByKeyOperation.java
87줄 부근 : findById(...)
91줄      : loadAccessContext.load(...)
```

```text
org\hibernate\event\internal\DefaultLoadEventListener.java
53줄  : onLoad(...)
634줄 : loadFromDatasource(...)
640줄 : persister.load(...)
```

```text
org\hibernate\persister\entity\AbstractEntityPersister.java
4281줄 이후 : load(...) 오버로드
```

```text
org\hibernate\loader\ast\internal\SingleIdEntityLoaderStandardImpl.java
62줄 : load(...)
```

```text
org\hibernate\loader\ast\internal\SingleIdLoadPlan.java
143줄 : getJdbcSelectExecutor().list(...)
```

```text
org\hibernate\sql\exec\internal\JdbcSelectExecutorStandardImpl.java
62줄 : executeQuery(...)
```

```text
org\hibernate\sql\results\jdbc\internal\DeferredResultSetAccess.java
190줄 : 내부 executeQuery()
212줄 : preparedStatement.executeQuery()
```

212줄에서 Hibernate가 JDBC `PreparedStatement`를 호출한다. 이 SELECT에서 최초 DB 왕복이 발생한다.

---

# 7. Service 반환 후 커밋과 Hibernate flush

Repository와 실제 Service 메서드가 정상 반환되면 트랜잭션 Advice가 커밋을 시작한다.

```text
TransactionAspectSupport.commitTransactionAfterReturning()
→ JpaTransactionManager.doCommit()
→ JPA EntityTransaction.commit()
→ Hibernate TransactionImpl.commit()
→ SessionImpl.flushBeforeTransactionCompletion()
→ DefaultFlushEventListener.onFlush()
→ AbstractFlushingEventListener.performExecutions()
→ ActionQueue.executeActions()
```

Spring Framework:

```text
<STACK>\spring-framework\spring-orm\src\main\java\
org\springframework\orm\jpa\JpaTransactionManager.java
```

```text
544줄 : doCommit(...)
551줄 : EntityTransaction 획득
```

JPA 표준:

```text
<STACK>\persistence-main\api\src\main\java\jakarta\persistence\EntityTransaction.java
```

```text
29줄 : EntityTransaction 인터페이스
35줄 : begin()
43줄 : commit()
```

Hibernate:

```text
org\hibernate\engine\transaction\internal\TransactionImpl.java
```

```text
org\hibernate\internal\SessionImpl.java
486줄  : managedFlush()
2113줄 : flushBeforeTransactionCompletion()
```

```text
org\hibernate\event\internal\DefaultFlushEventListener.java
25줄 : onFlush(...)
```

```text
org\hibernate\event\internal\AbstractFlushingEventListener.java
367줄 : performExecutions(...)
```

현재 Hibernate 체크아웃의 ActionQueue 구현:

```text
org\hibernate\action\queue\internal\GraphBasedActionQueue.java
469줄 : executeActions()
```

---

# 8. INSERT SQL 생성, 값 바인딩, JDBC 호출

## 8.1 INSERT 작업 실행

```text
<STACK>\hibernate-orm\hibernate-core\src\main\java\
org\hibernate\action\internal\EntityInsertAction.java
```

```text
101줄 : execute()
118줄 부근 : InsertCoordinator 호출
```

```text
org\hibernate\persister\entity\mutation\InsertCoordinatorStandard.java
```

```text
85, 90줄 : insert(...) 계열
109줄    : coordinateInsert(...)
499줄    : generateStaticOperationGroup()
```

SQL INSERT 구조 생성:

```text
org\hibernate\sql\ast\spi\model\builder\TableInsertBuilderStandard.java
42줄 : buildMutation()
```

SQL은 대략 다음 형태가 된다. 정확한 컬럼 순서는 Hibernate 로그에서 확인한다.

```sql
insert into purchase_orders
    (amount, created_at, product_name, quantity, id)
values
    (?, ?, ?, ?, ?)
```

## 8.2 엔티티 값을 `?` 파라미터에 바인딩

```text
org\hibernate\engine\jdbc\mutation\internal\JdbcValueBindingsImpl.java
```

```text
54줄  : bindValue(...)
86줄  : beforeStatement(...)
102줄 : ValueBinder.bind(...) 호출
```

```text
org\hibernate\type\descriptor\jdbc\BasicBinder.java
```

```text
49줄 : bind(PreparedStatement, value, index, ...)
```

## 8.3 JDBC PreparedStatement 생성과 실행

```text
org\hibernate\engine\jdbc\internal\StatementPreparerImpl.java
```

```text
77줄 : prepareStatement(String sql)
99줄 : connection().prepareStatement(sql)
```

Mutation 실행:

```text
org\hibernate\engine\jdbc\mutation\internal\AbstractMutationExecutor.java
```

```text
48, 58줄 : execute(...)
103줄    : performNonBatchedMutation(...)
```

```text
org\hibernate\engine\jdbc\mutation\internal\MutationExecutorSingleNonBatched.java
38줄 : performNonBatchedOperations(...)
```

최종 Hibernate/JDBC 경계:

```text
org\hibernate\engine\jdbc\internal\ResultSetReturnImpl.java
```

```text
183줄 : executeUpdate(PreparedStatement, String)
190줄 : statement.executeUpdate()
```

190줄에서 Hibernate 코드가 JDBC 객체에 제어권을 넘긴다.

---

# 9. JDBC 인터페이스와 실제 Hikari·pgjdbc 객체

## 9.1 JDBC 표준 인터페이스

DataSource:

```text
<STACK>\jdk\src\java.sql\share\classes\javax\sql\DataSource.java
```

```text
79줄 : DataSource 인터페이스
92줄 : getConnection()
```

Connection:

```text
<STACK>\jdk\src\java.sql\share\classes\java\sql\Connection.java
```

```text
86줄  : Connection 인터페이스
140줄 : prepareStatement(String sql)
250줄 : commit()
280줄 : close()
```

PreparedStatement:

```text
<STACK>\jdk\src\java.sql\share\classes\java\sql\PreparedStatement.java
```

```text
63줄 : PreparedStatement 인터페이스
79줄 : executeQuery()
97줄 : executeUpdate()
```

이 파일들은 DB 통신 구현이 아니라 표준 메서드 계약이다.

## 9.2 Hibernate가 Hikari에서 Connection을 빌림

Hibernate DataSource 경계:

```text
<STACK>\hibernate-orm\hibernate-core\src\main\java\
org\hibernate\engine\jdbc\connections\internal\DataSourceConnectionProvider.java
```

```text
146줄 : getConnection()
150줄 : dataSource.getConnection()
```

실제 DataSource는 Hikari다.

```text
<STACK>\HikariCP\src\main\java\com\zaxxer\hikari\HikariDataSource.java
```

```text
40줄 : HikariDataSource implements DataSource
92줄 : getConnection()
99줄 : pool.getConnection()
```

```text
<STACK>\HikariCP\src\main\java\com\zaxxer\hikari\pool\HikariPool.java
```

```text
152줄 : getConnection(long hardTimeout)
160줄 : connectionBag.borrow(...)
179줄 부근 : PoolEntry의 프록시 Connection 반환
```

실제 객체:

```text
java.sql.Connection
→ HikariProxyConnection
→ ProxyConnection.delegate
→ org.postgresql.jdbc.PgConnection
```

## 9.3 Hikari 프록시가 생성되는 코드

프록시 부모 구현:

```text
<STACK>\HikariCP\src\main\java\com\zaxxer\hikari\pool\ProxyConnection.java
```

```text
240줄 : close()
326줄 : prepareStatement(String sql)
328줄 : 실제 delegate.prepareStatement(sql) 후 Hikari 프록시로 감쌈
376줄 : commit()
```

프록시 팩토리:

```text
<STACK>\HikariCP\src\main\java\com\zaxxer\hikari\pool\ProxyFactory.java
```

```text
46줄 : getProxyConnection(...)
64줄 : getProxyPreparedStatement(...)
```

빌드 시 `HikariProxyConnection`, `HikariProxyPreparedStatement` 등을 생성하는 코드:

```text
<STACK>\HikariCP\src\main\java\com\zaxxer\hikari\util\JavassistProxyFactory.java
```

```text
42줄 : JavassistProxyFactory
64줄 : Connection 프록시 클래스 생성
71줄 : PreparedStatement 프록시 클래스 생성
84줄 : HikariProxyConnection 생성 코드
90줄 : HikariProxyPreparedStatement 생성 코드
114줄 : generateProxyClass(...)
```

## 9.4 Hikari PreparedStatement에서 pgjdbc로 위임

```text
<STACK>\HikariCP\src\main\java\com\zaxxer\hikari\pool\ProxyPreparedStatement.java
```

```text
58줄 : executeUpdate()
61줄 부근 : delegate.executeUpdate()
```

여기서 `delegate`는 pgjdbc의 `PgPreparedStatement`다.

---

# 10. PostgreSQL JDBC Driver가 SQL을 프로토콜 메시지로 전송

## 10.1 물리 Connection 생성

Hikari가 풀에 새 물리 커넥션을 추가할 때만 다음 경로가 실행된다.

```text
Hikari DriverDataSource.getConnection()
→ org.postgresql.Driver.connect()
→ ConnectionFactory.openConnection()
→ ConnectionFactoryImpl.openConnectionImpl()
→ PGStream 생성 및 socket.connect()
→ PgConnection 생성
```

Hikari:

```text
<STACK>\HikariCP\src\main\java\com\zaxxer\hikari\util\DriverDataSource.java
```

```text
125줄 : getConnection()
127줄 : driver.connect(...)
144줄 : 사용자명·비밀번호를 포함한 driver.connect(...)
```

pgjdbc Driver:

```text
<STACK>\pgjdbc\pgjdbc\src\main\java\org\postgresql\Driver.java
```

```text
70줄  : Driver implements java.sql.Driver
254줄 : connect(...)
```

```text
<STACK>\pgjdbc\pgjdbc\src\main\java\org\postgresql\core\ConnectionFactory.java
43줄 : openConnection(...)
```

```text
<STACK>\pgjdbc\pgjdbc\src\main\java\org\postgresql\core\v3\ConnectionFactoryImpl.java
221줄 : new PGStream(...)
330줄 부근 : openConnectionImpl(...)
```

소켓 생성:

```text
<STACK>\pgjdbc\pgjdbc\src\main\java\org\postgresql\core\PGStream.java
```

```text
194줄 : PGStream 생성자
200줄 : createSocket(...)
324줄 : 실제 createSocket(int timeout)
339줄 : socket.connect(...)
```

이 경로는 매 SQL마다 실행되지 않는다. 이후 요청은 Hikari가 보관한 기존 `PgConnection`을 빌려 쓴다.

## 10.2 SQL PreparedStatement 생성

```text
<STACK>\pgjdbc\pgjdbc\src\main\java\org\postgresql\jdbc\PgConnection.java
```

```text
106줄  : PgConnection implements BaseConnection
575줄  : prepareStatement(String sql)
1035줄 : commit()
```

실제 Statement:

```text
<STACK>\pgjdbc\pgjdbc\src\main\java\org\postgresql\jdbc\PgPreparedStatement.java
```

```text
85줄  : PgPreparedStatement implements PreparedStatement
138줄 : executeQuery()
156줄 : executeUpdate()
188줄 : executeWithFlags(...)
```

## 10.3 PostgreSQL wire protocol 메시지 생성

```text
PgPreparedStatement.executeQuery()/executeUpdate()
→ PgStatement 실행 공통 코드
→ connection.getQueryExecutor().execute(...)
→ QueryExecutorImpl
```

```text
<STACK>\pgjdbc\pgjdbc\src\main\java\org\postgresql\jdbc\PgStatement.java
```

```text
525, 533줄 : connection.getQueryExecutor().execute(...)
```

```text
<STACK>\pgjdbc\pgjdbc\src\main\java\org\postgresql\core\v3\QueryExecutorImpl.java
```

```text
1729줄 : sendQuery(...)
1791줄 : sendParse(...)
1873줄 : sendBind(...)
2073줄 : sendExecute(...)
2258줄 : Execute 이후 Sync/결과 처리로 이동
2398줄 : processResults(...)
```

주요 PostgreSQL 확장 쿼리 프로토콜 메시지:

```text
Parse   : SQL 문자열과 파라미터 타입 전달
Bind    : 실제 파라미터 값 연결
Execute : 준비된 Portal 실행
Sync    : 메시지 처리 동기화
```

실제 바이트 입출력:

```text
<STACK>\pgjdbc\pgjdbc\src\main\java\org\postgresql\core\PGStream.java
```

```text
48줄      : PGStream
422/892줄 : flush()
439줄     : sendChar(...)
449줄     : sendInteger4(...)
```

---

# 11. PostgreSQL 서버 내부

현재 `<STACK>`에는 PostgreSQL 서버 저장소가 없다. `postgres` 저장소를 추가하면 pgjdbc 이후를 다음 순서로 읽는다.

프로토콜 메시지 수신:

```text
<STACK>\postgres\src\backend\tcop\postgres.c

PostgresMain()
exec_parse_message()
exec_bind_message()
exec_execute_message()
```

Portal 실행:

```text
<STACK>\postgres\src\backend\tcop\pquery.c

PortalRun()
```

Executor:

```text
<STACK>\postgres\src\backend\executor\execMain.c

ExecutorStart()
ExecutorRun()
ExecutorFinish()
ExecutorEnd()
```

INSERT 노드:

```text
<STACK>\postgres\src\backend\executor\nodeModifyTable.c

ExecModifyTable()
ExecInsert()
```

테이블 접근 계층과 heap 저장:

```text
src\include\access\tableam.h
→ table_tuple_insert()

src\backend\access\heap\heapam_handler.c
→ heapam_tuple_insert()

src\backend\access\heap\heapam.c
→ heap_insert()
```

WAL 기록은 다음 디렉터리에서 이어진다.

```text
src\backend\access\transam\
```

PostgreSQL 저장소를 클론한 뒤에는 현재 체크아웃을 기준으로 함수 줄 번호를 다시 확인해야 한다.

---

# 12. COMMIT과 Connection 반환

pgjdbc의 실제 트랜잭션 커밋:

```text
HikariProxyConnection.commit()
→ ProxyConnection.commit()
→ PgConnection.commit()
→ PostgreSQL에 COMMIT 전송
```

관련 위치:

```text
HikariCP\...\ProxyConnection.java
376줄 : commit()

pgjdbc\...\PgConnection.java
1035줄 : commit()
```

트랜잭션 정리가 끝나면 Hibernate/Spring이 JDBC Connection을 `close()`한다. Hikari가 감싼 Connection이므로 물리 TCP 연결을 바로 끊지 않는다.

```text
Connection.close()
→ Hikari ProxyConnection.close()
→ PoolEntry.recycle()
→ PgConnection을 Hikari 풀에 반환
```

확인할 파일:

```text
<STACK>\HikariCP\src\main\java\com\zaxxer\hikari\pool\ProxyConnection.java
240줄 : close()
```

```text
<STACK>\HikariCP\src\main\java\com\zaxxer\hikari\pool\PoolEntry.java
77줄  : recycle(...)
101줄 : ProxyFactory.getProxyConnection(...)
```

그 후 `orderService.create()`가 Controller로 결과를 반환하고 Controller가 `201 Created` 응답을 만든다. 외부 Service 트랜잭션 프록시가 커밋을 끝낸 후에 Controller의 다음 줄이 실행된다.

---

# 13. 전체 실행 경로 한 번에 보기

## 13.1 시작 시 한 번

```text
PaymentServiceApplication.main()
→ SpringApplication.run()
→ AbstractApplicationContext.refresh()
→ Spring Boot 자동 설정

→ HikariDataSource 생성
→ Hibernate SessionFactoryImpl 생성
→ JpaTransactionManager 생성
→ OrderService CGLIB 트랜잭션 프록시 생성

→ OrderRepository 인터페이스 발견
→ JpaRepositoryFactoryBean 등록
→ 공유 EntityManager JDK 프록시 생성
→ SimpleJpaRepository 대상 생성
→ OrderRepository JDK 프록시 생성
→ 각 객체에 의존성 주입
```

## 13.2 요청마다

```text
POST /orders
→ DispatcherServlet.doDispatch()
→ RequestMappingHandlerAdapter
→ OrderController.create()

→ OrderService CGLIB 프록시
→ TransactionInterceptor
→ JpaTransactionManager.doBegin()
→ 현재 스레드에 Hibernate SessionImpl 바인딩

→ 실제 OrderService.create()
→ PurchaseOrder 생성: UUID ID 선할당
→ OrderRepository JDK 프록시.save(order)

→ JdkDynamicAopProxy.invoke()
→ Spring Data Repository 인터셉터 체인
→ SimpleJpaRepository.save()
→ JpaMetamodelEntityInformation.isNew()
→ ID가 있으므로 merge()

→ 공유 EntityManager 프록시
→ 현재 스레드의 SessionImpl 검색
→ SessionImpl.merge()
→ DefaultMergeEventListener

→ 같은 ID로 SELECT
→ Hibernate SELECT 실행기
→ JDBC PreparedStatement.executeQuery()
→ HikariProxyPreparedStatement
→ PgPreparedStatement
→ PostgreSQL
→ 행 없음

→ 새 엔티티로 처리
→ EntityInsertAction 등록
→ Repository와 Service 정상 반환

→ JpaTransactionManager.doCommit()
→ Hibernate flush
→ ActionQueue.executeActions()
→ EntityInsertAction.execute()
→ INSERT SQL 생성
→ 값 바인딩
→ PreparedStatement.executeUpdate()

→ HikariProxyPreparedStatement
→ PgPreparedStatement
→ QueryExecutorImpl
→ PGStream
→ TCP
→ PostgreSQL Executor
→ 행 및 WAL 저장

→ COMMIT
→ Hikari Connection 풀 반환
→ Controller가 HTTP 201 Created 반환
```

---

# 14. 실제로 따라갈 디버거 중단점 순서

## 14.1 시작 시 Repository 생성

```text
SpringApplication.run()
AbstractApplicationContext.refresh()
DataJpaRepositoriesRegistrar.getRepositoryConfigurationExtension()
RepositoryConfigurationDelegate.registerRepositoriesIn()
RepositoryComponentProvider.findCandidateComponents()
JpaRepositoryFactoryBean.afterPropertiesSet()
JpaRepositoryFactory.getTargetRepository()
RepositoryFactorySupport.getRepository()
SharedEntityManagerCreator.createSharedEntityManager()
JdkDynamicAopProxy 생성 지점
```

## 14.2 요청에서 DB까지

```text
DispatcherServlet.doDispatch()
InvocableHandlerMethod.doInvoke()
OrderController.create()
CglibAopProxy.DynamicAdvisedInterceptor.intercept()
TransactionInterceptor.invoke()
JpaTransactionManager.doBegin()
OrderService.create()
JdkDynamicAopProxy.invoke()
QueryExecutorMethodInterceptor.invoke()
ImplementationMethodExecutionInterceptor.invoke()
SimpleJpaRepository.save()
JpaMetamodelEntityInformation.isNew()
SharedEntityManagerInvocationHandler.invoke()
SessionImpl.merge()
DefaultMergeEventListener.onMerge()
DefaultMergeEventListener.entityIsDetached()
PreparedStatement.executeQuery()
DefaultMergeEventListener.entityIsTransient()
JpaTransactionManager.doCommit()
SessionImpl.flushBeforeTransactionCompletion()
EntityInsertAction.execute()
StatementPreparerImpl.prepareStatement()
JdbcValueBindingsImpl.beforeStatement()
ResultSetReturnImpl.executeUpdate()
Hikari ProxyPreparedStatement.executeUpdate()
PgPreparedStatement.executeUpdate()
QueryExecutorImpl.sendParse()
QueryExecutorImpl.sendBind()
QueryExecutorImpl.sendExecute()
PGStream.flush()
```

한 번에 모든 중단점을 걸기보다 위 순서를 4개 구간으로 나눠 확인한다.

```text
1회차: Controller → Service 트랜잭션 시작
2회차: Repository 프록시 → SimpleJpaRepository → merge
3회차: Hibernate SELECT → INSERT ActionQueue → flush
4회차: Hikari → pgjdbc → PGStream
```

이 순서로 보면 인터페이스 선언, 생성된 프록시, 실제 대상 구현체, JDBC 래퍼와 최종 드라이버가 각각 어디에서 연결되는지 확인할 수 있다.
