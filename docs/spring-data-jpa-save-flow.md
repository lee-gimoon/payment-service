# `OrderRepository.save()`가 DB까지 도달하는 전체 코드 경로

이 문서는 이 프로젝트의 다음 코드가 별도 구현 클래스 없이 어떻게 동작하는지 실제 소스 파일 순서로 추적한다.

```java
public interface OrderRepository extends JpaRepository<PurchaseOrder, String> {
}
```

프로젝트 코드는 [OrderRepository](../src/main/java/com/example/payment/order/OrderRepository.java)와 [PurchaseOrder](../src/main/java/com/example/payment/order/PurchaseOrder.java)에서 확인할 수 있다.

이 프로젝트는 Spring Boot `4.1.1`을 사용한다. Boot가 관리하는 주요 버전은 Spring Data JPA/Commons `4.1.1`, Spring Framework `7.0.9`, Hibernate ORM `7.4.5.Final`이다. 다른 버전에서는 클래스 이름은 같아도 줄 번호와 일부 내부 구현이 달라질 수 있다.

전체 코드는 하나의 저장소에 있지 않다.

```text
payment-service
→ Spring Boot
→ spring-data-jpa
→ spring-data-commons
→ spring-aop / spring-tx / spring-orm
→ Jakarta Persistence API
→ Hibernate ORM
→ JDBC 드라이버
→ PostgreSQL
```

## 1. `save()` 선언까지의 인터페이스 상속 구조

`save()`만 따라갈 때의 상속 경로는 다음과 같다.

```text
OrderRepository
└─ JpaRepository<PurchaseOrder, String>
   └─ ListCrudRepository<PurchaseOrder, String>
      └─ CrudRepository<PurchaseOrder, String>
         └─ Repository<PurchaseOrder, String>
```

`JpaRepository`의 전체 상속 구조는 다음과 같다.

```text
JpaRepository
├─ ListCrudRepository             ← save(), findById()
├─ ListPagingAndSortingRepository ← 페이징과 정렬
└─ QueryByExampleExecutor         ← Example 기반 검색
```

### 1.1 `JpaRepository.java`

모듈: `spring-data-jpa`

```text
spring-data-jpa/src/main/java/
└─ org/springframework/data/jpa/repository/JpaRepository.java
```

```java
@NoRepositoryBean
public interface JpaRepository<T, ID>
        extends ListCrudRepository<T, ID>,
                ListPagingAndSortingRepository<T, ID>,
                QueryByExampleExecutor<T> {
}
```

### 1.2 `ListCrudRepository.java`

모듈: `spring-data-commons`

```text
spring-data-commons/src/main/java/
└─ org/springframework/data/repository/ListCrudRepository.java
```

```java
@NoRepositoryBean
public interface ListCrudRepository<T, ID>
        extends CrudRepository<T, ID> {
}
```

### 1.3 `CrudRepository.java`

모듈: `spring-data-commons`

```text
spring-data-commons/src/main/java/
└─ org/springframework/data/repository/CrudRepository.java
```

여기에 `save()`와 `findById()`가 선언되어 있다.

```java
@NoRepositoryBean
public interface CrudRepository<T, ID>
        extends Repository<T, ID> {

    <S extends T> S save(S entity);

    Optional<T> findById(ID id);
}
```

아직 구현은 없고 메서드 선언만 있다.

### 1.4 `Repository.java`

모듈: `spring-data-commons`

```text
spring-data-commons/src/main/java/
└─ org/springframework/data/repository/Repository.java
```

```java
public interface Repository<T, ID> {
}
```

메서드가 없는 표식용 인터페이스다. Spring Data는 `Repository`를 상속한 인터페이스를 저장소 후보로 인식한다.

## 2. Spring Boot가 JPA Repository 기능을 활성화하는 과정

이 프로젝트에는 직접 작성한 `@EnableJpaRepositories`가 없다. Spring Boot 자동 설정이 대신 활성화한다.

### 2.1 `DataJpaRepositoriesAutoConfiguration.java`

모듈: `spring-boot-data-jpa`

```text
spring-boot-data-jpa/src/main/java/
└─ org/springframework/boot/data/jpa/autoconfigure/
   └─ DataJpaRepositoriesAutoConfiguration.java
```

