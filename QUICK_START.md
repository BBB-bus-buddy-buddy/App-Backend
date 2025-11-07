# 이벤트 데이터 생성 빠른 시작 가이드

## 가장 빠른 방법 (1분 소요) ⚡

### 1단계: 백엔드 서버 실행
```bash
cd /Users/mac/Desktop/Coding/_AppBackendBBB
./gradlew bootRun
```

서버가 시작되면 다음 메시지가 표시됩니다:
```
Started BustrackerApplication in X.XXX seconds
```

### 2단계: 브라우저에서 API 호출

브라우저 주소창에 다음 URL을 입력하고 엔터:

```
http://localhost:8080/api/admin/event/init-sample-data?organizationId=ORG001
```

**⚠️ 중요: `ORG001`을 실제 조직 ID로 변경하세요!**

조직 ID 찾는 방법:
1. 앱에 로그인
2. 마이페이지 > 내 정보 확인
3. "인증된 코드: XXX" 부분이 organizationId입니다

### 3단계: 결과 확인

브라우저에 다음과 같은 JSON이 표시되면 성공! ✅

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

### 4단계: 앱에서 확인

iOS 앱 실행 → 로그인 → 마이페이지 → **미션** 탭 클릭

다음 화면이 보이면 성공! 🎉
- 이벤트 제목: "CoShow 2024 부스 이벤트"
- 미션 3개 (특정 버스 탑승, 정류장 방문, 자동 승하차 감지)
- 상품 5개 (1등~5등)
- 뽑기 버튼 (미션 완료 전까지 비활성화)

---

## 터미널에서 하는 방법 (curl 사용)

```bash
# 기본 설정으로 생성
curl -X POST "http://localhost:8080/api/admin/event/init-sample-data"

# 특정 조직 ID로 생성
curl -X POST "http://localhost:8080/api/admin/event/init-sample-data?organizationId=YOUR_ORG_ID"
```

---

## 문제 해결

### "현재 진행 중인 이벤트가 없습니다" 오류
- **원인**: organizationId가 일치하지 않음
- **해결**: API 호출 시 `?organizationId=실제조직ID` 파라미터 추가

### API 호출이 안 됨
- **원인**: 백엔드 서버가 실행되지 않음
- **해결**: `./gradlew bootRun` 명령어로 서버 실행 확인

### 미션이 자동 완료되지 않음
- **원인**: targetValue (버스 번호 또는 정류장 ID)가 실제 데이터와 다름
- **해결**: MongoDB에서 실제 버스/정류장 ID를 확인하여 수정
  ```javascript
  // MongoDB Shell에서
  db.buses.find({ busNumber: "5001" })
  db.stations.find({ name: /CoShow/ })
  ```

---

## 다음 단계

더 자세한 설정이 필요하면 [`EVENT_SETUP_GUIDE.md`](./EVENT_SETUP_GUIDE.md) 참고

- 미션 커스터마이징
- 상품 추가/수정
- 확률 조정
- 이벤트 기간 변경
- MongoDB 직접 조작
