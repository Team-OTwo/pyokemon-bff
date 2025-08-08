# Pyokemon BFF (Backend For Frontend)

## 프로젝트 개요

Pyokemon BFF는 티켓 예매 시스템의 프론트엔드와 백엔드 마이크로서비스 사이에서 중개 역할을 하는 BFF(Backend For Frontend) 서비스입니다. 이 서비스는 클라이언트 요청을 받아 필요한 데이터를 여러 마이크로서비스에서 조합하여 제공합니다.

## 주요 기능

1. **테넌트 예매 현황**: 테넌트별 예매 현황 조회
2. **사용자 마이페이지 내 예약**: 사용자별 예매 내역 조회
3. **사용자 예매 상세**: 특정 예매 건에 대한 상세 정보 조회
4. **실시간 좌석 선택**: 폴링 기반의 실시간 좌석 선택 기능

## 기술 스택

- Java 21
- Spring Boot
- Spring WebFlux (WebClient)
- Redis (좌석 홀드/락 관리)
- Gradle

## 아키텍처

BFF 패턴을 적용하여 다음과 같은 마이크로서비스와 통신합니다:

- `event-service`: 공연 정보, 좌석 정보, 공연장 정보 제공
- `booking-service`: 예매 정보 관리
- `account-service`: 사용자 정보 관리
- `payment-service`: 결제 정보 관리

## 프로젝트 구조

```
pyokemon-bff/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── pyokemon/
│   │   │           └── bff/
│   │   │               ├── config/              # 설정 클래스
│   │   │               │   ├── CacheConfig.java
│   │   │               │   └── WebClientConfig.java
│   │   │               │   └── RedisConfig.java
│   │   │               ├── controller/          # API 컨트롤러
│   │   │               │   ├── BookingController.java
│   │   │               │   ├── BookingDetailController.java
│   │   │               │   ├── MyPageBookingController.java
│   │   │               │   └── SeatSelectionController.java
│   │   │               ├── dto/                 # 데이터 전송 객체
│   │   │               │   ├── external/        # 외부 서비스 DTO
│   │   │               │   └── response/        # 응답 DTO
│   │   │               ├── exception/           # 예외 처리
│   │   │               │   └── GlobalExceptionHandler.java
│   │   │               ├── filter/              # 필터
│   │   │               │   └── LoggingFilter.java
│   │   │               ├── service/             # 비즈니스 로직
│   │   │               │   ├── BookingDetailService.java
│   │   │               │   ├── BookingService.java
│   │   │               │   ├── MyPageBookingService.java
│   │   │               │   └── SeatSelectionService.java
│   │   │               └── PyokemonBffApplication.java
│   │   └── resources/
│   │       └── application.yml
│   └── test/
│       └── java/
│           └── com/
│               └── pyokemon/
│                   └── bff/
│                       └── PyokemonBffApplicationTests.java
├── build.gradle
├── settings.gradle
└── README.md
```

## API 엔드포인트

### 1. 테넌트 예매 현황

- **GET** `/api/v1/bookings`
- **Query Parameters**:
  - `account_id`: 테넌트 ID
  - `event_schedule_id`: 공연 일정 ID
- **응답 예시**:

```json
[
  {
    "bookingId": 1001,
    "userName": "가나다",
    "eventTitle": "오아시스 내한",
    "eventDate": "2025-08-06",
    "venueName": "서울 올림픽 체조경기장",
    "seat": { "className": "VIP", "floor": 1, "row": "D열", "col": "9" },
    "thumbnailUrl": "url::",
    "totalPrice": 151000,
    "status": "결제완료"
  }
]
```

### 2. 사용자 마이페이지 내 예약

- **GET** `/api/v1/mypage/bookings`
- **Query Parameters**:
  - `account_id`: 사용자 ID
- **응답 예시**:

```json
[
  {
    "bookingId": 1001,
    "eventTitle": "TOMORROW X TOGETHER WORLD TOUR",
    "eventDate": "2025.08.22(금) 16:00",
    "venueName": "잠실 종합 운동장",
    "thumbnailUrl": "https://cdn.example.com/poster/txt.jpg",
    "totalPrice": 198000,
    "status": "예매 완료"
  }
]
```

### 3. 사용자 예매 상세

- **GET** `/api/v1/bookings/{booking_id}/details`
- **Path Parameters**:
  - `booking_id`: 예매 ID
- **Query Parameters**:
  - `account_id`: 사용자 ID
- **응답 예시**:

```json
{
  "bookingId": 12345,
  "status": "CONFIRMED",
  "createdAt": "2025-08-06T14:00:00Z",
  "user": { "name": "홍길동" },
  "event": {
    "title": "레미제라블",
    "thumbnailUrl": "https://image.cdn.com/event/thumbnail.jpg",
    "eventDate": "2025-08-20",
    "venue": { "name": "예술의전당" }
  },
  "seat": { "className": "VIP", "floor": 1, "row": "D열", "col": "9번" },
  "payment": {
    "method": "신용카드",
    "status": "PAID",
    "paidAt": "2025-08-01T10:30:00Z",
    "amount": 120000
  }
}
```

### 4. 실시간 좌석 선택

- **GET** `/api/v1/events/{event_schedule_id}/seats`
- **Path Parameters**:
  - `event_schedule_id`: 공연 일정 ID
- **Query Parameters**:
  - `account_id`: 사용자 ID
- **초기 응답 예시**:

```json
{
  "event": {
    "eventScheduleId": 67890,
    "venue": { "name": "예술의전당" }
  },
  "seatClasses": [
    {
      "className": "VIP",
      "priority": 1,
      "price": 120000,
      "availableSeats": 50,
      "totalSeats": 100
    },
    {
      "className": "R",
      "priority": 2,
      "price": 80000,
      "availableSeats": 80,
      "totalSeats": 150
    }
  ]
}
```

- **폴링 응답 예시** (증분 업데이트):

```json
{
  "eventScheduleId": 67890,
  "lastUpdatedAt": "2025-08-08T13:06:00Z",
  "changedSeats": [
    { "seatId": "VIP-D-10", "status": "BOOKED" },
    { "seatId": "R-A-12", "status": "BOOKED" }
  ]
}
```

## 캐싱 전략

- Redis를 활용한 좌석 홀드/락 관리 (TTL 기반)
- 예매 완료된 좌석은 DB에 저장, 필요시 Redis Set으로 캐싱
- 폴링 주기: 2-3초
- 증분 응답은 `lastUpdatedAt` 기반으로 변경된 좌석만 전송

## 실행 방법

```bash
./gradlew bootRun
```

## 빌드 방법

```bash
./gradlew build
```