`JpaRepository`와 `DataSource`가 존재하면 `JpaRepositoriesImportSelector`를 가져온다.

```java
@ConditionalOnBean(DataSource.class)
@ConditionalOnClass(JpaRepository.class)
@Import(JpaRepositoriesImportSelector.class)
public final class DataJpaRepositoriesAutoConfiguration {
}
```

`JpaRepositoriesImportSelector.selectImports()`는 `DataJpaRepositoriesRegistrar`를 선택한다.

### 2.2 `DataJpaRepositoriesRegistrar.java`

모듈: `spring-boot-data-jpa`

```text
org/springframework/boot/data/jpa/autoconfigure/
└─ DataJpaRepositoriesRegistrar.java
```

이 클래스는 내부 설정 클래스에 `@EnableJpaRepositories`를 붙이고 `JpaRepositoryConfigExtension`을 사용한다.

```java
class DataJpaRepositoriesRegistrar
        extends AbstractRepositoryConfigurationSourceSupport {

    @Override
    protected RepositoryConfigurationExtension
            getRepositoryConfigurationExtension() {
        return new JpaRepositoryConfigExtension();
    }

    @EnableJpaRepositories
    private static final class EnableJpaRepositoriesConfiguration {
    }
}
```

### 2.3 `EnableJpaRepositories.java`

모듈: `spring-data-jpa`

```text
org/springframework/data/jpa/repository/config/
└─ EnableJpaRepositories.java
```

```java
@Import(JpaRepositoriesRegistrar.class)
public @interface EnableJpaRepositories {
}
```

### 2.4 `JpaRepositoriesRegistrar.java`

모듈: `spring-data-jpa`

```text
org/springframework/data/jpa/repository/config/
└─ JpaRepositoriesRegistrar.java
```

```java
class JpaRepositoriesRegistrar
        extends RepositoryBeanDefinitionRegistrarSupport {

    @Override
    protected RepositoryConfigurationExtension getExtension() {
        return new JpaRepositoryConfigExtension();
    }
}
```

## 3. `OrderRepository`를 찾아 BeanDefinition으로 등록하는 과정

### 3.1 `RepositoryBeanDefinitionRegistrarSupport.java`

모듈: `spring-data-commons`

```text
org/springframework/data/repository/config/
└─ RepositoryBeanDefinitionRegistrarSupport.java
```

`registerBeanDefinitions()`이 `RepositoryConfigurationDelegate`를 생성한 뒤 저장소 등록을 위임한다.

```java
RepositoryConfigurationDelegate delegate =
        new RepositoryConfigurationDelegate(...);

delegate.registerRepositoriesIn(registry, extension);
```

### 3.2 `RepositoryConfigurationDelegate.java`

모듈: `spring-data-commons`

```text
org/springframework/data/repository/config/
└─ RepositoryConfigurationDelegate.java
```

`registerRepositoriesIn()`에서 후보 인터페이스를 찾고 각 인터페이스의 BeanDefinition을 만든다.

```java
Collection<RepositoryConfiguration<RepositoryConfigurationSource>> configurations =
        extension.getRepositoryConfigurations(...);

for (RepositoryConfiguration<?> configuration : configurations) {
    BeanDefinitionBuilder builder = repositoryBeanDefinitionBuilder.build(configuration);
    registry.registerBeanDefinition(beanName, builder.getBeanDefinition());
}
```

### 3.3 `RepositoryConfigurationSourceSupport.java`

모듈: `spring-data-commons`

```text
org/springframework/data/repository/config/
└─ RepositoryConfigurationSourceSupport.java
```

`getCandidates()`가 `RepositoryComponentProvider`로 기본 패키지를 스캔한다.

### 3.4 `RepositoryComponentProvider.java`

모듈: `spring-data-commons`

```text
org/springframework/data/repository/config/
└─ RepositoryComponentProvider.java
```

다음 필터가 `Repository`를 상속한 인터페이스를 찾는다.

```java
addIncludeFilter(new InterfaceTypeFilter(Repository.class));
addExcludeFilter(new AnnotationTypeFilter(NoRepositoryBean.class));
```

