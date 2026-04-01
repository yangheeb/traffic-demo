# 장애 상황에서 효율적인 트래픽 제어 방법
> 채널계 AP서버 Filter 레벨 트래픽 제어 전략 비교 실험

---

## 개요

은행 인턴 당시 목격한 계정계 DB Hang → Failover 인시던트에서 출발한 프로젝트입니다.  
인시던트 보고서에 기록된 "채널계 앞단 트래픽 제한" 조치의 의미를 이해하고,  
**채널계 서버 Filter 레벨에서 어떤 트래픽 제어 전략이 가장 효과적인지** 실험했습니다.

---

## 실험 목적

- Filter 트래픽 제어가 실제로 효과가 있는가
- 네 가지 전략 중 어떤 전략이 커넥션 풀을 가장 잘 보호하는가

---

## 기술 스택

| 항목 | 내용 |
|------|------|
| Language | Java 17 |
| Framework | Spring Boot 4.0.5 |
| DB | MySQL |
| Connection Pool | HikariCP 7.0.2 |
| Build | Gradle |

---

## 프로젝트 구조

```
src/main/java/dev/fisa/
├── config/
│   └── TrafficProperties.java     # yml 설정값을 읽어오는 클래스
├── filter/
│   ├── RateLimitFilter.java       # 공통 인터페이스
│   ├── TrafficControlFilter.java  # 실제 요청을 가로채는 Filter
│   ├── TokenBucketFilter.java     # Token Bucket 알고리즘
│   ├── FixedWindowFilter.java     # Fixed Window Counter 알고리즘
│   ├── SlidingWindowFilter.java   # Sliding Window Log 알고리즘
│   └── LeakyBucketFilter.java     # Leaky Bucket 알고리즘
└── controller/
    └── HangController.java        # 계정계 DB Hang 상황 재현
```

---

## 시뮬레이션 환경

| 설정 | 값 | 이유 |
|------|-----|------|
| 커넥션 풀 | 10개 | 커넥션 풀 고갈 상황을 확실하게 만들기 위해 |
| 커넥션 대기 시간 | 250ms | HikariCP 최솟값 |
| 쿼리 처리 시간 | 0.5초 (SELECT SLEEP(0.5)) | 초당 최대 20개 처리 → 커넥션 풀 고갈 재현 |
| 동시 요청 수 | 200개 | 서버 처리 능력(20개)의 10배 |

> 실제 은행 수치를 재현하는 게 목적이 아니라  
> 커넥션 풀 고갈 상황에서 각 전략의 **상대적인 효과와 경향**을 비교하는 게 목적입니다.

---

## 설정 방법

`application.yml`에서 전략을 선택합니다.

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/traffic_demo
    username: root
    password: 1234
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      maximum-pool-size: 10
      minimum-idle: 10
      connection-timeout: 250

traffic:
  strategy: TOKEN_BUCKET  # 전략 선택
  tps-limit: 15           # 초당 허용 요청 수
```

### strategy 옵션

| 값 | 설명 |
|----|------|
| `NONE` | Filter 없음 (베이스라인 측정용) |
| `TOKEN_BUCKET` | Token Bucket Algorithm |
| `FIXED_WINDOW` | Fixed Window Counter Algorithm |
| `SLIDING_WINDOW` | Sliding Window Log Algorithm |
| `LEAKY_BUCKET` | Leaky Bucket Algorithm |

---

## 알고리즘 설명

### Token Bucket
- 버킷에 토큰이 있으면 통과, 없으면 차단
- 1초마다 tps-limit개 토큰 충전
- **버스트 허용**: 순간적으로 토큰이 많으면 한꺼번에 여러 요청 통과 가능

### Fixed Window Counter
- 1초 단위 고정 창(window)에서 요청 수를 카운트
- 창 안에서 tps-limit 초과 시 차단
- **창 경계 문제**: 창이 바뀌는 순간 순간적으로 2배 통과 가능

### Sliding Window Log
- 현재 시점 기준 과거 1초 안의 요청 수를 계산
- Fixed Window의 창 경계 문제를 보완
- 동시 폭발 트래픽에서는 Fixed Window와 결과 유사

### Leaky Bucket
- 버킷에 요청이 쌓이면 일정 속도로 흘려보냄
- tps-limit: 15면 66ms마다 1개씩 처리
- **버스트 허용 없음**: 항상 균등하게 제어

---

## 테스트 방법

서버 실행 후 아래 명령어로 200개 동시 요청을 보냅니다.

```bash
result=$(for i in {1..200}; do
  curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/request &
done
wait)
echo "$result" | sort | uniq -c
```

### 결과 읽는 법

```
같은 응답 코드가 여러 줄 나오면 합산해서 읽습니다.

