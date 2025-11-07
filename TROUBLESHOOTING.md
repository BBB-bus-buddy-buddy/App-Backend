# 이벤트 기능 문제 해결 가이드

## 프론트엔드 오류: "cannot read property 'id' of undefined"

### 원인
이 오류는 다음 경우에 발생합니다:
1. 백엔드 서버가 실행되지 않음
2. 백엔드에 이벤트 데이터가 없음
3. organizationId가 일치하지 않음
4. API 호출이 실패함

---

## 해결 방법

### 1단계: 백엔드 서버 실행 확인

```bash
# 백엔드 디렉토리로 이동
cd /Users/mac/Desktop/Coding/_AppBackendBBB

# 서버 실행
./gradlew bootRun
```

**확인 사항:**
- 서버가 `Started BustrackerApplication` 메시지를 출력하는지 확인
- 포트 8080이 사용 중이 아닌지 확인

**테스트:**
```bash
# 서버가 응답하는지 확인
curl http://localhost:8080/actuator/health
```

---

### 2단계: 이벤트 데이터 생성

**브라우저에서 실행:**
```
http://localhost:8080/api/admin/event/init-sample-data?organizationId=YOUR_ORG_ID
```

**또는 curl로 실행:**
```bash
curl -X POST "http://localhost:8080/api/admin/event/init-sample-data?organizationId=YOUR_ORG_ID"
```

**⚠️ 중요: `YOUR_ORG_ID`를 실제 값으로 변경!**

#### organizationId 찾는 방법:

**방법 1: 앱에서 확인**
1. 앱 실행
2. 로그인
3. 마이페이지 > 내 정보
4. "인증된 코드: XXX" ← 이 값이 organizationId

**방법 2: MongoDB에서 확인**
```javascript
// MongoDB Shell에서
use bustracker;

// 사용자 정보 조회
db.Auth.find({}, { organizationId: 1, username: 1 }).pretty();
```

**방법 3: JWT 토큰 디코딩**
1. 앱 로그인 후 AsyncStorage에서 token 확인
2. https://jwt.io 에서 토큰 디코딩
3. payload의 organizationId 확인

---

### 3단계: 이벤트 데이터 확인

#### MongoDB에서 확인:
```javascript
use bustracker;

// 이벤트 조회
db.events.find({ isActive: true }).pretty();

// 특정 조직의 이벤트 조회
db.events.find({ organizationId: "YOUR_ORG_ID", isActive: true }).pretty();

// 미션 확인
db.event_missions.find().pretty();

// 상품 확인
db.event_rewards.find().pretty();
```

#### REST API로 확인:
```bash
# 현재 이벤트 조회 (JWT 토큰 필요)
curl -X GET "http://localhost:8080/api/event/current" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

---

### 4단계: 네트워크 요청 확인

#### iOS 앱에서 로그 확인:

**React Native Debugger 사용:**
1. 앱 실행
2. iOS 시뮬레이터에서 `Cmd + D` 누르기
3. "Debug" 선택
4. Chrome DevTools에서 Network 탭 확인

**콘솔 로그 확인:**
```bash
# 터미널에서 Metro bundler 로그 확인
# "이벤트 데이터 로드 실패" 메시지 확인
```

---

## 자주 발생하는 오류와 해결책

### 오류 1: "진행 중인 이벤트가 없습니다"

**원인:**
- organizationId 불일치
- isActive가 false
- 이벤트가 생성되지 않음

**해결:**
```javascript
// MongoDB에서 확인
db.events.find({ organizationId: "YOUR_ORG_ID" }).pretty();

// organizationId 수정
db.events.updateOne(
  { _id: ObjectId("EVENT_ID") },
  { $set: { organizationId: "CORRECT_ORG_ID" } }
);