따라서 `JpaRepository`, `ListCrudRepository`, `CrudRepository`는 `@NoRepositoryBean` 때문에 Bean이 되지 않고, 구체적인 `OrderRepository`만 후보가 된다.

### 3.5 `DefaultRepositoryMetadata.java`

모듈: `spring-data-commons`

```text
org/springframework/data/repository/core/support/
└─ DefaultRepositoryMetadata.java
```

생성자에서 `OrderRepository`의 제네릭 상속 구조를 분석한다.

```java
List<TypeInformation<?>> arguments =
        TypeInformation.of(repositoryInterface)
                .getRequiredSuperTypeInformation(Repository.class)
                .getTypeArguments();
```

그 결과는 다음과 같다.

```text
repositoryInterface = OrderRepository
domainType          = PurchaseOrder
idType              = String
```

### 3.6 `RepositoryBeanDefinitionBuilder.java`

모듈: `spring-data-commons`

```text
org/springframework/data/repository/config/
└─ RepositoryBeanDefinitionBuilder.java
```

`build()`가 Repository 인터페이스를 직접 Bean으로 등록하는 것이 아니라 Repository용 `FactoryBean`을 등록한다.

```java
BeanDefinitionBuilder builder = BeanDefinitionBuilder
        .rootBeanDefinition(configuration.getRepositoryFactoryBeanClassName());

builder.addConstructorArgValue(configuration.getRepositoryInterface());
```

JPA에서 선택되는 값은 다음과 같다.

```text
FactoryBean 클래스 = JpaRepositoryFactoryBean
생성자 인수         = OrderRepository.class
```

### 3.7 `JpaRepositoryConfigExtension.java`

모듈: `spring-data-jpa`

```text
org/springframework/data/jpa/repository/config/
└─ JpaRepositoryConfigExtension.java
```

이 클래스가 기본 구현체와 FactoryBean을 지정한다.

```java
public String getRepositoryBaseClassName() {
    return SimpleJpaRepository.class.getName();
}

public String getRepositoryFactoryBeanClassName() {
    return JpaRepositoryFactoryBean.class.getName();
}
```

`postProcess()`는 `JpaRepositoryFactoryBean`에 트랜잭션 매니저와 공유 `EntityManager` 참조를 설정한다. `registerBeansForRoot()`와 `registerSharedEntityMangerIfNotAlreadyRegistered()`는 공유 `EntityManager` Bean을 등록한다.

## 4. `JpaRepositoryFactoryBean`이 저장소 프록시를 만드는 과정

### 4.1 `JpaRepositoryFactoryBean.java`

모듈: `spring-data-jpa`

```text
org/springframework/data/jpa/repository/support/
└─ JpaRepositoryFactoryBean.java
```

Spring은 이 클래스 생성자에 `OrderRepository.class`를 넣고 `setEntityManager()`로 공유 EntityManager 프록시를 주입한다.

```java
public JpaRepositoryFactoryBean(Class<? extends T> repositoryInterface) {
    super(repositoryInterface);
}

public void setEntityManager(EntityManager entityManager) {
    this.entityManager = entityManager;
}
```

`doCreateRepositoryFactory()`는 `JpaRepositoryFactory`를 만든다.

```java
return new JpaRepositoryFactory(entityManager);
```

### 4.2 `RepositoryFactoryBeanSupport.java`

모듈: `spring-data-commons`

```text
org/springframework/data/repository/core/support/
└─ RepositoryFactoryBeanSupport.java
```

이 클래스는 Spring의 `FactoryBean<T>`를 구현한다. `afterPropertiesSet()`에서 팩토리를 초기화하고, `getObject()`가 최종 저장소 프록시를 반환한다.

```java
this.factory = createRepositoryFactory();
this.repository = Lazy.of(() ->
        factory.getRepository(repositoryInterface, repositoryFragments));
```

### 4.3 `TransactionalRepositoryFactoryBeanSupport.java`

모듈: `spring-data-commons`

```text
org/springframework/data/repository/core/support/
└─ TransactionalRepositoryFactoryBeanSupport.java
```

Repository 팩토리에 다음 기능을 추가한다.

- `TransactionalRepositoryProxyPostProcessor`: 트랜잭션 처리
- `PersistenceExceptionTranslationRepositoryProxyPostProcessor`: JPA 예외를 Spring `DataAccessException`으로 변환

