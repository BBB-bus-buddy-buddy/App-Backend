// MongoDB 이벤트 데이터 초기화 스크립트
// 사용법: mongosh mongodb://localhost:27017/bustracker < init-event-data.js

// 1. 기존 이벤트 데이터 삭제 (선택사항)
db.events.deleteMany({});
db.event_missions.deleteMany({});
db.event_rewards.deleteMany({});
db.event_participations.deleteMany({});

print("✅ 기존 이벤트 데이터 삭제 완료");

// 2. 이벤트 생성
const eventResult = db.events.insertOne({
    name: "CoShow 2024 부스 이벤트",
    description: "버스 버디버디 부스를 방문하고 미션을 완료하여 푸짐한 경품을 받아가세요!",
    startDate: new Date("2024-11-07T00:00:00Z"),
    endDate: new Date("2024-12-31T23:59:59Z"),
    isActive: true,
    organizationId: "ORG001",  // 실제 organization ID로 변경 필요
    createdAt: new Date(),
    updatedAt: new Date()
});

const eventId = eventResult.insertedId;
print("✅ 이벤트 생성 완료: " + eventId);

// 3. 미션 생성
const missions = [
    {
        eventId: { $ref: "events", $id: eventId },
        title: "특정 버스 탑승하기",
        description: "5001번 버스를 타고 목적지까지 이동하세요",
        missionType: "BOARDING",
        targetValue: "5001",
        isRequired: true,
        order: 1,
        createdAt: new Date()
    },
    {
        eventId: { $ref: "events", $id: eventId },
        title: "특정 정류장 방문하기",
        description: "CoShow 전시장 정류장을 방문하세요",
        missionType: "VISIT_STATION",
        targetValue: "STATION_COSHOW",  // 실제 정류장 ID로 변경 필요
        isRequired: true,
        order: 2,
        createdAt: new Date()
    },
    {
        eventId: { $ref: "events", $id: eventId },
        title: "자동 승하차 감지 완료",
        description: "버스에 탑승하여 자동 승하차 감지 기능을 체험하세요",
        missionType: "AUTO_DETECT_BOARDING",
        targetValue: null,
        isRequired: true,
        order: 3,
        createdAt: new Date()
    }
];

const missionResult = db.event_missions.insertMany(missions);
print("✅ 미션 생성 완료: " + missionResult.insertedIds.length + "개");

// 4. 상품 생성 (1등: 5%, 2등: 10%, 3등: 15%, 4등: 20%, 5등: 50%)
const rewards = [
    {
        eventId: { $ref: "events", $id: eventId },
        rewardName: "AirPods Pro 2세대",
        rewardGrade: 1,
        probability: 0.05,
        totalQuantity: 5,
        remainingQuantity: 5,
        imageUrl: "https://example.com/airpods-pro.jpg",
        description: "최신 노이즈 캔슬링 무선 이어폰",
        createdAt: new Date(),
        updatedAt: new Date()
    },
    {
        eventId: { $ref: "events", $id: eventId },
        rewardName: "스타벅스 기프티콘 3만원",
        rewardGrade: 2,
        probability: 0.10,
        totalQuantity: 10,
        remainingQuantity: 10,
        imageUrl: "https://example.com/starbucks-30k.jpg",
        description: "스타벅스 모바일 기프트카드 3만원권",
        createdAt: new Date(),
        updatedAt: new Date()
    },
    {
        eventId: { $ref: "events", $id: eventId },
        rewardName: "카카오프렌즈 인형",
        rewardGrade: 3,
        probability: 0.15,
        totalQuantity: 15,
        remainingQuantity: 15,
        imageUrl: "https://example.com/kakao-friends.jpg",
        description: "라이언 또는 어피치 인형 (랜덤)",
        createdAt: new Date(),
        updatedAt: new Date()
    },
    {
        eventId: { $ref: "events", $id: eventId },
        rewardName: "스타벅스 기프티콘 1만원",
        rewardGrade: 4,
        probability: 0.20,
        totalQuantity: 20,
        remainingQuantity: 20,
        imageUrl: "https://example.com/starbucks-10k.jpg",
        description: "스타벅스 모바일 기프트카드 1만원권",
        createdAt: new Date(),
        updatedAt: new Date()
    },
    {
        eventId: { $ref: "events", $id: eventId },
        rewardName: "버스 버디버디 굿즈",
        rewardGrade: 5,
        probability: 0.50,
        totalQuantity: 50,
        remainingQuantity: 50,
        imageUrl: "https://example.com/busbuddy-goods.jpg",
        description: "버스 버디버디 에코백 + 스티커 세트",
        createdAt: new Date(),
        updatedAt: new Date()
    }
];

const rewardResult = db.event_rewards.insertMany(rewards);
print("✅ 상품 생성 완료: " + rewardResult.insertedIds.length + "개");

// 5. 생성된 데이터 확인
print("\n========================================");
print("📋 생성된 이벤트 데이터 요약");
print("========================================");
print("이벤트 ID: " + eventId);
print("이벤트 이름: CoShow 2024 부스 이벤트");
print("미션 수: " + missions.length + "개");
print("상품 수: " + rewards.length + "개");
print("조직 ID: ORG001");
print("\n⚠️  주의: organizationId와 targetValue를 실제 값으로 변경하세요!");
print("========================================\n");

// 6. 데이터 조회 테스트
print("📊 이벤트 조회 테스트:");
const event = db.events.findOne({ _id: eventId });
print(JSON.stringify(event, null, 2));

print("\n📋 미션 목록:");
db.event_missions.find({ "eventId.$id": eventId }).forEach(mission => {
    print("  - " + mission.title + " (" + mission.missionType + ")");
});

print("\n🎁 상품 목록:");
db.event_rewards.find({ "eventId.$id": eventId }).sort({ rewardGrade: 1 }).forEach(reward => {
    print("  - " + reward.rewardGrade + "등: " + reward.rewardName + " (" + (reward.probability * 100) + "%)");
});

print("\n✅ 이벤트 데이터 초기화 완료!");
