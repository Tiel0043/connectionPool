# Simple Database Connection Pool (DBCP) 구현체

이 프로젝트는 Java로 구현된 간단한 데이터베이스 커넥션 풀(DBCP: Database Connection Pool) 구현체입니다. 멀티스레드 환경에서 데이터베이스 커넥션을 효율적으로 관리하고 재사용할 수 있도록 설계되었습니다.

## 📋 목차

- [프로젝트 개요](#프로젝트-개요)
- [아키텍처](#아키텍처)
- [핵심 기능](#핵심-기능)
- [사용된 디자인 패턴 및 기법](#사용된-디자인-패턴-및-기법)
- [구현 세부사항](#구현-세부사항)
- [실행 방법](#실행-방법)
- [성능 테스트](#성능-테스트)

## 🎯 프로젝트 개요

### 목적
- 데이터베이스 커넥션 생성 비용을 최소화
- 멀티스레드 환경에서 안전한 커넥션 관리
- 커넥션 풀의 기본 동작 원리 학습

### 주요 특징
- **커넥션 풀링**: 미리 생성된 커넥션을 재사용하여 성능 향상
- **스레드 안전성**: synchronized 키워드를 통한 동기화 보장
- **타임아웃 처리**: 커넥션 요청 시 최대 대기 시간 설정
- **싱글톤 패턴**: 애플리케이션 전체에서 하나의 커넥션 풀 인스턴스 사용

## 🏗️ 아키텍처

```
┌─────────────────────────────────────────────────────────────────┐
│                           Main Application                      │
├─────────────────────────────────────────────────────────────────┤
│  Thread 1  │  Thread 2  │  Thread 3  │  ...  │  Thread N      │
│     │      │     │      │     │      │       │     │          │
│     ▼      │     ▼      │     ▼      │       │     ▼          │
├─────────────────────────────────────────────────────────────────┤
│               SimpleConnectionPool (Singleton)                  │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                Connection Queue                          │   │
│  │  ┌─────┐  ┌─────┐  ┌─────┐  ┌─────┐  ┌─────┐           │   │
│  │  │ C1  │  │ C2  │  │ C3  │  │ C4  │  │ C5  │           │   │
│  │  └─────┘  └─────┘  └─────┘  └─────┘  └─────┘           │   │
│  └─────────────────────────────────────────────────────────┘   │
│                            │                                   │
│  ┌─────────────────────────▼───────────────────────────────┐   │
│  │           동기화 메커니즘 (synchronized)                   │   │
│  │  • getConnection() - wait()/notify()                   │   │
│  │  • releaseConnection() - notify()                      │   │
│  │  • closePool()                                         │   │
│  └─────────────────────────────────────────────────────────┘   │
├─────────────────────────────────────────────────────────────────┤
│                     MySQL Database                             │
└─────────────────────────────────────────────────────────────────┘
```

### 컴포넌트 다이어그램

```
┌─────────────────────┐     ┌─────────────────────────────────┐
│       Main.java     │────▶│     SimpleConnectionPool        │
│                     │     │                                 │
│                     │     │  • connectionPool: Queue        │
│                     │     │  • url, user, password          │
│                     │     │  • maxSize: int                 │
└─────────────────────┘     │                                 │
                            │  Methods:                       │
                            │  • getInstance()                │
                            │  • getConnection(timeout)       │
                            │  • releaseConnection()          │
                            │  • closePool()                  │
                            │  • initializeConnectionPool()   │
                            └─────────────────────────────────┘
                                           │
                                           ▼
                            ┌─────────────────────────────────┐
                            │        java.sql.Connection      │
                            │                                 │
                            │  MySQL JDBC Driver를 통한       │
                            │  실제 데이터베이스 연결          │
                            └─────────────────────────────────┘
```

## ⚡ 핵심 기능

### 1. 커넥션 풀 초기화
```java
private void initializeConnectionPool() {
    try {
        for (int i = 0; i < maxSize; i++) {
            connectionPool.add(createConnection());
        }
    } catch (SQLException e) {
        throw new RuntimeException("커넥션풀 초기화중 오류 발생", e);
    }
}
```
- 풀 생성 시점에 최대 크기만큼 커넥션을 미리 생성
- `LinkedList<Connection>`을 Queue로 사용하여 FIFO 방식으로 관리

### 2. 커넥션 획득 (타임아웃 포함)
```java
public synchronized Connection getConnection(long timeout) throws SQLException, InterruptedException {
    long startTime = System.currentTimeMillis();
    
    while (connectionPool.isEmpty()) {
        long elapsedTime = System.currentTimeMillis() - startTime;
        long waitTime = timeout - elapsedTime;
        if (waitTime <= 0) {
            throw new SQLException("Timeout waiting for connection");
        }
        wait(waitTime);
    }
    return connectionPool.poll();
}
```

### 3. 커넥션 반환 및 알림
```java
public synchronized void releaseConnection(Connection connection) {
    connectionPool.offer(connection);
    notify(); // 대기 중인 스레드에게 알림
}
```

## 🔧 사용된 디자인 패턴 및 기법

### 1. **싱글톤 패턴 (Singleton Pattern)**
- **위치**: `SimpleConnectionPool.java:29-35`
- **구현 방식**: Lazy Initialization with synchronized
- **목적**: 애플리케이션 전체에서 하나의 커넥션 풀 인스턴스만 존재하도록 보장

```java
public static synchronized SimpleConnectionPool getInstance(String url, String user, String password, int maxSize) {
    if (pool == null) {
        pool = new SimpleConnectionPool(url, user, password, maxSize);
    }
    return pool;
}
```

### 2. **객체 풀 패턴 (Object Pool Pattern)**
- **구현**: `Queue<Connection>` 자료구조 사용
- **목적**: 비용이 큰 데이터베이스 커넥션 객체를 재사용
- **장점**: 객체 생성/소멸 비용 절약, 메모리 사용량 제어

### 3. **생산자-소비자 패턴 (Producer-Consumer Pattern)**
- **구현**: `wait()`/`notify()` 메커니즘
- **위치**: `getConnection()`, `releaseConnection()` 메서드
- **동작 원리**:
  - 커넥션이 없으면 스레드는 `wait()` 상태로 대기
  - 커넥션이 반환되면 `notify()`로 대기 중인 스레드 깨움

### 4. **동시성 제어 기법**

#### a) **synchronized 키워드**
- **적용 메서드**: `getInstance()`, `getConnection()`, `releaseConnection()`, `closePool()`
- **목적**: 스레드 안전성 보장

#### b) **타임아웃 메커니즘**
```java
long elapsedTime = System.currentTimeMillis() - startTime;
long waitTime = timeout - elapsedTime;
if (waitTime <= 0) {
    throw new SQLException("Timeout waiting for connection");
}
wait(waitTime);
```
- 무한 대기 방지
- 리소스 부족 상황에서 시스템 안정성 확보

## 🔍 구현 세부사항

### 커넥션 라이프사이클

1. **초기화 단계**
   - `SimpleConnectionPool` 생성자에서 `initializeConnectionPool()` 호출
   - `maxSize`만큼 커넥션을 미리 생성하여 Queue에 저장

2. **커넥션 요청 단계**
   - 스레드가 `getConnection(timeout)` 호출
   - 풀이 비어있으면 `wait(waitTime)` 상태로 대기
   - 커넥션이 있으면 `poll()`로 꺼내서 반환

3. **커넥션 사용 단계**
   - 애플리케이션에서 DB 작업 수행
   - 예제에서는 `Thread.sleep(500)`으로 DB 작업 시뮬레이션

4. **커넥션 반환 단계**
   - `releaseConnection(connection)` 호출
   - 커넥션을 다시 Queue에 `offer()`
   - `notify()`로 대기 중인 스레드에게 알림

### 스레드 동작 흐름

```
Thread-1: getConnection() 호출 → 커넥션 획득 → DB 작업 → releaseConnection()
Thread-2: getConnection() 호출 → 대기 (풀이 비어있음) → notify() 받음 → 커넥션 획득
Thread-3: getConnection() 호출 → 대기 → 타임아웃 → SQLException 발생
...
```

## 🚀 실행 방법

### 필수 조건
- Java 8 이상
- MySQL 서버 실행 중
- MySQL Connector/J 드라이버

### 데이터베이스 설정
```sql
-- 예제에서 사용하는 데이터베이스
CREATE DATABASE babel;
```

### 실행 명령어
```bash
# 프로젝트 빌드
./gradlew build

# 실행
./gradlew run
```

### 설정 변경
`Main.java:12-14`에서 데이터베이스 연결 정보 수정:
```java
String url = "jdbc:mysql://localhost:3306/babel?serverTimezone=UTC&characterEncoding=UTF-8";
String user = "root";
String password = "1234";
```

## 📊 성능 테스트

### 테스트 시나리오
- **스레드 수**: 20개
- **커넥션 풀 크기**: 5개
- **각 스레드 작업 시간**: 500ms
- **커넥션 요청 타임아웃**: 5초

### 예상 동작
1. 처음 5개 스레드가 즉시 커넥션 획득
2. 나머지 15개 스레드는 대기 상태
3. 500ms 후 첫 번째 배치 완료, 커넥션 반환
4. 대기 중인 스레드들이 순차적으로 커넥션 획득
5. 모든 스레드 완료까지 약 2초 소요

### 로그 출력 예시
```
Thread-0 커넥션 풀 획득
스레드 획득 현재 개수 : 4
Thread-1 커넥션 풀 획득
스레드 획득 현재 개수 : 3
...
Thread-5커넥션 풀이 반납되길 기다리고 있습니다.
Thread-6커넥션 풀이 반납되길 기다리고 있습니다.
...
Thread-0 커넥션 풀 반납
스레드 반납 현재 개수 : 1
```

## 🎓 학습 포인트

### 1. 커넥션 풀의 필요성
- **문제**: 매번 새로운 커넥션 생성 시 높은 오버헤드
- **해결**: 미리 생성된 커넥션 재사용으로 성능 향상

### 2. 멀티스레드 환경에서의 동시성 제어
- **synchronized**: 메서드 레벨에서 동기화
- **wait()/notify()**: 조건 변수를 통한 스레드 간 협력

### 3. 타임아웃 처리의 중요성
- 무한 대기 방지
- 시스템 리소스 보호
- 장애 상황에서 빠른 복구

### 4. 객체 풀 패턴의 활용
- 비용이 큰 객체의 재사용
- 메모리 사용량 제어
- 시스템 안정성 향상

## 📝 참고사항

이 구현체는 학습 목적으로 제작된 간단한 버전입니다. 실제 운영 환경에서는 다음과 같은 기능들이 추가로 필요합니다:

- 커넥션 유효성 검사 (validation)
- 동적 풀 크기 조절
- 통계 및 모니터링 기능
- 더 정교한 예외 처리
- 설정 파일을 통한 외부 설정

상용 솔루션으로는 Apache Commons DBCP, HikariCP, C3P0 등을 권장합니다.

## 🔗 참고 링크
- [커넥션풀 구현 정리글](https://velog.io/@rlamw2000/%EC%BB%A4%EB%84%A5%EC%85%98%ED%92%80-%EA%B5%AC%ED%98%84%EA%B3%BC-%EB%A9%80%ED%8B%B0%EC%8A%A4%EB%A0%88%EB%93%9C)