### 4.4 `JpaRepositoryFactory.java`

모듈: `spring-data-jpa`

```text
org/springframework/data/jpa/repository/support/
└─ JpaRepositoryFactory.java
```

`getTargetRepository()`가 `PurchaseOrder`용 `JpaEntityInformation`을 만들고 리플렉션으로 기본 구현체를 생성한다.

```java
JpaEntityInformation<?, Serializable> entityInformation =
        getEntityInformation(information.getDomainType());

Object repository = getTargetRepositoryViaReflection(
        information,
        entityInformation,
        entityManager);
```

기본 구현 클래스는 `SimpleJpaRepository`다.

```java
protected Class<?> getRepositoryBaseClass(RepositoryMetadata metadata) {
    return SimpleJpaRepository.class;
}
```

결과적으로 다음과 같은 객체가 생성된다.

```java
new SimpleJpaRepository(
        purchaseOrderEntityInformation,
        sharedEntityManagerProxy);
```

### 4.5 `RepositoryFactorySupport.java`

모듈: `spring-data-commons`

```text
org/springframework/data/repository/core/support/
└─ RepositoryFactorySupport.java
```

`getRepository()`가 `SimpleJpaRepository`를 target으로 설정하고 `OrderRepository` 인터페이스를 구현하는 프록시를 만든다.

```java
Object target = getTargetRepository(information);

ProxyFactory result = new ProxyFactory();
result.setTarget(target);
result.setInterfaces(
        repositoryInterface,
        Repository.class,
        TransactionalProxy.class);
```

메서드 실행 인터셉터도 추가한다.

```java
result.addAdvice(new QueryExecutorMethodInterceptor(...));
result.addAdvice(new ImplementationMethodExecutionInterceptor(...));

T repository = (T) result.getProxy(classLoader);
```

이 `repository`가 서비스에 주입되는 `OrderRepository` 객체다.

### 4.6 `DefaultAopProxyFactory.java`와 `JdkDynamicAopProxy.java`

모듈: `spring-aop`

```text
spring-aop/src/main/java/org/springframework/aop/framework/
├─ DefaultAopProxyFactory.java
├─ JdkDynamicAopProxy.java
└─ ReflectiveMethodInvocation.java
```

인터페이스 기반 설정이므로 `DefaultAopProxyFactory.createAopProxy()`가 `JdkDynamicAopProxy`를 선택한다.

`JdkDynamicAopProxy.getProxy()`는 JVM의 동적 프록시를 만든다.

```java
return Proxy.newProxyInstance(
        classLoader,
        proxiedInterfaces,
        this);
```

따라서 소스 파일 형태의 `OrderRepositoryImpl.java`가 생성되는 것은 아니다. JVM 메모리에 `OrderRepository`를 구현하는 프록시 객체가 만들어진다.

## 5. `orderRepository.save(order)` 호출 경로

호출 시 핵심 경로는 다음과 같다.

```text
OrderRepository JDK 프록시
→ JdkDynamicAopProxy.invoke()
→ ReflectiveMethodInvocation.proceed()
→ TransactionInterceptor.invoke()
→ QueryExecutorMethodInterceptor.invoke()
→ ImplementationMethodExecutionInterceptor.invoke()
→ RepositoryComposition.invoke()
→ RepositoryMethodInvoker
→ SimpleJpaRepository.save()
```

### 5.1 `JdkDynamicAopProxy.java`

모듈: `spring-aop`

`invoke()`가 `save()`에 적용할 인터셉터 목록을 가져오고 `ReflectiveMethodInvocation.proceed()`를 호출한다.

### 5.2 `TransactionalRepositoryProxyPostProcessor.java`

모듈: `spring-data-commons`

```text
org/springframework/data/repository/core/support/
└─ TransactionalRepositoryProxyPostProcessor.java
```

이 클래스가 저장소 프록시에 `TransactionInterceptor`를 등록한다.

### 5.3 `TransactionInterceptor.java`

모듈: `spring-tx`

```text
org/springframework/transaction/interceptor/
└─ TransactionInterceptor.java
```

`invoke()`가 `TransactionAspectSupport.invokeWithinTransaction()`으로 이동한다.

