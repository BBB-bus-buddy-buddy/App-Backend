# CoShow 이벤트 데이터 생성 가이드

## 개요
버스 버디버디 앱의 CoShow 2024 부스 이벤트 데이터를 생성하는 방법을 안내합니다.

---

## 방법 1: REST API를 통한 생성 (추천) ⭐

### 1단계: 백엔드 서버 실행
```bash
cd /Users/mac/Desktop/Coding/_AppBackendBBB
./gradlew bootRun
```

### 2단계: API 호출로 샘플 데이터 생성

**Postman, curl, 또는 브라우저에서 다음 API를 호출:**

```bash
# 기본 조직 ID (ORG001)로 생성
curl -X POST "http://localhost:8080/api/admin/event/init-sample-data"

# 또는 특정 조직 ID로 생성
curl -X POST "http://localhost:8080/api/admin/event/init-sample-data?organizationId=YOUR_ORG_ID"
```

**응답 예시:**
```json
{
  "data": {
    "eventId": "673c6f8a5e2f1a3b4c5d6e7f",
    "eventName": "CoShow 2024 부스 이벤트",
    "organizationId": "ORG001",
    "missionsCreated": 3,
    "rewardsCreated": 5,
    "startDate": "2024-11-07T18:30:00",
    "endDate": "2025-01-07T18:30:00"
  },
  "message": "샘플 이벤트 데이터 생성 완료"
}
```

### 3단계: Swagger UI에서 확인 (선택사항)
브라우저에서 http://localhost:8080/swagger-ui/index.html 접속하여 API 테스트 가능

---

## 방법 2: MongoDB 스크립트를 통한 생성

### 1단계: MongoDB 접속
```bash
# MongoDB 실행 확인
mongosh mongodb://localhost:27017/bustracker

# 또는 원격 MongoDB의 경우
mongosh "mongodb://username:password@host:port/bustracker"
```

### 2단계: JavaScript 스크립트 실행
```bash
# init-event-data.js 파일 실행
mongosh mongodb://localhost:27017/bustracker < /Users/mac/Desktop/Coding/_AppBackendBBB/init-event-data.js
```

### 2단계 (대안): 직접 MongoDB Shell에서 실행
MongoDB Shell에 접속한 후:
```javascript
// 파일 내용을 복사-붙여넣기하여 실행
use bustracker;
// init-event-data.js의 내용을 붙여넣기
```

---

## 생성되는 데이터 상세

### 이벤트 (Event)
```javascript
{
  name: "CoShow 2024 부스 이벤트",
  description: "버스 버디버디 부스를 방문하고 미션을 완료하여 푸짐한 경품을 받아가세요!",
  startDate: 현재시각,
  endDate: 2개월 후,
  isActive: true,
  organizationId: "ORG001"  // ⚠️ 실제 조직 ID로 변경 필요
}
```

### 미션 (Missions) - 3개
1. **특정 버스 탑승하기**
   - 타입: `BOARDING`
   - 타겟값: `5001` (5001번 버스)
   - 필수 여부: ✅ 필수

2. **특정 정류장 방문하기**
   - 타입: `VISIT_STATION`
   - 타겟값: `STATION_COSHOW` (⚠️ 실제 정류장 ID로 변경 필요)
   - 필수 여부: ✅ 필수

3. **자동 승하차 감지 완료**
   - 타입: `AUTO_DETECT_BOARDING`
   - 타겟값: `null` (어떤 버스든 상관없음)
   - 필수 여부: ✅ 필수

### 상품 (Rewards) - 5개
| 등급 | 상품명 | 확률 | 수량 |
|------|--------|------|------|
| 1등 | AirPods Pro 2세대 | 5% | 5개 |
| 2등 | 스타벅스 기프티콘 3만원 | 10% | 10개 |
| 3등 | 카카오프렌즈 인형 | 15% | 15개 |
| 4등 | 스타벅스 기프티콘 1만원 | 20% | 20개 |
| 5등 | 버스 버디버디 굿즈 | 50% | 50개 |

**확률 합계: 100%** ✅

---

## 데이터 확인 방법

### 1. MongoDB Shell로 확인
```javascript
use bustracker;

// 이벤트 조회
db.events.find().pretty();

// 미션 조회
db.event_missions.find().pretty();

// 상품 조회
db.event_rewards.find().sort({ rewardGrade: 1 }).pretty();
```

