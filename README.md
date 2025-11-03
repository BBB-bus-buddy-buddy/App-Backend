# BusBuddyBuddy (BBB) 시스템 아키텍처 다이어그램

## 목차
1. [전체 시스템 아키텍처](#1-전체-시스템-아키텍처)
2. [백엔드 레이어 아키텍처](#2-백엔드-레이어-아키텍처)
3. [API 엔드포인트 맵](#3-api-엔드포인트-맵)
4. [데이터 흐름 다이어그램](#4-데이터-흐름-다이어그램)
5. [도메인 모델 다이어그램](#5-도메인-모델-다이어그램)
6. [실시간 통신 아키텍처](#6-실시간-통신-아키텍처)
7. [인증 및 보안 흐름](#7-인증-및-보안-흐름)
8. [모바일 앱 아키텍처](#8-모바일-앱-아키텍처)
9. [배포 아키텍처](#9-배포-아키텍처)

---

## 1. 전체 시스템 아키텍처

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          BusBuddyBuddy System                            │
└─────────────────────────────────────────────────────────────────────────┘

┌──────────────────────┐                    ┌──────────────────────────┐
│   Mobile App (iOS)   │                    │  Mobile App (Android)    │
│                      │                    │                          │
│  ┌────────────────┐  │                    │  ┌────────────────────┐  │
│  │ React Native   │  │                    │  │  React Native      │  │
│  │ TypeScript     │  │                    │  │  TypeScript        │  │
│  │ Zustand        │  │                    │  │  Zustand           │  │
│  │ Axios          │  │                    │  │  Axios             │  │
│  │ Naver Map SDK  │  │                    │  │  Naver Map SDK     │  │
│  └────────────────┘  │                    │  └────────────────────┘  │
└──────────┬───────────┘                    └────────────┬─────────────┘
           │                                             │
           │         REST API (HTTP/HTTPS)               │
           │         WebSocket (Real-time)               │
           └─────────────────┬───────────────────────────┘
                             │
                             ▼
              ┌──────────────────────────────┐
              │     API Gateway / LB         │
              │   (http://devse.kr:12589)    │
              └──────────────┬───────────────┘
                             │
                             ▼
┌────────────────────────────────────────────────────────────────────────┐
│                      Backend Server (_AppBackendBBB)                    │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │                    Spring Boot 3.3.1 (Java 21)                   │  │
│  ├──────────────────────────────────────────────────────────────────┤  │
│  │  Security Layer                                                  │  │
│  │  ┌────────────────┬─────────────────┬──────────────────────┐    │  │
│  │  │ JWT Filter     │ OAuth2 Handler  │ Token Exception      │    │  │
│  │  └────────────────┴─────────────────┴──────────────────────┘    │  │
│  ├──────────────────────────────────────────────────────────────────┤  │
│  │  Controller Layer (13 Controllers)                              │  │
│  │  ┌──────┬──────┬──────┬──────┬──────┬──────┬──────┬──────┐     │  │
│  │  │ Auth │ Bus  │Route │Station│Driver│User │Admin │ ...  │     │  │
│  │  └──────┴──────┴──────┴──────┴──────┴──────┴──────┴──────┘     │  │
│  ├──────────────────────────────────────────────────────────────────┤  │
│  │  Service Layer (14 Services)                                    │  │
│  │  ┌──────┬──────┬──────┬──────┬──────┬──────┬──────┬──────┐     │  │
│  │  │AuthSvc│BusSvc│RouteSvc│StationSvc│DriverSvc│...│       │  │  │
│  │  └──────┴──────┴──────┴──────┴──────┴──────┴──────┴──────┘     │  │
│  ├──────────────────────────────────────────────────────────────────┤  │
│  │  Repository Layer (8 Repositories)                              │  │
│  │  ┌──────┬──────┬──────┬──────┬──────┬──────┬──────┬──────┐     │  │
│  │  │BusRepo│RouteRepo│StationRepo│UserRepo│ ...│             │  │  │
│  │  └──────┴──────┴──────┴──────┴──────┴──────┴──────┴──────┘     │  │
│  ├──────────────────────────────────────────────────────────────────┤  │
│  │  WebSocket Handlers                                             │  │
│  │  ┌──────────────────────┬─────────────────────────────┐        │  │
│  │  │ BusDriverWS Handler  │ BusPassengerWS Handler      │        │  │
│  │  └──────────────────────┴─────────────────────────────┘        │  │
│  └──────────────────────────────────────────────────────────────────┘  │
└────────────────────────────┬───────────────────────────────────────────┘
                             │
                             ▼
              ┌──────────────────────────────┐
              │         MongoDB              │
              │  ┌────────────────────────┐  │
              │  │ Collections:           │  │
              │  │ - users                │  │
              │  │ - organizations        │  │
              │  │ - buses                │  │
              │  │ - routes               │  │
              │  │ - stations             │  │
              │  │ - drivers              │  │
              │  │ - busOperations        │  │
              │  │ - tokens               │  │
              │  └────────────────────────┘  │
              └──────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                        External Services                                │
│  ┌──────────────┐  ┌──────────────┐                                     │
│  │ Google OAuth │  │ Naver Map API│                                     │
│  │   Provider   │  │              │                                     │
│  └──────────────┘  └──────────────┘                                     │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 2. 백엔드 레이어 아키텍처

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Request Flow                                  │
└─────────────────────────────────────────────────────────────────────┘

   Client Request (HTTP/WebSocket)
            │
            ▼
   ┌─────────────────┐
   │ Filter Chain    │ ← TokenExceptionFilter
   │                 │ ← JwtAuthenticationFilter
   │                 │ ← Spring Security Filters
   └────────┬────────┘
            │
            ▼
   ┌─────────────────────────────────────────────────────────────┐
   │                    Controller Layer                          │
   ├─────────────────────────────────────────────────────────────┤
   │  @RestController                                            │
   │  @RequestMapping("/api/...")                                │
   │  @PreAuthorize("hasRole('...')")                           │
   │                                                             │
   │  Controllers:                                               │
   │  • AuthController          - 인증/사용자 관리               │
   │  • BusController           - 버스 CRUD                      │
   │  • RouteController         - 노선 관리                      │
   │  • StationController       - 정류장 관리                    │
   │  • DriverController        - 운전자 관리                    │
   │  • BusOperationController  - 운행 계획                      │
   │  • OrganizationController  - 조직 관리                      │
   │  • UserController          - 사용자 관리                    │
   │  • AdminController         - 관리자 기능                    │
   │  • StaffController         - 스태프 관리                    │
   │  • DriveController         - 운행 관리                      │
   │  • KakaoApiController      - Kakao API 연동                │
   │  • AppDownloadController   - 앱 다운로드                    │
   └────────┬────────────────────────────────────────────────────┘
            │
            ▼
   ┌─────────────────────────────────────────────────────────────┐
   │                     Service Layer                            │
   ├─────────────────────────────────────────────────────────────┤
   │  @Service                                                   │
   │  Business Logic & Validation                                │
   │                                                             │
   │  Services:                                                  │
   │  • AuthService              - 인증 로직                    │
   │  • BusService               - 버스 비즈니스 로직           │
   │  • RouteService             - 노선 관리 로직               │
   │  • StationService           - 정류장 관리                  │
   │  • DriverService            - 운전자 관리                  │
   │  • BusOperationService      - 운행 계획 관리               │
   │  • OrganizationService      - 조직 관리                    │
   │  • UserService              - 사용자 관리                  │
   │  • DriveService             - 운행 관리                    │
   │  • PassengerLocationService - 승객 위치 서비스             │
   │  • TokenService             - 토큰 관리                    │
   │  • KakaoApiService          - Kakao API                    │
   │  • CustomOAuth2UserService  - OAuth2 사용자 서비스         │
   │  • PasswordEncoderService   - 암호화 서비스                │
   └────────┬────────────────────────────────────────────────────┘
            │
            ▼
   ┌─────────────────────────────────────────────────────────────┐
   │                   Repository Layer                           │
   ├─────────────────────────────────────────────────────────────┤
   │  @Repository                                                │
   │  extends MongoRepository / ReactiveMongoRepository          │
   │                                                             │
   │  Repositories:                                              │
   │  • BusRepository           - Bus 데이터 접근               │
   │  • RouteRepository         - Route 데이터 접근             │
   │  • StationRepository       - Station 데이터 접근           │
   │  • UserRepository          - User 데이터 접근              │
   │  • DriverRepository        - Driver 데이터 접근            │
   │  • BusOperationRepository  - BusOperation 접근             │
   │  • OrganizationRepository  - Organization 접근             │
   │  • TokenRepository         - Token 접근                    │
   └────────┬────────────────────────────────────────────────────┘
            │
            ▼
   ┌─────────────────────────────────────────────────────────────┐
   │                      MongoDB                                 │
   ├─────────────────────────────────────────────────────────────┤
   │  NoSQL Document Database                                    │
   │  • Reactive & Synchronous Support                           │
   │  • BSON Documents                                           │
   │  • Indexing for Performance                                 │
   └─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│                    Cross-Cutting Concerns                            │
├─────────────────────────────────────────────────────────────────────┤
│  • Exception Handling (Global @ExceptionHandler)                   │
│  • Logging (SLF4J + Logback)                                        │
│  • Security (JWT + OAuth2)                                          │
│  • Validation (Jakarta Validation)                                 │
│  • API Documentation (Swagger/OpenAPI)                              │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 3. API 엔드포인트 맵

```
┌────────────────────────────────────────────────────────────────────┐
│                      API Endpoints Structure                        │
└────────────────────────────────────────────────────────────────────┘

/api
├── /auth                              [인증 관련]
│   ├── GET    /user                  → 사용자 정보 조회
│   ├── POST   /logout                → 로그아웃
│   └── POST   /withdrawal            → 회원 탈퇴
│
├── /bus                               [버스 관리]
│   ├── GET    /                      → 조직별 모든 버스 조회
│   ├── GET    /{busNumber}           → 특정 버스 조회
│   ├── GET    /station/{stationId}   → 정류장 경유 버스 조회
│   ├── GET    /stations-detail/{busNumber} → 버스 정류장 상세
│   ├── GET    /seats/{busNumber}     → 버스 좌석 조회
│   ├── GET    /location/{busNumber}  → 버스 위치 조회
│   ├── GET    /real-number/{busRealNumber} → 실제 번호로 조회
│   ├── GET    /operating             → 운행 중인 버스
│   ├── POST   /                      → 버스 등록 [STAFF]
│   ├── PUT    /                      → 버스 수정 [STAFF]
│   ├── PUT    /{busNumber}/operate   → 운행 상태 변경 [STAFF]
│   └── DELETE /{busNumber}           → 버스 삭제 [STAFF]
│
├── /routes                            [노선 관리]
│   ├── GET    /                      → 라우트 목록 (검색 가능)
│   ├── GET    /{id}                  → 라우트 상세 조회
│   ├── POST   /                      → 라우트 생성 [STAFF]
│   ├── PUT    /                      → 라우트 수정 [STAFF]
│   └── DELETE /{id}                  → 라우트 삭제 [STAFF]
│
├── /station                           [정류장 관리]
│   ├── GET    /                      → 정류장 목록 (검색 가능)
│   ├── POST   /                      → 정류장 등록 [STAFF]
│   ├── PUT    /{id}                  → 정류장 수정 [STAFF]
│   └── DELETE /{id}                  → 정류장 삭제 [STAFF]
│
├── /driver                            [운전자 관리]
│   └── ...                           → 운전자 관련 엔드포인트
│
├── /organization                      [조직 관리]
│   └── ...                           → 조직 관련 엔드포인트
│
├── /operation                         [운행 계획]
│   └── ...                           → 운행 계획 관련
│
├── /user                              [사용자 관리]
│   └── ...                           → 사용자 관련 엔드포인트
│
├── /admin                             [관리자 기능]
│   └── ...                           → 관리자 전용 기능
│
└── /staff                             [스태프 관리]
    └── ...                           → 스태프 관련 엔드포인트

┌────────────────────────────────────────────────────────────────────┐
│                      WebSocket Endpoints                            │
└────────────────────────────────────────────────────────────────────┘

/ws
├── /driver                            → 운전자 실시간 위치 전송
└── /passenger                         → 승객 실시간 버스 정보 수신

┌────────────────────────────────────────────────────────────────────┐
│                         권한 레벨                                   │
├────────────────────────────────────────────────────────────────────┤
│  [PUBLIC]  - 인증 불필요                                           │
│  [USER]    - 일반 사용자 (승객)                                    │
│  [DRIVER]  - 운전자                                                │
│  [STAFF]   - 관리자                                                │
│  [ADMIN]   - 최고 관리자                                           │
└────────────────────────────────────────────────────────────────────┘
```

---

## 4. 데이터 흐름 다이어그램

### 4.1 일반 HTTP 요청 흐름

```
┌──────────────┐
│ Mobile App   │
│ (React Native)│
└──────┬───────┘
       │
       │ 1. HTTP Request
       │    Authorization: Bearer {JWT_TOKEN}
       │    Content-Type: application/json
       │
       ▼
┌──────────────────────────────────────────────────────────────┐
│                  Spring Security Filter Chain                 │
├──────────────────────────────────────────────────────────────┤
│  2. TokenExceptionFilter                                     │
│     └─> JWT 예외 사전 처리                                   │
│                                                              │
│  3. JwtAuthenticationFilter                                  │
│     └─> JWT 토큰 검증                                        │
│     └─> 사용자 인증 정보 설정                                │
│                                                              │
│  4. Spring Security Filters                                  │
│     └─> 권한 검증 (@PreAuthorize)                           │
└──────────────────────┬───────────────────────────────────────┘
                       │
                       │ 5. Authenticated Request
                       │
                       ▼
        ┌──────────────────────────────┐
        │      Controller              │
        │  @AuthenticationPrincipal    │
        │  OAuth2User principal        │
        └──────────┬───────────────────┘
                   │
                   │ 6. Service Method Call
                   │    (Business Logic)
                   │
                   ▼
        ┌──────────────────────────────┐
        │        Service               │
        │  - Validation                │
        │  - Business Logic            │
        │  - Data Transformation       │
        └──────────┬───────────────────┘
                   │
                   │ 7. Repository Method Call
                   │    (Data Access)
                   │
                   ▼
        ┌──────────────────────────────┐
        │      Repository              │
        │  MongoRepository<T, ID>      │
        └──────────┬───────────────────┘
                   │
                   │ 8. MongoDB Query
                   │
                   ▼
        ┌──────────────────────────────┐
        │        MongoDB               │
        │  - Find / Insert             │
        │  - Update / Delete           │
        └──────────┬───────────────────┘
                   │
                   │ 9. Result
                   │
                   ▼
        ┌──────────────────────────────┐
        │     Response DTO             │
        │  ApiResponse<T>              │
        │  {                           │
        │    data: T,                  │
        │    message: String           │
        │  }                           │
        └──────────┬───────────────────┘
                   │
                   │ 10. HTTP Response
                   │     Status: 200 OK
                   │     Content-Type: application/json
                   │
                   ▼
        ┌──────────────────────────────┐
        │      Mobile App              │
        │  - Update UI                 │
        │  - Update State (Zustand)    │
        └──────────────────────────────┘
```

### 4.2 실시간 WebSocket 통신 흐름

```
┌─────────────────┐                          ┌─────────────────┐
│  Driver App     │                          │  Passenger App  │
│  (운전자)       │                          │  (승객)         │
└────────┬────────┘                          └────────┬────────┘
         │                                            │
         │ 1. WebSocket Connect                      │
         │    /ws/driver                             │
         │                                           │
         ▼                                           │
┌─────────────────────────────┐                     │
│  BusDriverWebSocketHandler  │                     │
│  - Session Management       │                     │
│  - Message Handler          │                     │
└────────┬────────────────────┘                     │
         │                                           │
         │ 2. Location Update Message                │
         │    {                                      │
         │      busNumber: "123",                    │
         │      latitude: 37.123,                    │
         │      longitude: 127.456,                  │
         │      timestamp: "..."                     │
         │    }                                      │
         │                                           │
         ▼                                           │
┌─────────────────────────────┐                     │
│       BusService            │                     │
│  - Update Bus Location      │                     │
│  - Calculate ETA            │                     │
│  - Update Station Status    │                     │
└────────┬────────────────────┘                     │
         │                                           │
         │ 3. Save to MongoDB                        │
         │                                           │
         ▼                                           │
┌─────────────────────────────┐                     │
│        MongoDB              │                     │
│  buses.location = {         │                     │
│    type: "Point",           │                     │
│    coordinates: [lng, lat]  │                     │
│  }                          │                     │
└────────┬────────────────────┘                     │
         │                                           │
         │ 4. Event Publish                          │
         │                                           │
         ▼                                           │
┌─────────────────────────────┐                     │
│  BusStatusEventListener     │                     │
│  - Listen to Bus Updates    │                     │
│  - Broadcast to Subscribers │                     │
└────────┬────────────────────┘                     │
         │                                           │
         │ 5. Broadcast Message                      │
         │                                           │
         ▼                                           ▼
┌──────────────────────────────┐         ┌──────────────────────┐
│ BusPassengerWebSocketHandler │◄────────│ 6. WebSocket Connect │
│  - Session Management        │         │    /ws/passenger     │
│  - Send Updates to Clients   │         │                      │
└────────┬─────────────────────┘         └──────────────────────┘
         │
         │ 7. Real-time Update Message
         │    {
         │      busNumber: "123",
         │      currentLocation: {...},
         │      nextStation: "정류장A",
         │      eta: "2분",
         │      seats: { available: 15, total: 45 }
         │    }
         │
         ▼
┌─────────────────┐
│  Passenger App  │
│  - Update Map   │
│  - Update ETA   │
│  - Update UI    │
└─────────────────┘
```

---

## 5. 도메인 모델 다이어그램

```
┌────────────────────────────────────────────────────────────────────┐
│                      Domain Model Relationships                     │
└────────────────────────────────────────────────────────────────────┘

                    ┌─────────────────────┐
                    │   Organization      │
                    ├─────────────────────┤
                    │ - id: String        │
                    │ - name: String      │
                    │ - type: String      │
                    │ - address: String   │
                    │ - contactEmail      │
                    │ - createdAt         │
                    └──────────┬──────────┘
                               │
                               │ 1:N
                ┌──────────────┼──────────────────┐
                │              │                  │
                ▼              ▼                  ▼
    ┌───────────────┐  ┌──────────────┐  ┌──────────────┐
    │     User      │  │    Route     │  │   Station    │
    ├───────────────┤  ├──────────────┤  ├──────────────┤
    │ - id          │  │ - id         │  │ - id         │
    │ - email       │  │ - routeName  │  │ - name       │
    │ - name        │  │ - routeType  │  │ - location   │
    │ - role        │  │ - stations[] │  │   - lat/lng  │
    │ - orgId       │  │ - orgId      │  │ - address    │
    │ - provider    │  │ - distance   │  │ - orgId      │
    └───────┬───────┘  └──────┬───────┘  └──────────────┘
            │                 │                  ▲
            │                 │                  │
            │ 1:1             │ N:M              │
            │                 │                  │
            ▼                 ▼                  │
    ┌───────────────┐  ┌──────────────┐         │
    │    Driver     │  │RouteStation  │─────────┘
    ├───────────────┤  ├──────────────┤
    │ - id          │  │ - routeId    │
    │ - userId      │  │ - stationId  │
    │ - licenseNo   │  │ - sequence   │
    │ - phone       │  │ - distance   │
    │ - status      │  └──────────────┘
    └───────┬───────┘
            │
            │ 1:1
            │
            ▼
    ┌───────────────────────┐
    │        Bus            │
    ├───────────────────────┤
    │ - id                  │
    │ - busNumber           │
    │ - busRealNumber       │
    │ - routeId        ────────┐
    │ - driverId            │  │
    │ - orgId               │  │
    │ - currentLocation     │  │
    │ - isOperate           │  │
    │ - seatInfo            │  │
    │   - total: Int        │  │
    │   - available: Int    │  │
    │ - lastUpdated         │  │
    └───────┬───────────────┘  │
            │                  │
            │ 1:N              │ N:1
            │                  │
            ▼                  ▼
    ┌───────────────────┐  ┌──────────────┐
    │  BusOperation     │  │    Route     │
    ├───────────────────┤  │  (위에서 정의)│
    │ - id              │  └──────────────┘
    │ - busId           │
    │ - routeId         │
    │ - startTime       │
    │ - endTime         │
    │ - currentStation  │
    │ - passedStations[]│
    │ - status          │
    │ - createdAt       │
    └───────────────────┘

┌────────────────────────────────────────────────────────────────────┐
│                      Key Relationships                              │
├────────────────────────────────────────────────────────────────────┤
│  Organization (1) ──── (N) User                                    │
│  Organization (1) ──── (N) Bus                                     │
│  Organization (1) ──── (N) Route                                   │
│  Organization (1) ──── (N) Station                                 │
│                                                                     │
│  Route (N) ──── (M) Station  (through RouteStation)               │
│  Bus (N) ──── (1) Route                                           │
│  Bus (1) ──── (1) Driver                                          │
│  Bus (1) ──── (N) BusOperation                                    │
│  User (1) ──── (1) Driver (optional)                              │
└────────────────────────────────────────────────────────────────────┘
```

---

## 6. 실시간 통신 아키텍처

```
┌────────────────────────────────────────────────────────────────────┐
│              WebSocket Real-time Communication Flow                 │
└────────────────────────────────────────────────────────────────────┘

┌─────────────────┐                                  ┌─────────────────┐
│  Driver Device  │                                  │Passenger Devices│
│                 │                                  │                 │
│ ┌─────────────┐ │                                  │ ┌─────────────┐ │
│ │GPS Tracker  │ │                                  │ │Map View     │ │
│ │Location API │ │                                  │ │ETA Display  │ │
│ └──────┬──────┘ │                                  │ └──────▲──────┘ │
│        │        │                                  │        │        │
└────────┼────────┘                                  └────────┼────────┘
         │                                                    │
         │ 1. Location Update (every 5s)                     │
         │    ws://server/ws/driver                          │
         │                                                    │
         ▼                                                    │
┌─────────────────────────────────────────────────────────┐ │
│           Spring Boot WebSocket Server                  │ │
│ ┌─────────────────────────────────────────────────────┐ │ │
│ │        WebSocket Configuration                      │ │ │
│ │  @EnableWebSocket                                   │ │ │
│ └─────────────────────────────────────────────────────┘ │ │
│                                                          │ │
│ ┌──────────────────────┐  ┌────────────────────────┐   │ │
│ │BusDriverWSHandler    │  │BusPassengerWSHandler   │   │ │
│ ├──────────────────────┤  ├────────────────────────┤   │ │
│ │• afterConnectionEst. │  │• afterConnectionEst.   │   │ │
│ │• handleTextMessage   │  │• handleTextMessage     │   │ │
│ │• handleTransportErr  │  │• handleTransportError  │   │ │
│ │• afterConnectionCls  │  │• afterConnectionClosed │   │ │
│ │                      │  │                        │   │ │
│ │ Session Pool:        │  │ Session Pool:          │   │ │
│ │ {busId → Session}    │  │ {userId → Session}     │   │ │
│ └──────────┬───────────┘  └────────────▲───────────┘   │ │
│            │                           │               │ │
└────────────┼───────────────────────────┼───────────────┘ │
             │                           │                 │
             │ 2. Process Location       │                 │
             │                           │                 │
             ▼                           │                 │
    ┌─────────────────┐                 │                 │
    │   BusService    │                 │                 │
    ├─────────────────┤                 │                 │
    │• updateLocation │                 │                 │
    │• calculateETA   │                 │                 │
    │• updateSeats    │                 │                 │
    └────────┬────────┘                 │                 │
             │                           │                 │
             │ 3. Save to DB             │                 │
             │                           │                 │
             ▼                           │                 │
    ┌─────────────────┐                 │                 │
    │    MongoDB      │                 │                 │
    │  buses          │                 │                 │
    │  collection     │                 │                 │
    └────────┬────────┘                 │                 │
             │                           │                 │
             │ 4. Event Trigger          │                 │
             │                           │                 │
             ▼                           │                 │
    ┌──────────────────────┐            │                 │
    │BusStatusEventListener│            │                 │
    ├──────────────────────┤            │                 │
    │• onBusLocationUpdate │            │                 │
    │• broadcastToPassengers───────────┘                 │
    └──────────────────────┘   5. Broadcast               │
                                                           │
                                  6. WebSocket Message     │
                                     {                     │
                                       busNumber,          │
                                       location,           │
                                       eta,                │
                                       seats               │
                                     }                     │
                                                           │
                                                           ▼
                                                  Update Passenger UI

┌────────────────────────────────────────────────────────────────────┐
│                   Message Flow Timeline                             │
├────────────────────────────────────────────────────────────────────┤
│  T+0s   : Driver GPS sends location                                │
│  T+0.1s : WebSocket receives message                               │
│  T+0.2s : BusService processes update                              │
│  T+0.3s : MongoDB saves location                                   │
│  T+0.4s : Event listener triggered                                 │
│  T+0.5s : Broadcast to all connected passengers                    │
│  T+0.6s : Passenger app receives update                            │
│  T+0.7s : UI updates (map marker moves, ETA refreshes)            │
└────────────────────────────────────────────────────────────────────┘
```

---

## 7. 인증 및 보안 흐름

```
┌────────────────────────────────────────────────────────────────────┐
│                    Authentication & Security Flow                   │
└────────────────────────────────────────────────────────────────────┘

┌─────────────────┐
│   Mobile App    │
│   (Login Page)  │
└────────┬────────┘
         │
         │ 1. User clicks "Google Login"
         │
         ▼
┌──────────────────────────────────────────────────────────────┐
│                   OAuth2 Login Flow                           │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  App ──────────────> Google OAuth2 Provider                 │
│         2. Redirect                                          │
│                                                              │
│  User authenticates with Google                              │
│  (Email & Password)                                          │
│                                                              │
│  Google ──────────> App                                      │
│         3. Authorization Code                                │
│                                                              │
└──────────────────────┬───────────────────────────────────────┘
                       │
                       │ 4. Exchange code for token
                       │    POST /oauth2/token
                       │
                       ▼
        ┌──────────────────────────────┐
        │  Spring Security OAuth2      │
        │  Authorization Server        │
        └──────────────┬───────────────┘
                       │
                       │ 5. Verify user
                       │
                       ▼
        ┌──────────────────────────────┐
        │  CustomOAuth2UserService     │
        │  - loadUser()                │
        │  - Extract user info         │
        │  - Check organization        │
        └──────────────┬───────────────┘
                       │
                       │ 6. Create/Update user
                       │
                       ▼
        ┌──────────────────────────────┐
        │      UserRepository          │
        │  - findByEmail()             │
        │  - save(user)                │
        └──────────────┬───────────────┘
                       │
                       │ 7. User authenticated
                       │
                       ▼
        ┌──────────────────────────────┐
        │  OAuth2LoginSuccessHandler   │
        │  - onAuthenticationSuccess() │
        └──────────────┬───────────────┘
                       │
                       │ 8. Generate JWT
                       │
                       ▼
        ┌──────────────────────────────┐
        │     JwtTokenProvider         │
        │  - generateToken()           │
        │  - setExpiration(7 days)     │
        │  - sign with secret key      │
        └──────────────┬───────────────┘
                       │
                       │ 9. Return JWT token
                       │    {
                       │      "token": "eyJhbGc...",
                       │      "expiresIn": 604800
                       │    }
                       │
                       ▼
        ┌──────────────────────────────┐
        │      Mobile App              │
        │  - Store in AsyncStorage     │
        │    await AsyncStorage        │
        │      .setItem('token', jwt)  │
        └──────────────┬───────────────┘
                       │
                       │ 10. Subsequent requests
                       │     with JWT in header
                       │
                       ▼

┌────────────────────────────────────────────────────────────────────┐
│              Authenticated Request Flow                             │
├────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  Mobile App                                                        │
│      │                                                             │
│      │ GET /api/bus                                                │
│      │ Authorization: Bearer eyJhbGc...                            │
│      │                                                             │
│      ▼                                                             │
│  ┌─────────────────────────────────────┐                          │
│  │  TokenExceptionFilter               │                          │
│  │  - Pre-process JWT exceptions       │                          │
│  └──────────────┬──────────────────────┘                          │
│                 │                                                  │
│                 ▼                                                  │
│  ┌─────────────────────────────────────┐                          │
│  │  JwtAuthenticationFilter            │                          │
│  │  1. Extract JWT from header         │                          │
│  │  2. Validate token                  │                          │
│  │     - Check expiration              │                          │
│  │     - Verify signature              │                          │
│  │  3. Extract user info               │                          │
│  │  4. Set SecurityContext             │                          │
│  └──────────────┬──────────────────────┘                          │
│                 │                                                  │
│                 ▼                                                  │
│  ┌─────────────────────────────────────┐                          │
│  │  Spring Security Filters            │                          │
│  │  - Check @PreAuthorize              │                          │
│  │  - hasRole('USER')                  │                          │
│  │  - hasRole('STAFF')                 │                          │
│  │  - hasRole('ADMIN')                 │                          │
│  └──────────────┬──────────────────────┘                          │
│                 │                                                  │
│                 │ ✓ Authorized                                     │
│                 │                                                  │
│                 ▼                                                  │
│  ┌─────────────────────────────────────┐                          │
│  │  Controller Method                  │                          │
│  │  @AuthenticationPrincipal           │                          │
│  │  OAuth2User principal               │                          │
│  └─────────────────────────────────────┘                          │
│                                                                     │
└────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────┐
│                      Security Components                            │
├────────────────────────────────────────────────────────────────────┤
│  JWT Structure:                                                    │
│  ┌──────────────────────────────────────────────────────────┐     │
│  │ Header                                                   │     │
│  │ {                                                        │     │
│  │   "alg": "HS256",                                        │     │
│  │   "typ": "JWT"                                           │     │
│  │ }                                                        │     │
│  ├──────────────────────────────────────────────────────────┤     │
│  │ Payload                                                  │     │
│  │ {                                                        │     │
│  │   "sub": "user@example.com",                            │     │
│  │   "organizationId": "org123",                           │     │
│  │   "role": "USER",                                        │     │
│  │   "iat": 1234567890,                                     │     │
│  │   "exp": 1235172690                                      │     │
│  │ }                                                        │     │
│  ├──────────────────────────────────────────────────────────┤     │
│  │ Signature                                                │     │
│  │ HMACSHA256(                                              │     │
│  │   base64UrlEncode(header) + "." +                       │     │
│  │   base64UrlEncode(payload),                             │     │
│  │   secret_key                                             │     │
│  │ )                                                        │     │
│  └──────────────────────────────────────────────────────────┘     │
│                                                                     │
│  Token Storage: AsyncStorage (Mobile)                             │
│  Token Lifetime: 7 days                                           │
│  Refresh Strategy: Re-login after expiration                      │
└────────────────────────────────────────────────────────────────────┘
```

---

## 8. 모바일 앱 아키텍처

```
┌────────────────────────────────────────────────────────────────────┐
│              Mobile App Architecture (React Native)                 │
└────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                          App.tsx                                 │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  Providers                                                │  │
│  │  ┌─────────────────────────────────────────────────────┐  │  │
│  │  │ SafeAreaProvider                                    │  │  │
│  │  │  └─> ToastProvider                                  │  │  │
│  │  │       └─> GlobalWebSocketProvider                   │  │  │
│  │  │            └─> NavigationContainer                  │  │  │
│  │  └─────────────────────────────────────────────────────┘  │  │
│  │                                                           │  │
│  │  Axios Interceptor (Global)                              │  │
│  │  ┌─────────────────────────────────────────────────────┐  │  │
│  │  │ Request:                                            │  │  │
│  │  │   - Add JWT token from AsyncStorage                │  │  │
│  │  │   - Set Authorization header                       │  │  │
│  │  └─────────────────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                      Navigation Stack                            │
├─────────────────────────────────────────────────────────────────┤
│  @react-navigation/native-stack                                 │
│                                                                  │
│  ┌──────────────┐                                               │
│  │ LoginPage    │ ────> EnterCodePage                          │
│  └──────────────┘            │                                  │
│                              │                                  │
│                              ▼                                  │
│                       LoadingPage                               │
│                              │                                  │
│                              ▼                                  │
│                    ┌─────────────────┐                          │
│                    │    HomePage     │ (Main)                   │
│                    └────────┬────────┘                          │
│                             │                                   │
│         ┌───────────────────┼────────────────────┐             │
│         │                   │                    │             │
│         ▼                   ▼                    ▼             │
│   RouteListPage      BusListPage          MyPage              │
│         │                   │                    │             │
│         │                   ▼                    │             │
│         │             BusRoutePage               │             │
│         │                   │                    │             │
│         │                   ▼                    │             │
│         │            BusSchedulePage             │             │
│         └───────────────────┴────────────────────┘             │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                    State Management (Zustand)                    │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌────────────────────┐  ┌────────────────────────┐            │
│  │  useBusStore       │  │  useBoardingStore      │            │
│  ├────────────────────┤  ├────────────────────────┤            │
│  │ - buses: Bus[]     │  │ - boardingInfo         │            │
│  │ - selectedBus      │  │ - passengers[]         │            │
│  │ - setBuses()       │  │ - setBoardingInfo()    │            │
│  │ - selectBus()      │  │ - addPassenger()       │            │
│  │ - updateBusLoc()   │  │ - removePassenger()    │            │
│  └────────────────────┘  └────────────────────────┘            │
│                                                                  │
│  ┌────────────────────┐  ┌────────────────────────┐            │
│  │  useModalStore     │  │ useSelectedStationStore│            │
│  ├────────────────────┤  ├────────────────────────┤            │
│  │ - isOpen: boolean  │  │ - station: Station     │            │
│  │ - modalType        │  │ - setStation()         │            │
│  │ - openModal()      │  │ - clearStation()       │            │
│  │ - closeModal()     │  │ - getStationInfo()     │            │
│  └────────────────────┘  └────────────────────────┘            │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                      API Service Layer                           │
├─────────────────────────────────────────────────────────────────┤
│  src/api/services/                                              │
│                                                                  │
│  ┌─────────────────┐  ┌─────────────────┐  ┌────────────────┐  │
│  │ authService     │  │ busService      │  │ routeService   │  │
│  ├─────────────────┤  ├─────────────────┤  ├────────────────┤  │
│  │ - login()       │  │ - getBuses()    │  │ - getRoutes()  │  │
│  │ - logout()      │  │ - getBusById()  │  │ - getRouteById()│ │
│  │ - getUser()     │  │ - updateBus()   │  │ - searchRoutes()│ │
│  │ - withdraw()    │  │ - getBusSeats() │  │ - createRoute()│  │
│  └─────────────────┘  │ - getBusLoc()   │  └────────────────┘  │
│                       └─────────────────┘                        │
│  ┌─────────────────┐  ┌─────────────────┐  ┌────────────────┐  │
│  │stationService   │  │ userService     │  │websocketService│  │
│  ├─────────────────┤  ├─────────────────┤  ├────────────────┤  │
│  │ - getStations() │  │ - getUserInfo() │  │ - connect()    │  │
│  │ - searchStation()│  │ - updateUser()  │  │ - disconnect() │  │
│  │ - createStation()│  │ - deleteUser()  │  │ - subscribe()  │  │
│  └─────────────────┘  └─────────────────┘  │ - sendMessage()│  │
│                                             └────────────────┘  │
│  All services use apiClient (Axios instance)                   │
│  Base URL: http://devse.kr:12589                               │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                     Component Structure                          │
├─────────────────────────────────────────────────────────────────┤
│  src/components/                                                │
│                                                                  │
│  common/                  ← Reusable UI Components             │
│  ├─ Button.tsx                                                  │
│  ├─ Card.tsx                                                    │
│  ├─ Input.tsx                                                   │
│  ├─ Text.tsx                                                    │
│  └─ Toast.tsx                                                   │
│                                                                  │
│  Map/                     ← Map Integration                     │
│  └─ MapView.tsx           (Naver Map SDK)                       │
│                                                                  │
│  SearchBar/               ← Search Components                   │
│  ├─ SearchBar.tsx                                               │
│  ├─ FullScreenSearchModal.tsx                                   │
│  └─ CommonSearchBarModule.tsx                                   │
│                                                                  │
│  Station/                 ← Station-related                     │
│  ├─ StationSearch.tsx                                           │
│  ├─ StationList.tsx                                             │
│  ├─ StationDetail.tsx                                           │
│  ├─ StationPanel.tsx                                            │
│  └─ SearchStationModal.tsx                                      │
│                                                                  │
│  └─ Footer.tsx, MyPage.tsx                                      │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                    WebSocket Integration                         │
├─────────────────────────────────────────────────────────────────┤
│  GlobalWebSocketProvider                                        │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  useEffect(() => {                                        │  │
│  │    const ws = new WebSocket('ws://devse.kr:12589/ws');   │  │
│  │                                                           │  │
│  │    ws.onmessage = (event) => {                           │  │
│  │      const data = JSON.parse(event.data);                │  │
│  │      // Update Zustand store                             │  │
│  │      useBusStore.setState({ buses: data.buses });        │  │
│  │    };                                                     │  │
│  │                                                           │  │
│  │    return () => ws.close();                              │  │
│  │  }, []);                                                  │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                      Data Flow in App                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  User Action (UI)                                               │
│       │                                                          │
│       ▼                                                          │
│  Component Event Handler                                        │
│       │                                                          │
│       ▼                                                          │
│  API Service Call (authService, busService, etc.)              │
│       │                                                          │
│       ▼                                                          │
│  API Client (Axios) + JWT Token                                │
│       │                                                          │
│       ▼                                                          │
│  Backend API (http://devse.kr:12589)                           │
│       │                                                          │
│       ▼                                                          │
│  Response (JSON)                                                │
│       │                                                          │
│       ▼                                                          │
│  Update Zustand Store                                           │
│       │                                                          │
│       ▼                                                          │
│  Component Re-render (React)                                    │
│       │                                                          │
│       ▼                                                          │
│  UI Update                                                       │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 9. 배포 아키텍처

```
┌────────────────────────────────────────────────────────────────────┐
│                      Deployment Architecture                        │
└────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                    Production Environment                         │
└─────────────────────────────────────────────────────────────────┘

                        Internet (Public)
                              │
                              │ HTTPS
                              ▼
                    ┌──────────────────┐
                    │   Load Balancer  │
                    │  (Nginx/HAProxy) │
                    └────────┬─────────┘
                             │
                ┌────────────┼────────────┐
                │            │            │
                ▼            ▼            ▼
        ┌──────────┐  ┌──────────┐  ┌──────────┐
        │ Backend  │  │ Backend  │  │ Backend  │
        │Instance 1│  │Instance 2│  │Instance 3│
        └────┬─────┘  └────┬─────┘  └────┬─────┘
             │             │             │
             └─────────────┼─────────────┘
                           │
                           ▼
              ┌────────────────────────┐
              │   MongoDB Cluster      │
              │  ┌──────────────────┐  │
              │  │ Primary Node     │  │
              │  └────────┬─────────┘  │
              │           │            │
              │  ┌────────┴─────────┐  │
              │  │                  │  │
              │  ▼                  ▼  │
              │ ┌─────────┐  ┌─────────┐
              │ │Secondary│  │Secondary│
              │ │ Node 1  │  │ Node 2  │
              │ └─────────┘  └─────────┘
              └────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                    Backend Deployment                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Docker Container                                               │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │                                                          │  │
│  │  FROM openjdk:21-jdk-slim                               │  │
│  │                                                          │  │
│  │  WORKDIR /app                                           │  │
│  │                                                          │  │
│  │  COPY build/libs/bustracker-0.0.1-SNAPSHOT.jar app.jar │  │
│  │                                                          │  │
│  │  EXPOSE 8080                                            │  │
│  │                                                          │  │
│  │  ENV SPRING_PROFILES_ACTIVE=prod                        │  │
│  │                                                          │  │
│  │  ENTRYPOINT ["java", "-jar", "app.jar"]                │  │
│  │                                                          │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                  │
│  Environment Variables:                                         │
│  - SPRING_PROFILES_ACTIVE=prod                                 │
│  - MONGODB_URI=mongodb://cluster.example.com/busbuddy          │
│  - JWT_SECRET=xxx                                              │
│  - OAUTH2_CLIENT_ID=xxx                                        │
│  - OAUTH2_CLIENT_SECRET=xxx                                    │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                  Mobile App Distribution                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  iOS Distribution                                               │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  1. Build with Xcode                                     │  │
│  │     npm run ios                                          │  │
│  │                                                          │  │
│  │  2. Archive & Export                                     │  │
│  │     Product → Archive → Distribute App                  │  │
│  │                                                          │  │
│  │  3. Upload to App Store Connect                         │  │
│  │     Xcode Organizer → Distribute App                    │  │
│  │                                                          │  │
│  │  4. App Store Review                                     │  │
│  │     TestFlight (Beta) → Production                      │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                  │
│  Android Distribution                                           │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  1. Build Release APK/AAB                                │  │
│  │     cd android && ./gradlew assembleRelease             │  │
│  │     cd android && ./gradlew bundleRelease               │  │
│  │                                                          │  │
│  │  2. Sign APK/AAB                                         │  │
│  │     jarsigner -keystore release.keystore app.apk        │  │
│  │                                                          │  │
│  │  3. Upload to Google Play Console                       │  │
│  │     Internal Testing → Production                       │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                    CI/CD Pipeline (Recommended)                  │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Backend CI/CD (GitHub Actions / Jenkins)                       │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  1. Code Push to Repository                             │  │
│  │     ↓                                                    │  │
│  │  2. Run Tests (./gradlew test)                          │  │
│  │     ↓                                                    │  │
│  │  3. Build JAR (./gradlew bootJar)                       │  │
│  │     ↓                                                    │  │
│  │  4. Build Docker Image                                   │  │
│  │     ↓                                                    │  │
│  │  5. Push to Container Registry                          │  │
│  │     ↓                                                    │  │
│  │  6. Deploy to Server (Docker Compose / K8s)            │  │
│  │     ↓                                                    │  │
│  │  7. Health Check                                         │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                  │
│  Mobile CI/CD (Fastlane + GitHub Actions)                      │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  1. Code Push to Repository                             │  │
│  │     ↓                                                    │  │
│  │  2. Run Tests (npm test)                                │  │
│  │     ↓                                                    │  │
│  │  3. Build iOS (fastlane ios build)                     │  │
│  │     ↓                                                    │  │
│  │  4. Build Android (fastlane android build)             │  │
│  │     ↓                                                    │  │
│  │  5. Upload to TestFlight / Play Console                │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                    Monitoring & Logging                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Application Monitoring                                         │
│  - Spring Boot Actuator (/actuator/health, /metrics)          │
│  - Prometheus + Grafana (Metrics visualization)                │
│  - ELK Stack (Elasticsearch, Logstash, Kibana)                │
│                                                                  │
│  Database Monitoring                                            │
│  - MongoDB Atlas Monitoring (if cloud)                         │
│  - MongoDB Ops Manager                                          │
│                                                                  │
│  Error Tracking                                                 │
│  - Sentry / Crashlytics (Mobile crashes)                       │
│  - Backend error logging (SLF4J)                               │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                    Environment Configuration                     │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Development (application-dev.properties)                       │
│  - MongoDB: localhost:27017                                    │
│  - JWT Secret: dev-secret-key                                  │
│  - Log Level: DEBUG                                            │
│                                                                  │
│  Production (application-prod.properties)                       │
│  - MongoDB: cluster URI (replica set)                          │
│  - JWT Secret: strong-production-secret                        │
│  - Log Level: INFO                                             │
│  - HTTPS enabled                                               │
│  - CORS: restricted origins                                    │
└─────────────────────────────────────────────────────────────────┘
```

---

## 10. 시스템 시퀀스 다이어그램

### 10.1 버스 실시간 위치 추적 시나리오

```
┌────────────────────────────────────────────────────────────────────┐
│          Real-time Bus Tracking Sequence Diagram                    │
└────────────────────────────────────────────────────────────────────┘

Driver      Driver       Bus         Bus      Event      Passenger   Passenger
App         WebSocket    Service     Repo     Listener   WebSocket   App
 │              │           │          │          │          │         │
 │ GPS Update   │           │          │          │          │         │
 │──────────────>           │          │          │          │         │
 │              │           │          │          │          │         │
 │ Connect WS   │           │          │          │          │         │
 │──────────────>           │          │          │          │         │
 │              │           │          │          │          │         │
 │ Send Location│           │          │          │          │         │
 │ { busId,     │           │          │          │          │         │
 │   lat, lng } │           │          │          │          │         │
 │──────────────>           │          │          │          │         │
 │              │           │          │          │          │         │
 │              │ Update    │          │          │          │         │
 │              │ Location  │          │          │          │         │
 │              │───────────>          │          │          │         │
 │              │           │          │          │          │         │
 │              │           │ Save     │          │          │         │
 │              │           │ Location │          │          │         │
 │              │           │──────────>          │          │         │
 │              │           │          │          │          │         │
 │              │           │          │ Updated  │          │         │
 │              │           │          │<─────────│          │         │
 │              │           │          │          │          │         │
 │              │           │ Publish  │          │          │         │
 │              │           │ Event    │          │          │         │
 │              │           │──────────────────────>         │         │
 │              │           │          │          │          │         │
 │              │           │          │          │ Broadcast│         │
 │              │           │          │          │ to All   │         │
 │              │           │          │          │ Passengers        │
 │              │           │          │          │──────────>         │
 │              │           │          │          │          │         │
 │              │           │          │          │          │ Receive │
 │              │           │          │          │          │ Update  │
 │              │           │          │          │          │─────────>
 │              │           │          │          │          │         │
 │              │           │          │          │          │         │ Update
 │              │           │          │          │          │         │ Map UI
 │              │           │          │          │          │         │
 │ ACK          │           │          │          │          │         │
 │<─────────────│           │          │          │          │         │
 │              │           │          │          │          │         │

Time: ~500ms end-to-end latency
```

### 10.2 사용자 로그인 시나리오

```
┌────────────────────────────────────────────────────────────────────┐
│                  User Login Sequence Diagram                        │
└────────────────────────────────────────────────────────────────────┘

Mobile     Auth        Google      OAuth2      JWT         User       Async
App        Controller  OAuth2      Handler     Provider    Repo       Storage
 │            │          │            │           │          │          │
 │ Click      │          │            │           │          │          │
 │ Login      │          │            │           │          │          │
 │────────────>          │            │           │          │          │
 │            │          │            │           │          │          │
 │ Redirect   │          │            │           │          │          │
 │ to Google  │          │            │           │          │          │
 │<───────────│          │            │           │          │          │
 │            │          │            │           │          │          │
 │ Auth with  │          │            │           │          │          │
 │ Google     │          │            │           │          │          │
 │────────────────────────>           │           │          │          │
 │            │          │            │           │          │          │
 │ Auth Code  │          │            │           │          │          │
 │<────────────────────────           │           │          │          │
 │            │          │            │           │          │          │
 │ Exchange   │          │            │           │          │          │
 │ Code       │          │            │           │          │          │
 │────────────>          │            │           │          │          │
 │            │          │            │           │          │          │
 │            │ Verify   │            │           │          │          │
 │            │ User     │            │           │          │          │
 │            │──────────────────────>│           │          │          │
 │            │          │            │           │          │          │
 │            │          │            │ Load/Create         │          │
 │            │          │            │ User                │          │
 │            │          │            │─────────────────────>          │
 │            │          │            │           │          │          │
 │            │          │            │           │ User Data│          │
 │            │          │            │           │<─────────│          │
 │            │          │            │           │          │          │
 │            │          │            │ Generate  │          │          │
 │            │          │            │ JWT       │          │          │
 │            │          │            │───────────>          │          │
 │            │          │            │           │          │          │
 │            │          │            │           │ JWT Token│          │
 │            │          │            │           │<─────────│          │
 │            │          │            │           │          │          │
 │            │ JWT      │            │           │          │          │
 │            │ Response │            │           │          │          │
 │            │<──────────────────────│           │          │          │
 │            │          │            │           │          │          │
 │ JWT Token  │          │            │           │          │          │
 │<───────────│          │            │           │          │          │
 │            │          │            │           │          │          │
 │ Store JWT  │          │            │           │          │          │
 │────────────────────────────────────────────────────────────────────>│
 │            │          │            │           │          │          │
 │ Navigate   │          │            │           │          │          │
 │ to Home    │          │            │           │          │          │
 │            │          │            │           │          │          │
```

---

## 요약

이 문서는 **BusBuddyBuddy (BBB)** 시스템의 전체 소프트웨어 아키텍처를 시각화한 다이어그램 모음입니다.

### 포함된 다이어그램

1. **전체 시스템 아키텍처** - 클라이언트-서버 전체 구조
2. **백엔드 레이어 아키텍처** - Controller-Service-Repository 패턴
3. **API 엔드포인트 맵** - 모든 REST API 엔드포인트 구조
4. **데이터 흐름 다이어그램** - HTTP & WebSocket 통신 흐름
5. **도메인 모델 다이어그램** - 엔티티 관계도
6. **실시간 통신 아키텍처** - WebSocket 메시징 구조
7. **인증 및 보안 흐름** - OAuth2 + JWT 인증 프로세스
8. **모바일 앱 아키텍처** - React Native 앱 구조
9. **배포 아키텍처** - 프로덕션 환경 배포 구조
10. **시퀀스 다이어그램** - 주요 시나리오별 상호작용

### 주요 특징

- **Layered Architecture**: 명확한 계층 분리
- **Real-time Communication**: WebSocket 기반 양방향 통신
- **Security-first Design**: OAuth2 + JWT 이중 보안
- **Scalable Infrastructure**: MongoDB Cluster + Load Balancing
- **Mobile-first Approach**: React Native 크로스 플랫폼

---

**생성일**: 2025-11-03
**버전**: 1.0
**프로젝트**: BusBuddyBuddy (BBB)
**작성자**: Architecture Analysis Tool