### 5.4 `TransactionAspectSupport.java`

모듈: `spring-tx`

`invokeWithinTransaction()`이 트랜잭션을 시작하거나 기존 트랜잭션에 참여한 뒤 다음 인터셉터를 실행한다.

```java
TransactionInfo txInfo = createTransactionIfNecessary(...);
Object result = invocation.proceedWithInvocation();
commitTransactionAfterReturning(txInfo);
```

### 5.5 `JpaTransactionManager.java`

모듈: `spring-orm`

```text
org/springframework/orm/jpa/
└─ JpaTransactionManager.java
```

`doBegin()`이 트랜잭션용 실제 EntityManager를 만들고 현재 스레드에 연결한다.

```java
EntityManager newEm = createEntityManagerForTransaction();

TransactionSynchronizationManager.bindResource(
        entityManagerFactory,
        entityManagerHolder);
```

Hibernate를 사용하므로 실제 EntityManager는 Hibernate `SessionImpl` 계열 객체다.

### 5.6 `QueryExecutorMethodInterceptor.java`

모듈: `spring-data-commons`

```text
org/springframework/data/repository/core/support/
└─ QueryExecutorMethodInterceptor.java
```

`findByProductName()` 같은 쿼리 메서드는 이 인터셉터가 처리한다. `save()`는 쿼리 메서드가 아니므로 다음 인터셉터로 넘긴다.

```java
if (hasQueryFor(method)) {
    return executeRepositoryQuery(...);
}

return invocation.proceed();
```

### 5.7 `ImplementationMethodExecutionInterceptor`

모듈: `spring-data-commons`

`RepositoryFactorySupport.java` 안의 내부 클래스다.

```java
return composition.invoke(
        invocationMulticaster,
        method,
        arguments);
```

### 5.8 `RepositoryComposition.java`

모듈: `spring-data-commons`

```text
org/springframework/data/repository/core/support/
└─ RepositoryComposition.java
```

`CrudRepository.save()`와 호환되는 실제 구현 메서드인 `SimpleJpaRepository.save()`를 찾는다.

### 5.9 `RepositoryMethodInvoker.java`

모듈: `spring-data-commons`

```text
org/springframework/data/repository/core/support/
└─ RepositoryMethodInvoker.java
```

최종적으로 리플렉션을 사용해 `SimpleJpaRepository.save()`를 호출한다.

```java
return AopUtils.invokeJoinpointUsingReflection(
        instance,
        baseClassMethod,
        args);
```

## 6. `SimpleJpaRepository.save()`와 새 엔티티 판정

### 6.1 `SimpleJpaRepository.java`

모듈: `spring-data-jpa`

```text
org/springframework/data/jpa/repository/support/
└─ SimpleJpaRepository.java
```

실제 CRUD 구현은 다음과 같다.

```java
@Transactional
public <S extends T> S save(S entity) {
    if (entityInformation.isNew(entity)) {
        entityManager.persist(entity);
        return entity;
    }
    else {
        return entityManager.merge(entity);
    }
}
```

### 6.2 `JpaEntityInformationSupport.java`

모듈: `spring-data-jpa`

```text
org/springframework/data/jpa/repository/support/
└─ JpaEntityInformationSupport.java
```

엔티티가 `Persistable`을 구현하면 `JpaPersistableEntityInformation`, 아니면 `JpaMetamodelEntityInformation`을 만든다.

### 6.3 `JpaMetamodelEntityInformation.java`

모듈: `spring-data-jpa`

```text
org/springframework/data/jpa/repository/support/
└─ JpaMetamodelEntityInformation.java
```

래퍼 타입의 `@Version` 필드가 있으면 버전 값이 `null`인지 먼저 검사한다. 사용할 수 있는 버전 필드가 없으면 `AbstractEntityInformation.isNew()`로 이동한다.

### 6.4 `AbstractEntityInformation.java`

모듈: `spring-data-commons`

```text
org/springframework/data/repository/core/support/
└─ AbstractEntityInformation.java
```

객체 타입 ID는 `null`이면 새 엔티티다.

```java
ID id = getId(entity);

if (!idType.isPrimitive()) {
    return id == null;
}
```

### 6.5 이 프로젝트의 `PurchaseOrder`가 실제로 선택하는 분기