예시)
  1 200
 69 200   → 200(성공) 합산: 70개 (35%)
130 500   → 500(실패): 130개 (65%)
155 429   → 429(차단): 155개
```

| 응답 코드 | 의미 |
|-----------|------|
| `200` | Filter 통과 + 커넥션 획득 + 쿼리 처리 완료 |
| `500` | Filter 통과했지만 커넥션 풀 고갈로 250ms 초과 → Timeout과 같은 경험 |
| `429` | Filter가 tps-limit 초과 판단해서 즉시 차단 → 사용자 즉시 안내 |

> 500이 줄고 429가 늘어날수록 Filter가 효과적으로 동작하는 것이고,  
> 500이 0에 가까울수록 커넥션 풀 보호가 잘 된 것입니다.

---

## 실험 결과

### 베이스라인 (strategy: NONE)

```
성공(200): 35%   실패(500): 65%   차단(429): 0%
```

### tps-limit별 비교 (실측값)

| 전략 | tps-limit | 성공(200) | 실패(500) | 차단(429) |
|------|-----------|-----------|-----------|-----------|
| 아무것도 안 함 | - | 35% | 65% | 0% |
| Token Bucket | 15 | 15% | 7% | 77% |
| Token Bucket | 20 | 24% | 12% | 63% |
| Token Bucket | 25 | 40% | 15% | 44% |
| Fixed Window | 15 | 15% | 7% | 77% |
| Fixed Window | 20 | 15% | 15% | 70% |
| Fixed Window | 25 | 18% | 19% | 62% |
| Sliding Window | 15 | 15% | 7% | 77% |
| Sliding Window | 20 | 17% | 12% | 70% |
| Sliding Window | 25 | 19% | 18% | 62% |
| **Leaky Bucket** | **15** | **20%** | **4%** | **75%** |
| **Leaky Bucket** | **20** | **24%** | **7%** | **68%** |
| **Leaky Bucket** | **25** | **29%** | **12%** | **59%** |

---

## 결론

### 실험을 통해 알게 된 것

1. **Filter 트래픽 제어는 실제로 효과가 있습니다.**  
   65% 실패하던 상황에서 Filter를 붙이면 실패를 크게 줄일 수 있었습니다.

2. **Leaky Bucket이 전 구간에서 가장 안정적입니다.**  
   나가는 속도를 고정해서 커넥션 풀을 가장 균등하게 보호했습니다.

3. **tps-limit이 서버 처리 능력을 초과하면 실패가 급증합니다.**  
   tps-limit 25 (처리 능력 20개 초과) 구간에서 모든 전략의 실패가 늘어났습니다.

### 한계

- 실제 은행은 L4/L7 하드웨어 스위치 사용 → 동일 환경 재현 불가
- 채널계-계정계 중간 계층 생략
- 상태를 서버 메모리에만 저장 → 멀티 서버 환경에서는 Redis 같은 공유 상태저장소 필요

### 다음 단계

- Circuit Breaker 추가로 Hang 감지 자동화
- Toxiproxy를 활용한 더 실제에 가까운 Hang 재현
- Redis 기반 분산 상태저장소 적용

---

## 주의사항

- 테스트는 Git Bash 환경에서 진행
- 같은 응답 코드가 여러 줄 나오면 합산해서 읽어야 함
- 매 실행마다 결과가 조금씩 다를 수 있음 (curl 병렬 실행 특성상)
- 전략 변경 후 반드시 서버 재시작 필요
