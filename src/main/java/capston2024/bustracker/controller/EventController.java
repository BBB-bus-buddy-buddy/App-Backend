package capston2024.bustracker.controller;

import capston2024.bustracker.config.dto.*;
import capston2024.bustracker.exception.UnauthorizedException;
import capston2024.bustracker.service.AuthService;
import capston2024.bustracker.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 이벤트 컨트롤러
 * CoShow 부스 이벤트 API
 */
@RestController
@RequestMapping("/api/event")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Event", description = "이벤트 관련 API (CoShow 부스 이벤트)")
public class EventController {

    private final EventService eventService;
    private final AuthService authService;

    /**
     * 현재 진행 중인 이벤트 조회
     */
    @GetMapping("/current")
    @Operation(summary = "현재 진행 중인 이벤트 조회",
            description = "현재 활성화된 이벤트 정보를 조회합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "이벤트 조회 성공",
                    content = @Content(schema = @Schema(implementation = EventDTO.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "진행 중인 이벤트 없음")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<ApiResponse<EventDTO>> getCurrentEvent(
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 조회할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String organizationId = (String) userInfo.get("organizationId");

        log.info("현재 이벤트 조회 - 조직: {}", organizationId);
        EventDTO event = eventService.getCurrentEvent(organizationId);

        return ResponseEntity.ok(new ApiResponse<>(event, "이벤트 조회 성공"));
    }

    /**
     * 이벤트 미션 목록 조회
     */
    @GetMapping("/{eventId}/missions")
    @Operation(summary = "이벤트 미션 목록 조회",
            description = "이벤트의 모든 미션 목록을 조회합니다. 사용자의 완료 여부 포함.")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<ApiResponse<List<EventMissionDTO>>> getEventMissions(
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal,
            @PathVariable String eventId) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 조회할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String userId = (String) userInfo.get("userId");

        log.info("미션 목록 조회 - userId: {}, eventId: {}", userId, eventId);
        List<EventMissionDTO> missions = eventService.getEventMissions(eventId, userId);

        return ResponseEntity.ok(new ApiResponse<>(missions, "미션 목록 조회 성공"));
    }

    /**
     * 이벤트 상품 목록 조회
     */
    @GetMapping("/{eventId}/rewards")
    @Operation(summary = "이벤트 상품 목록 조회",
            description = "이벤트의 모든 상품 목록을 조회합니다 (1~5등 상품).")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<ApiResponse<List<EventRewardDTO>>> getEventRewards(
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal,
            @PathVariable String eventId) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 조회할 수 있습니다.");
        }

        log.info("상품 목록 조회 - eventId: {}", eventId);
        List<EventRewardDTO> rewards = eventService.getEventRewards(eventId);

        return ResponseEntity.ok(new ApiResponse<>(rewards, "상품 목록 조회 성공"));
    }

    /**
     * 미션 완료 처리
     */
    @PostMapping("/complete-mission")
    @Operation(summary = "미션 완료 처리",
            description = "미션을 완료 처리합니다.")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<ApiResponse<EventParticipationDTO>> completeMission(
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal,
            @RequestBody MissionCompleteRequestDTO request) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 미션을 완료할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String userId = (String) userInfo.get("userId");

        log.info("미션 완료 요청 - userId: {}, missionId: {}", userId, request.getMissionId());
        EventParticipationDTO participation = eventService.completeMission(userId, request);

        return ResponseEntity.ok(new ApiResponse<>(participation, "미션 완료 처리 성공"));
    }

    /**
     * 랜덤 뽑기 실행
     */
    @PostMapping("/{eventId}/draw-reward")
    @Operation(summary = "랜덤 뽑기 실행",
            description = "모든 필수 미션을 완료한 사용자가 랜덤 뽑기를 실행합니다.")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<ApiResponse<RewardDrawResponseDTO>> drawReward(
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal,
            @PathVariable String eventId) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 뽑기를 할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String userId = (String) userInfo.get("userId");

        log.info("뽑기 실행 - userId: {}, eventId: {}", userId, eventId);
        RewardDrawResponseDTO result = eventService.drawReward(userId, eventId);

        return ResponseEntity.ok(new ApiResponse<>(result, "뽑기 실행 성공"));
    }

    /**
     * 내 참여 현황 조회
     */
    @GetMapping("/{eventId}/my-participation")
    @Operation(summary = "내 참여 현황 조회",
            description = "현재 사용자의 이벤트 참여 현황을 조회합니다.")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<ApiResponse<EventParticipationDTO>> getMyParticipation(
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal,
            @PathVariable String eventId) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 조회할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String userId = (String) userInfo.get("userId");

        log.info("참여 현황 조회 - userId: {}, eventId: {}", userId, eventId);
        EventParticipationDTO participation = eventService.getMyParticipation(userId, eventId);

        return ResponseEntity.ok(new ApiResponse<>(participation, "참여 현황 조회 성공"));
    }
}