`PurchaseOrder` 생성자는 저장 전에 UUID 문자열을 ID에 넣는다.

```java
this.id = UUID.randomUUID().toString();
```

또한 `PurchaseOrder`에는 `@Version` 필드가 없고 `Persistable<String>`도 구현하지 않는다. 따라서 새로 생성한 주문이어도 Spring Data의 판정은 다음과 같다.

```text
id != null
→ JpaMetamodelEntityInformation.isNew(order) == false
→ SimpleJpaRepository.save()
→ entityManager.merge(order)
```

Hibernate는 `merge()` 과정에서 해당 ID의 행을 조회하고, 행이 없고 새 객체로 판단되면 INSERT 작업을 만든다. 그러므로 이 프로젝트에서는 `save()`가 곧바로 `persist()`를 호출하는 일반적인 예시와 실제 경로가 다르다.

`merge()`는 전달한 객체 자체가 아니라 영속 상태인 복사본을 반환할 수 있으므로 `OrderService`가 다음처럼 반환값을 다시 대입하는 것이 중요하다.

```java
order = orderRepository.save(order);
```

## 7. 공유 EntityManager 프록시가 실제 Hibernate Session을 찾는 과정

### 7.1 `EntityManager.java`

모듈: `jakarta.persistence-api`

```text
jakarta/persistence/EntityManager.java
```

이 파일에는 JPA 표준 메서드가 선언되어 있다.

```java
void persist(Object entity);
<T> T merge(T entity);
<T> T find(Class<T> entityClass, Object primaryKey);
void flush();
```

### 7.2 `SharedEntityManagerCreator.java`

모듈: `spring-orm`

```text
org/springframework/orm/jpa/
└─ SharedEntityManagerCreator.java
```

`createSharedEntityManager()`가 `EntityManager` 인터페이스를 구현하는 JDK 프록시를 만든다.

호출 시 `SharedEntityManagerInvocationHandler.invoke()`가 다음 메서드로 현재 트랜잭션의 실제 EntityManager를 찾는다.

```java
EntityManager target = EntityManagerFactoryUtils
        .doGetTransactionalEntityManager(entityManagerFactory, properties, true);
```

그 후 실제 EntityManager에 메서드를 위임한다.

```java
Object result = method.invoke(target, args);
```

### 7.3 `EntityManagerFactoryUtils.java`

모듈: `spring-orm`

```text
org/springframework/orm/jpa/
└─ EntityManagerFactoryUtils.java
```

`doGetTransactionalEntityManager()`가 `JpaTransactionManager.doBegin()`에서 현재 스레드에 연결해 둔 `EntityManagerHolder`를 찾는다.

```java
EntityManagerHolder holder =
        (EntityManagerHolder)
                TransactionSynchronizationManager.getResource(entityManagerFactory);

return holder.getEntityManager();
```

## 8. Hibernate의 `merge()`와 `persist()` 처리

Hibernate의 `Session` 인터페이스는 JPA의 `EntityManager`를 상속한다.

```java
public interface Session
        extends SharedSessionContract, EntityManager {
}
```

실제 구현은 `org.hibernate.internal.SessionImpl`이다.

### 8.1 `persist()` 분기

```text
SessionImpl.persist()
→ SessionImpl.firePersist()
→ DefaultPersistEventListener.onPersist()
→ DefaultPersistEventListener.entityIsTransient()
→ AbstractSaveEventListener.saveWithGeneratedId()
→ AbstractSaveEventListener.addInsertAction()
→ ActionQueue.addAction(EntityInsertAction)
```

주요 파일은 다음과 같다.

```text
hibernate-core/src/main/java/
├─ org/hibernate/internal/SessionImpl.java
├─ org/hibernate/event/internal/DefaultPersistEventListener.java
├─ org/hibernate/event/internal/AbstractSaveEventListener.java
└─ org/hibernate/engine/spi/ActionQueue.java
```

`persist()` 시점에는 일반적으로 INSERT 작업이 `ActionQueue`에 등록된다. IDENTITY 키처럼 ID를 즉시 얻어야 하는 경우에는 INSERT가 더 빨리 실행될 수 있다.

### 8.2 `merge()` 분기

