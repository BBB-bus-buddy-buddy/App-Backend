package capston2024.bustracker.controller;

import capston2024.bustracker.config.dto.ApiResponse;
import capston2024.bustracker.domain.Event;
import capston2024.bustracker.domain.EventMission;
import capston2024.bustracker.domain.EventReward;
import capston2024.bustracker.repository.EventMissionRepository;
import capston2024.bustracker.repository.EventRepository;
import capston2024.bustracker.repository.EventRewardRepository;
import com.mongodb.DBRef;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 이벤트 관리자 컨트롤러
 * 이벤트, 미션, 상품 생성 및 관리
 */
@RestController
@RequestMapping("/api/admin/event")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Event Admin", description = "이벤트 관리자 API")
public class EventAdminController {

    private final EventRepository eventRepository;
    private final EventMissionRepository eventMissionRepository;
    private final EventRewardRepository eventRewardRepository;

    /**
     * 샘플 이벤트 데이터 일괄 생성
     */
    @PostMapping("/init-sample-data")
    @Operation(summary = "샘플 이벤트 데이터 생성",
            description = "CoShow 2024 부스 이벤트 샘플 데이터를 일괄 생성합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> initSampleData(
            @RequestParam(defaultValue = "ORG001") String organizationId) {

        log.info("샘플 이벤트 데이터 생성 시작 - organizationId: {}", organizationId);

        // 1. 이벤트 생성
        Event event = Event.builder()
                .name("CoShow 2024 부스 이벤트")
                .description("버스 버디버디 부스를 방문하고 미션을 완료하여 푸짐한 경품을 받아가세요!")
                .startDate(LocalDateTime.now())
                .endDate(LocalDateTime.now().plusMonths(2))
                .isActive(true)
                .organizationId(organizationId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        event = eventRepository.save(event);
        log.info("✅ 이벤트 생성 완료: {}", event.getId());

        // 2. 미션 생성
        List<EventMission> missions = new ArrayList<>();

        missions.add(EventMission.builder()
                .eventId(new DBRef("events", event.getId()))
                .title("특정 버스 탑승하기")
                .description("5001번 버스를 타고 목적지까지 이동하세요")
                .missionType(EventMission.MissionType.BOARDING)
                .targetValue("5001")
                .isRequired(true)
                .order(1)
                .createdAt(LocalDateTime.now())
                .build());

        missions.add(EventMission.builder()
                .eventId(new DBRef("events", event.getId()))
                .title("특정 정류장 방문하기")
                .description("CoShow 전시장 정류장을 방문하세요")
                .missionType(EventMission.MissionType.VISIT_STATION)
                .targetValue("STATION_COSHOW")
                .isRequired(true)
                .order(2)
                .createdAt(LocalDateTime.now())
                .build());

        missions.add(EventMission.builder()
                .eventId(new DBRef("events", event.getId()))
                .title("자동 승하차 감지 완료")
                .description("버스에 탑승하여 자동 승하차 감지 기능을 체험하세요")
                .missionType(EventMission.MissionType.AUTO_DETECT_BOARDING)
                .targetValue(null)
                .isRequired(true)
                .order(3)
                .createdAt(LocalDateTime.now())
                .build());

        missions = eventMissionRepository.saveAll(missions);
        log.info("✅ 미션 생성 완료: {}개", missions.size());

        // 3. 상품 생성 (1등: 5%, 2등: 10%, 3등: 15%, 4등: 20%, 5등: 50%)
        List<EventReward> rewards = new ArrayList<>();

        rewards.add(EventReward.builder()
                .eventId(new DBRef("events", event.getId()))
                .rewardName("AirPods Pro 2세대")
                .rewardGrade(1)
                .probability(0.05)
                .totalQuantity(5)
                .remainingQuantity(5)
                .imageUrl("https://example.com/airpods-pro.jpg")
                .description("최신 노이즈 캔슬링 무선 이어폰")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        rewards.add(EventReward.builder()
                .eventId(new DBRef("events", event.getId()))
                .rewardName("스타벅스 기프티콘 3만원")
                .rewardGrade(2)
                .probability(0.10)
                .totalQuantity(10)
                .remainingQuantity(10)
                .imageUrl("https://example.com/starbucks-30k.jpg")
                .description("스타벅스 모바일 기프트카드 3만원권")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        rewards.add(EventReward.builder()
                .eventId(new DBRef("events", event.getId()))
                .rewardName("카카오프렌즈 인형")
                .rewardGrade(3)
                .probability(0.15)
                .totalQuantity(15)
                .remainingQuantity(15)
                .imageUrl("https://example.com/kakao-friends.jpg")
                .description("라이언 또는 어피치 인형 (랜덤)")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        rewards.add(EventReward.builder()
                .eventId(new DBRef("events", event.getId()))
                .rewardName("스타벅스 기프티콘 1만원")
                .rewardGrade(4)
                .probability(0.20)
                .totalQuantity(20)
                .remainingQuantity(20)
                .imageUrl("https://example.com/starbucks-10k.jpg")
                .description("스타벅스 모바일 기프트카드 1만원권")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        rewards.add(EventReward.builder()
                .eventId(new DBRef("events", event.getId()))
                .rewardName("버스 버디버디 굿즈")
                .rewardGrade(5)
                .probability(0.50)
                .totalQuantity(50)
                .remainingQuantity(50)
                .imageUrl("https://example.com/busbuddy-goods.jpg")
                .description("버스 버디버디 에코백 + 스티커 세트")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        rewards = eventRewardRepository.saveAll(rewards);
        log.info("✅ 상품 생성 완료: {}개", rewards.size());

        // 결과 반환
        Map<String, Object> result = new HashMap<>();
        result.put("eventId", event.getId());
        result.put("eventName", event.getName());
        result.put("organizationId", event.getOrganizationId());
        result.put("missionsCreated", missions.size());
        result.put("rewardsCreated", rewards.size());
        result.put("startDate", event.getStartDate());
        result.put("endDate", event.getEndDate());

        log.info("✅ 샘플 이벤트 데이터 생성 완료: {}", event.getId());

        return ResponseEntity.ok(new ApiResponse<>(result, "샘플 이벤트 데이터 생성 완료"));
    }

    /**
     * 이벤트 활성화/비활성화
     */
    @PatchMapping("/{eventId}/toggle-active")
    @Operation(summary = "이벤트 활성화/비활성화", description = "이벤트의 활성화 상태를 변경합니다.")
    public ResponseEntity<ApiResponse<Event>> toggleEventActive(@PathVariable String eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("이벤트를 찾을 수 없습니다."));

        event.setActive(!event.isActive());
        event.setUpdatedAt(LocalDateTime.now());
        event = eventRepository.save(event);

        log.info("이벤트 활성화 상태 변경: {} -> {}", eventId, event.isActive());

        return ResponseEntity.ok(new ApiResponse<>(event, "이벤트 상태 변경 완료"));
    }

    /**
     * 모든 이벤트 조회
     */
    @GetMapping("/all")
    @Operation(summary = "모든 이벤트 조회", description = "모든 이벤트 목록을 조회합니다.")
    public ResponseEntity<ApiResponse<List<Event>>> getAllEvents() {
        List<Event> events = eventRepository.findAll();
        return ResponseEntity.ok(new ApiResponse<>(events, "이벤트 목록 조회 성공"));
    }

    /**
     * 이벤트 삭제
     */
    @DeleteMapping("/{eventId}")
    @Operation(summary = "이벤트 삭제", description = "이벤트와 관련된 모든 데이터를 삭제합니다.")
    public ResponseEntity<ApiResponse<String>> deleteEvent(@PathVariable String eventId) {
        // 이벤트 삭제
        eventRepository.deleteById(eventId);

        // 관련 미션 삭제
        List<EventMission> missions = eventMissionRepository.findByEventIdOrderByOrder(eventId);
        eventMissionRepository.deleteAll(missions);

        // 관련 상품 삭제
        List<EventReward> rewards = eventRewardRepository.findByEventIdOrderByRewardGrade(eventId);
        eventRewardRepository.deleteAll(rewards);

        log.info("이벤트 삭제 완료: {}", eventId);

        return ResponseEntity.ok(new ApiResponse<>("이벤트 삭제 완료", "이벤트 삭제 성공"));
    }
}