### 2. REST API로 확인
```bash
# 현재 이벤트 조회 (인증 필요)
curl -X GET "http://localhost:8080/api/event/current" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"

# 미션 목록 조회
curl -X GET "http://localhost:8080/api/event/{eventId}/missions" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"

# 상품 목록 조회
curl -X GET "http://localhost:8080/api/event/{eventId}/rewards" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

### 3. Swagger UI로 확인
http://localhost:8080/swagger-ui/index.html 에서 "Event" 섹션 확인

---

## 주의사항 ⚠️

### 반드시 수정해야 할 값들:

1. **organizationId**: `"ORG001"`
   - 실제 사용자의 조직 ID로 변경
   - `db.Auth.findOne({ /* 사용자 정보 */ })` 로 확인

2. **targetValue (정류장 미션)**: `"STATION_COSHOW"`
   - 실제 정류장 ID로 변경
   - `db.stations.find({ name: /CoShow/ })` 로 검색

3. **이벤트 기간**:
   - `startDate`, `endDate`를 행사 일정에 맞게 조정

4. **상품 수량**:
   - 실제 준비한 상품 수량에 맞게 조정
   - `totalQuantity`와 `remainingQuantity` 값 변경

---

## 관리자 API 사용법

### 이벤트 활성화/비활성화
```bash
curl -X PATCH "http://localhost:8080/api/admin/event/{eventId}/toggle-active"
```

### 모든 이벤트 조회
```bash
curl -X GET "http://localhost:8080/api/admin/event/all"
```

### 이벤트 삭제
```bash
curl -X DELETE "http://localhost:8080/api/admin/event/{eventId}"
```

---

## 테스트 시나리오

### 1. 미션 완료 테스트
```bash
# 1) 버스 탑승 미션 완료
curl -X POST "http://localhost:8080/api/event/complete-mission" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "EVENT_ID",
    "missionId": "MISSION_ID",
    "targetValue": "5001"
  }'

# 2) 정류장 방문 미션 완료
curl -X POST "http://localhost:8080/api/event/complete-mission" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "EVENT_ID",
    "missionId": "MISSION_ID",
    "targetValue": "STATION_COSHOW"
  }'

# 3) 자동 승하차 감지 미션은 앱에서 자동 완료
```

### 2. 뽑기 테스트
```bash
# 모든 필수 미션 완료 후
curl -X POST "http://localhost:8080/api/event/{eventId}/draw-reward" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

**응답 예시:**
```json
{
  "data": {
    "success": true,
    "reward": {
      "id": "...",
      "rewardName": "카카오프렌즈 인형",
      "rewardGrade": 3,
      "probability": 0.15,
      "description": "라이언 또는 어피치 인형 (랜덤)"
    },
    "message": "축하합니다! 3등 당첨!"
  }
}
```

---

## 문제 해결

### 문제: "현재 진행 중인 이벤트가 없습니다" 오류
**해결책:**
1. `organizationId`가 사용자의 실제 조직 ID와 일치하는지 확인
2. `isActive: true` 상태인지 확인
3. MongoDB에서 확인: `db.events.find({ organizationId: "YOUR_ORG_ID", isActive: true })`

### 문제: "미션 조건이 일치하지 않습니다" 오류
**해결책:**
1. `targetValue`가 실제 버스 번호/정류장 ID와 일치하는지 확인
2. 대소문자 및 공백 확인

### 문제: "뽑기 자격이 없습니다" 오류
**해결책:**
1. 모든 **필수 미션**을 완료했는지 확인
2. 참여 현황 조회 API로 확인:
   ```bash
   curl -X GET "http://localhost:8080/api/event/{eventId}/my-participation" \
     -H "Authorization: Bearer YOUR_TOKEN"
   ```

### 문제: "남은 상품이 없습니다" 오류
**해결책:**
- 모든 상품의 `remainingQuantity`가 0인지 확인
- 상품 재고 추가: `db.event_rewards.updateMany({}, { $inc: { remainingQuantity: 10 } })`

---

## 추가 커스터마이징

### 미션 추가하기
```java
// EventAdminController.java의 initSampleData() 메서드에 추가
missions.add(EventMission.builder()
    .eventId(new DBRef("events", event.getId()))
    .title("새로운 미션 제목")
    .description("미션 설명")
    .missionType(EventMission.MissionType.BOARDING)  // 또는 VISIT_STATION, AUTO_DETECT_BOARDING
    .targetValue("TARGET_VALUE")
    .isRequired(false)  // 선택 미션
    .order(4)
    .createdAt(LocalDateTime.now())
    .build());
```

### 상품 추가하기
```java
// EventAdminController.java의 initSampleData() 메서드에 추가
rewards.add(EventReward.builder()
    .eventId(new DBRef("events", event.getId()))
    .rewardName("새로운 상품명")
    .rewardGrade(6)  // 새로운 등급
    .probability(0.10)  // 10% (확률 합계가 1.0이 되도록 조정)
    .totalQuantity(10)
    .remainingQuantity(10)
    .imageUrl("https://example.com/image.jpg")
    .description("상품 설명")
    .createdAt(LocalDateTime.now())
    .updatedAt(LocalDateTime.now())
    .build());
```

---

## 요약

### 빠른 시작 (3단계)
1. 백엔드 서버 실행: `./gradlew bootRun`
2. API 호출: `curl -X POST "http://localhost:8080/api/admin/event/init-sample-data?organizationId=YOUR_ORG_ID"`
3. 앱에서 "마이페이지 > 미션" 탭으로 확인 ✅

### 데이터 수정이 필요한 경우
- `organizationId` → 실제 조직 ID
- `targetValue` (정류장 미션) → 실제 정류장 ID
- 이벤트 기간 조정
- 상품 수량 조정

이제 이벤트를 생성하고 테스트할 수 있습니다! 🎉