```text
SessionImpl.merge()
→ SessionImpl.fireMerge()
→ DefaultMergeEventListener.onMerge()
→ 엔티티 상태 판정
→ entityIsDetached() / entityIsTransient() / entityIsPersistent()
```

주요 파일은 다음과 같다.

```text
org/hibernate/internal/SessionImpl.java
org/hibernate/event/internal/DefaultMergeEventListener.java
```

준영속 객체로 판단하면 `DefaultMergeEventListener.entityIsDetached()`가 ID로 기존 행을 조회하고, 전달받은 객체의 필드 값을 조회된 영속 객체로 복사한다.

이 프로젝트의 새 `PurchaseOrder`처럼 ID는 있지만 DB 행이 없는 경우에는 조회 결과가 없으므로 Hibernate가 다시 transient 객체로 처리하여 INSERT 작업을 만든다.

기존 행을 수정하는 경우 flush 중 `DefaultFlushEntityEventListener`의 변경 감지가 `EntityUpdateAction`을 만든다.

## 9. 트랜잭션 커밋, flush, JDBC 실행

Repository 호출이 정상 반환되면 `TransactionAspectSupport`가 커밋을 요청한다.

```text
TransactionAspectSupport.commitTransactionAfterReturning()
→ JpaTransactionManager.doCommit()
→ EntityTransaction.commit()
→ Hibernate TransactionImpl.commit()
→ SessionImpl.beforeTransactionCompletion()
→ SessionImpl.flushBeforeTransactionCompletion()
```

### 9.1 Hibernate 트랜잭션 파일

```text
org/hibernate/engine/transaction/internal/TransactionImpl.java
org/hibernate/resource/transaction/backend/jdbc/internal/
└─ JdbcResourceLocalTransactionCoordinatorImpl.java
```

`JdbcResourceLocalTransactionCoordinatorImpl`은 실제 JDBC 커밋 전에 `beforeCompletionCallback()`을 실행한다. 이 콜백이 `SessionImpl.beforeTransactionCompletion()`을 호출하면서 flush가 시작된다.

### 9.2 Hibernate flush 파일

```text
org/hibernate/internal/SessionImpl.java
org/hibernate/event/internal/DefaultFlushEventListener.java
org/hibernate/event/internal/AbstractFlushingEventListener.java
org/hibernate/engine/spi/ActionQueue.java
```

핵심 경로는 다음과 같다.

```text
SessionImpl.flushBeforeTransactionCompletion()
→ SessionImpl.managedFlush()
→ SessionImpl.fireFlush()
→ DefaultFlushEventListener.onFlush()
→ AbstractFlushingEventListener.performExecutions()
→ ActionQueue.executeActions()
```

### 9.3 INSERT와 UPDATE 실행 파일

새 행 INSERT:

```text
EntityInsertAction.execute()
→ InsertCoordinatorStandard.insert()
```

기존 행 UPDATE:

```text
DefaultFlushEntityEventListener.scheduleUpdate()
→ EntityUpdateAction.execute()
→ UpdateCoordinatorStandard.update()
```

관련 파일은 다음과 같다.

```text
org/hibernate/action/internal/EntityInsertAction.java
org/hibernate/action/internal/EntityUpdateAction.java
org/hibernate/event/internal/DefaultFlushEntityEventListener.java
org/hibernate/persister/entity/mutation/InsertCoordinatorStandard.java
org/hibernate/persister/entity/mutation/UpdateCoordinatorStandard.java
```

### 9.4 Hibernate에서 JDBC 경계까지

일반적인 비배치 실행 경로는 다음과 같다.

```text
InsertCoordinatorStandard / UpdateCoordinatorStandard
→ AbstractMutationExecutor.execute()
→ MutationExecutorStandard.performNonBatchedOperations()
→ AbstractMutationExecutor.performNonBatchedMutation()
→ ResultSetReturnImpl.executeUpdate()
→ java.sql.PreparedStatement.executeUpdate()
→ PostgreSQL JDBC 드라이버
→ PostgreSQL
```

관련 Hibernate 파일은 다음과 같다.

```text
org/hibernate/engine/jdbc/mutation/internal/AbstractMutationExecutor.java
org/hibernate/engine/jdbc/mutation/internal/MutationExecutorStandard.java
org/hibernate/engine/jdbc/internal/ResultSetReturnImpl.java
```