// 이벤트 활성화
db.events.updateOne(
  { _id: ObjectId("EVENT_ID") },
  { $set: { isActive: true } }
);
```

### 오류 2: "Network request failed"

**원인:**
- 백엔드 서버가 실행되지 않음
- API 엔드포인트 URL이 잘못됨
- CORS 문제

**해결:**
1. 백엔드 서버 실행 확인
2. apiClient.ts에서 baseURL 확인:
   ```typescript
   // src/api/apiClient.ts
   const API_BASE_URL = 'http://localhost:8080';  // 또는 실제 서버 URL
   ```

### 오류 3: "401 Unauthorized"

**원인:**
- JWT 토큰이 없거나 만료됨
- 토큰이 올바르지 않음

**해결:**
1. 앱에서 로그아웃 후 다시 로그인
2. AsyncStorage에서 토큰 확인:
   ```typescript
   import AsyncStorage from '@react-native-async-storage/async-storage';

   AsyncStorage.getItem('token').then(token => {
     console.log('Token:', token);
   });
   ```

### 오류 4: "Cannot read property 'id' of undefined"

**원인:**
- API 응답이 예상과 다름
- 백엔드에서 null 반환

**해결:**
```bash
# API 응답 확인
curl -X GET "http://localhost:8080/api/event/current" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -v

# 예상 응답:
{
  "data": {
    "id": "673c6f8a5e2f1a3b4c5d6e7f",
    "name": "CoShow 2024 부스 이벤트",
    ...
  },
  "message": "이벤트 조회 성공"
}
```

---

## 디버깅 체크리스트

### 백엔드 체크리스트:
- [ ] 백엔드 서버가 실행 중인가?
- [ ] MongoDB가 실행 중인가?
- [ ] 이벤트 데이터가 생성되어 있는가?
- [ ] organizationId가 올바른가?
- [ ] isActive가 true인가?

### 프론트엔드 체크리스트:
- [ ] 앱이 로그인되어 있는가?
- [ ] JWT 토큰이 유효한가?
- [ ] API 엔드포인트 URL이 올바른가?
- [ ] 네트워크 연결이 정상인가?

### 데이터 체크리스트:
- [ ] Event 컬렉션에 데이터가 있는가?
- [ ] EventMission 컬렉션에 데이터가 있는가?
- [ ] EventReward 컬렉션에 데이터가 있는가?

---

## 전체 시스템 리셋 (최후의 수단)

모든 방법이 실패하면 다음 순서대로 진행:

### 1. 백엔드 데이터 초기화
```javascript
use bustracker;

// 이벤트 관련 데이터 모두 삭제
db.events.deleteMany({});
db.event_missions.deleteMany({});
db.event_rewards.deleteMany({});
db.event_participations.deleteMany({});
```

### 2. 새 이벤트 데이터 생성
```bash
curl -X POST "http://localhost:8080/api/admin/event/init-sample-data?organizationId=YOUR_ORG_ID"
```

### 3. 프론트엔드 캐시 클리어
```bash
# iOS
cd /Users/mac/Desktop/Coding/__BBBApp
rm -rf ios/build
watchman watch-del-all
npm start -- --reset-cache
```

### 4. 앱 재시작
```bash
# 새 터미널에서
npm run ios-16p
```

---

## 추가 도움이 필요한 경우

### 로그 파일 확인:

**백엔드 로그:**
```bash
# Spring Boot 로그 확인
tail -f logs/spring.log
```

**프론트엔드 로그:**
```bash
# Metro bundler 로그 확인
# 터미널에서 앱 실행 중인 창 확인
```

### 디버깅 모드 활성화:

**EventPage.tsx에 로그 추가:**
```typescript
const loadEventData = async () => {
  try {
    console.log('=== 이벤트 데이터 로드 시작 ===');

    const event = await getCurrentEvent();
    console.log('조회된 이벤트:', JSON.stringify(event, null, 2));

    if (!event || !event.id) {
      console.error('이벤트 ID가 없음!');
      return;
    }

    console.log('=== 이벤트 데이터 로드 성공 ===');
  } catch (error) {
    console.error('=== 이벤트 데이터 로드 실패 ===', error);
  }
};
```

이제 문제를 체계적으로 해결할 수 있습니다! 🔧