`ResultSetReturnImpl.executeUpdate()`의 다음 호출이 Hibernate와 JDBC 드라이버의 경계다.

```java
return statement.executeUpdate();
```

JDBC 배치가 활성화되어 있으면 `org.hibernate.engine.jdbc.batch.internal.BatchImpl`을 거쳐 `PreparedStatement.executeBatch()`가 호출된다.

## 10. 전체 실행 경로 요약

애플리케이션 시작 시 한 번 실행되는 경로:

```text
DataJpaRepositoriesAutoConfiguration
→ DataJpaRepositoriesRegistrar
→ @EnableJpaRepositories
→ JpaRepositoriesRegistrar
→ RepositoryBeanDefinitionRegistrarSupport
→ RepositoryConfigurationDelegate
→ RepositoryComponentProvider
→ OrderRepository 발견
→ DefaultRepositoryMetadata
→ RepositoryBeanDefinitionBuilder
→ JpaRepositoryFactoryBean 등록
→ JpaRepositoryFactory
→ SimpleJpaRepository target 생성
→ RepositoryFactorySupport
→ OrderRepository JDK 프록시 생성
```

`save()`를 호출할 때마다 실행되는 경로:

```text
OrderRepository.save(order)
→ JdkDynamicAopProxy.invoke()
→ TransactionInterceptor.invoke()
→ JpaTransactionManager.doBegin()
→ QueryExecutorMethodInterceptor: 쿼리 메서드가 아니므로 통과
→ ImplementationMethodExecutionInterceptor
→ RepositoryComposition
→ RepositoryMethodInvoker
→ SimpleJpaRepository.save()
→ JpaEntityInformation.isNew(order)
→ 이 프로젝트의 PurchaseOrder는 ID가 이미 있으므로 merge()
→ SharedEntityManagerInvocationHandler
→ EntityManagerFactoryUtils
→ 실제 Hibernate SessionImpl.merge()
→ DB 행 조회 및 영속 객체 생성/상태 복사
→ Repository 호출 반환
→ JpaTransactionManager.doCommit()
→ Hibernate flush
→ EntityInsertAction 또는 EntityUpdateAction
→ MutationExecutor
→ ResultSetReturnImpl
→ PreparedStatement.executeUpdate()
→ PostgreSQL JDBC 드라이버
→ PostgreSQL
```

## 11. 디버거 중단점 순서

프록시 생성부터 확인하려면 애플리케이션 시작 전에 다음 순서로 중단점을 건다.

```text
RepositoryComponentProvider.findCandidateComponents()
RepositoryFactorySupport.getRepository()
JpaRepositoryFactory.getTargetRepository()
DefaultAopProxyFactory.createAopProxy()
JdkDynamicAopProxy.getProxy()
```

`save()` 호출부터 DB까지 확인하려면 다음 순서로 중단점을 건다.

```text
JdkDynamicAopProxy.invoke()
TransactionInterceptor.invoke()
JpaTransactionManager.doBegin()
QueryExecutorMethodInterceptor.doInvoke()
ImplementationMethodExecutionInterceptor.invoke()
RepositoryComposition.invoke()
RepositoryMethodInvoker
SimpleJpaRepository.save()
JpaMetamodelEntityInformation.isNew()
SharedEntityManagerInvocationHandler.invoke()
EntityManagerFactoryUtils.doGetTransactionalEntityManager()
SessionImpl.merge()
DefaultMergeEventListener.onMerge()
JpaTransactionManager.doCommit()
SessionImpl.flushBeforeTransactionCompletion()
DefaultFlushEventListener.onFlush()
ActionQueue.executeActions()
EntityInsertAction.execute() 또는 EntityUpdateAction.execute()
AbstractMutationExecutor.performNonBatchedMutation()
ResultSetReturnImpl.executeUpdate()
PreparedStatement.executeUpdate()
```

IntelliJ에서 의존성 내부 클래스를 열 때는 `Ctrl+N`으로 클래스 이름을 검색하고 프로젝트 외부 항목을 포함하면 Gradle이 내려받은 `sources.jar`의 실제 `.java` 파일을 볼 수 있다.
