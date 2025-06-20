package capston2024.bustracker.controller;

import capston2024.bustracker.config.dto.ApiResponse;
import capston2024.bustracker.config.dto.OperationPlanDTO;
import capston2024.bustracker.exception.BusinessException;
import capston2024.bustracker.exception.ResourceNotFoundException;
import capston2024.bustracker.exception.UnauthorizedException;
import capston2024.bustracker.service.AuthService;
import capston2024.bustracker.service.BusOperationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/operation-plan")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Bus Operation Plan", description = "버스 운행 일정 관리 API - 프론트엔드 호환")
public class BusOperationController {

    private final BusOperationService busOperationService;
    private final AuthService authService;

    /**
     * 운전자 오늘 운행 일정 조회 - 프론트엔드 operationPlan.js와 호환
     * 경로: /api/operation-plan/driver/today
     */
    @GetMapping("/driver/today")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(summary = "운전자 오늘 운행 일정 조회",
            description = "현재 인증된 운전자의 오늘 운행 일정을 조회합니다. 프론트엔드 operationPlan.getDriverTodaySchedules()와 호환됩니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "오늘 운행 일정 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음 (운전자 권한 필요)")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<ApiResponse<List<OperationPlanDTO>>> getDriverTodaySchedules(
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 운행 일정을 조회할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String driverId = (String) userInfo.get("email");  // 운전자 ID는 email로 식별
        String organizationId = (String) userInfo.get("organizationId");
        String role = (String) userInfo.get("role");

        // 운전자 권한 확인
        if (!"ROLE_DRIVER".equals(role)) {
            throw new UnauthorizedException("운전자 권한이 필요합니다.");
        }

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 운행 일정을 조회할 수 없습니다.");
        }

        log.info("운전자 오늘 운행 일정 조회 - 운전자: {}, 조직: {}", driverId, organizationId);
        List<OperationPlanDTO> plans = busOperationService.getDriverTodayOperationPlans(driverId, organizationId);

        return ResponseEntity.ok(new ApiResponse<>(plans, "오늘 운행 일정이 성공적으로 조회되었습니다."));
    }

    /**
     * 운전자 본인의 특정 날짜 운행 일정 조회
     */
    @GetMapping("/driver/{date}")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(summary = "운전자 특정 날짜 운행 일정 조회",
            description = "현재 인증된 운전자의 특정 날짜 운행 일정을 조회합니다. 운전자 권한이 필요합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "운행 일정 조회 성공",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음 (운전자 권한 필요)")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<ApiResponse<List<OperationPlanDTO>>> getDriverOperationPlansByDate(
            @Parameter(description = "조회할 날짜 (YYYY-MM-DD)") @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 운행 일정을 조회할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String driverId = (String) userInfo.get("email");
        String organizationId = (String) userInfo.get("organizationId");
        String role = (String) userInfo.get("role");

        // 운전자 권한 확인
        if (!"ROLE_DRIVER".equals(role)) {
            throw new UnauthorizedException("운전자 권한이 필요합니다.");
        }

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 운행 일정을 조회할 수 없습니다.");
        }

        try {
            log.info("운전자 특정 날짜 운행 일정 조회 - 날짜: {}, 운전자: {}, 조직: {}", date, driverId, organizationId);

            List<OperationPlanDTO> plans = busOperationService.getDriverOperationPlansByDate(driverId, date, organizationId);

            return ResponseEntity.ok(new ApiResponse<>(plans,
                    String.format("%s 운행 일정이 성공적으로 조회되었습니다.", date)));
        } catch (Exception e) {
            log.error("날짜 파싱 오류 또는 조회 실패: {}", e.getMessage());
            throw new BusinessException("잘못된 날짜 형식입니다. YYYY-MM-DD 형식으로 입력해주세요.");
        }
    }

    /**
     * 운전자 월간 운행 일정 조회 - 프론트엔드 operationPlan.js와 호환
     * 경로: /api/operation-plan/driver/monthly/{year}/{month}
     */
    @GetMapping("/driver/monthly/{year}/{month}")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(summary = "운전자 월간 운행 일정 조회",
            description = "현재 인증된 운전자의 특정 월 운행 일정을 조회합니다. 프론트엔드 operationPlan.getDriverMonthlySchedules(year, month)와 호환됩니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "월간 운행 일정 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음 (운전자 권한 필요)")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<ApiResponse<List<OperationPlanDTO>>> getDriverMonthlySchedules(
            @Parameter(description = "연도") @PathVariable int year,
            @Parameter(description = "월 (1-12)") @PathVariable int month,
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 운행 일정을 조회할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String driverId = (String) userInfo.get("email");
        String organizationId = (String) userInfo.get("organizationId");
        String role = (String) userInfo.get("role");

        // 운전자 권한 확인
        if (!"ROLE_DRIVER".equals(role)) {
            throw new UnauthorizedException("운전자 권한이 필요합니다.");
        }

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 운행 일정을 조회할 수 없습니다.");
        }

        // 월 유효성 검증
        if (month < 1 || month > 12) {
            throw new BusinessException("월은 1~12 사이의 값이어야 합니다.");
        }

        log.info("운전자 월간 운행 일정 조회 - 년월: {}-{}, 운전자: {}, 조직: {}", year, month, driverId, organizationId);
        List<OperationPlanDTO> plans = busOperationService.getDriverMonthlyOperationPlans(driverId, year, month, organizationId);

        return ResponseEntity.ok(new ApiResponse<>(plans,
                String.format("%d년 %d월 운행 일정이 성공적으로 조회되었습니다.", year, month)));
    }

    /**
     * 운전자 현재 월 운행 일정 조회 - 프론트엔드 operationPlan.js와 호환
     * 경로: /api/operation-plan/driver/monthly
     */
    @GetMapping("/driver/monthly")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(summary = "운전자 현재 월 운행 일정 조회",
            description = "현재 인증된 운전자의 현재 월 운행 일정을 조회합니다. 프론트엔드 operationPlan.getDriverCurrentMonthSchedules()와 호환됩니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "현재 월 운행 일정 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음 (운전자 권한 필요)")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<ApiResponse<List<OperationPlanDTO>>> getDriverCurrentMonthSchedules(
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 운행 일정을 조회할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String driverId = (String) userInfo.get("email");
        String organizationId = (String) userInfo.get("organizationId");
        String role = (String) userInfo.get("role");

        // 운전자 권한 확인
        if (!"ROLE_DRIVER".equals(role)) {
            throw new UnauthorizedException("운전자 권한이 필요합니다.");
        }

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 운행 일정을 조회할 수 없습니다.");
        }

        LocalDate now = LocalDate.now();
        int year = now.getYear();
        int month = now.getMonthValue();

        log.info("운전자 현재 월 운행 일정 조회 - 년월: {}-{}, 운전자: {}, 조직: {}", year, month, driverId, organizationId);
        List<OperationPlanDTO> plans = busOperationService.getDriverMonthlyOperationPlans(driverId, year, month, organizationId);

        return ResponseEntity.ok(new ApiResponse<>(plans,
                String.format("현재 월(%d년 %d월) 운행 일정이 성공적으로 조회되었습니다.", year, month)));
    }

    /**
     * 운행 일정 상세 조회 - 프론트엔드 operationPlan.js와 호환
     * 경로: /api/operation-plan/detail/{operationId}
     */
    @GetMapping("/detail/{operationId}")
    @Operation(summary = "운행 일정 상세 조회",
            description = "특정 운행 일정의 상세 정보를 조회합니다. 프론트엔드 operationPlan.getScheduleDetail(operationId)와 호환됩니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "운행 일정 상세 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "운행 일정을 찾을 수 없음")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<ApiResponse<OperationPlanDTO>> getScheduleDetail(
            @Parameter(description = "운행 일정 ID") @PathVariable String operationId,
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 운행 일정을 조회할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String organizationId = (String) userInfo.get("organizationId");

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 운행 일정을 조회할 수 없습니다.");
        }

        log.info("운행 일정 상세 조회 - ID: {}, 조직: {}", operationId, organizationId);
        OperationPlanDTO plan = busOperationService.getOperationPlanDetail(operationId, organizationId);

        return ResponseEntity.ok(new ApiResponse<>(plan, "운행 일정이 성공적으로 조회되었습니다."));
    }

    // ========== 관리자용 API ==========

    /**
     * 버스 운행 일정 추가 (관리자용)
     */
    @PostMapping
    @PreAuthorize("hasRole('STAFF')")
    @Operation(summary = "버스 운행 일정 생성 (관리자용)",
            description = "새로운 버스 운행 일정을 생성합니다. 관리자 권한이 필요합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "운행 일정 생성 성공",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청 데이터"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음 (관리자 권한 필요)")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<ApiResponse<List<OperationPlanDTO>>> createOperationPlan(
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal,
            @Parameter(description = "운행 일정 생성 요청 데이터") @RequestBody OperationPlanDTO operationPlanDTO) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 운행 일정을 생성할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String organizationId = (String) userInfo.get("organizationId");

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 운행 일정을 생성할 수 없습니다.");
        }

        log.info("운행 일정 생성 요청 - 조직: {}, 버스: {}", organizationId, operationPlanDTO.getBusId());
        List<OperationPlanDTO> createdPlans = busOperationService.createOperationPlan(operationPlanDTO, organizationId);

        return ResponseEntity.ok(new ApiResponse<>(createdPlans, "운행 일정이 성공적으로 생성되었습니다."));
    }

    /**
     * 일별 운행 일정 조회 (관리자용)
     */
    @GetMapping("/{date}")
    @PreAuthorize("hasRole('STAFF')")
    @Operation(summary = "일별 운행 일정 조회 (관리자용)",
            description = "특정 날짜의 모든 운행 일정을 조회합니다. 관리자 권한이 필요합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "일별 운행 일정 조회 성공",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음 (관리자 권한 필요)")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<ApiResponse<List<OperationPlanDTO>>> getOperationPlansByDate(
            @Parameter(description = "조회할 날짜 (YYYY-MM-DD)") @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 운행 일정을 조회할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String organizationId = (String) userInfo.get("organizationId");

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 운행 일정을 조회할 수 없습니다.");
        }

        log.info("일별 운행 일정 조회 (관리자) - 날짜: {}, 조직: {}", date, organizationId);
        List<OperationPlanDTO> plans = busOperationService.getOperationPlansByDate(date, organizationId);

        return ResponseEntity.ok(new ApiResponse<>(plans, "일별 운행 일정이 성공적으로 조회되었습니다."));
    }

    /**
     * 오늘 운행 일정 조회 (관리자용)
     */
    @GetMapping("/today")
    @PreAuthorize("hasRole('STAFF')")
    @Operation(summary = "오늘 운행 일정 조회 (관리자용)",
            description = "오늘 날짜의 모든 운행 일정을 조회합니다. 관리자 권한이 필요합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "오늘 운행 일정 조회 성공",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음 (관리자 권한 필요)")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<ApiResponse<List<OperationPlanDTO>>> getTodayOperationPlans(
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 운행 일정을 조회할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String organizationId = (String) userInfo.get("organizationId");

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 운행 일정을 조회할 수 없습니다.");
        }

        log.info("오늘 운행 일정 조회 (관리자) - 조직: {}", organizationId);
        List<OperationPlanDTO> plans = busOperationService.getTodayOperationPlans(organizationId);

        return ResponseEntity.ok(new ApiResponse<>(plans, "오늘 운행 일정이 성공적으로 조회되었습니다."));
    }

    /**
     * 버스 운행 일정 주별 조회 (관리자용)
     */
    @GetMapping("/weekly")
    @PreAuthorize("hasRole('STAFF')")
    @Operation(summary = "주별 운행 일정 조회 (관리자용)",
            description = "현재 주의 모든 운행 일정을 조회합니다. 관리자 권한이 필요합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "주별 운행 일정 조회 성공",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음 (관리자 권한 필요)")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<ApiResponse<List<OperationPlanDTO>>> getWeeklyOperationPlans(
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 운행 일정을 조회할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String organizationId = (String) userInfo.get("organizationId");

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 운행 일정을 조회할 수 없습니다.");
        }

        log.info("주별 운행 일정 조회 (관리자) - 조직: {}", organizationId);
        List<OperationPlanDTO> plans = busOperationService.getWeeklyOperationPlans(organizationId);

        return ResponseEntity.ok(new ApiResponse<>(plans, "주별 운행 일정이 성공적으로 조회되었습니다."));
    }

    /**
     * 버스 운행 일정 월별 조회 (관리자용)
     */
    @GetMapping("/monthly")
    @PreAuthorize("hasRole('STAFF')")
    @Operation(summary = "월별 운행 일정 조회 (관리자용)",
            description = "현재 월의 모든 운행 일정을 조회합니다. 관리자 권한이 필요합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "월별 운행 일정 조회 성공",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음 (관리자 권한 필요)")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<ApiResponse<List<OperationPlanDTO>>> getMonthlyOperationPlans(
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 운행 일정을 조회할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String organizationId = (String) userInfo.get("organizationId");

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 운행 일정을 조회할 수 없습니다.");
        }

        log.info("월별 운행 일정 조회 (관리자) - 조직: {}", organizationId);
        List<OperationPlanDTO> plans = busOperationService.getMonthlyOperationPlans(organizationId);

        return ResponseEntity.ok(new ApiResponse<>(plans, "월별 운행 일정이 성공적으로 조회되었습니다."));
    }

    /**
     * 운행 일정 수정 (관리자용)
     */
    @PutMapping
    @PreAuthorize("hasRole('STAFF')")
    @Operation(summary = "운행 일정 수정 (관리자용)",
            description = "기존 운행 일정의 정보를 수정합니다. 관리자 권한이 필요합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "운행 일정 수정 성공",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청 데이터"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음 (관리자 권한 필요)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "운행 일정을 찾을 수 없음")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<ApiResponse<OperationPlanDTO>> updateOperationPlan(
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal,
            @Parameter(description = "운행 일정 수정 요청 데이터") @RequestBody OperationPlanDTO operationPlanDTO) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 운행 일정을 수정할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String organizationId = (String) userInfo.get("organizationId");

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 운행 일정을 수정할 수 없습니다.");
        }

        if (operationPlanDTO.getId() == null || operationPlanDTO.getId().trim().isEmpty()) {
            throw new BusinessException("수정할 운행 일정 ID가 필요합니다.");
        }

        log.info("운행 일정 수정 요청 - ID: {}, 조직: {}", operationPlanDTO.getId(), organizationId);
        OperationPlanDTO updatedPlan = busOperationService.updateOperationPlan(operationPlanDTO, organizationId);

        return ResponseEntity.ok(new ApiResponse<>(updatedPlan, "운행 일정이 성공적으로 수정되었습니다."));
    }

    /**
     * 운행 일정 삭제 (관리자용)
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('STAFF')")
    @Operation(summary = "운행 일정 삭제 (관리자용)",
            description = "지정된 운행 일정을 삭제합니다. 관리자 권한이 필요합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "운행 일정 삭제 성공",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음 (관리자 권한 필요)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "운행 일정을 찾을 수 없음")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<ApiResponse<Boolean>> deleteOperationPlan(
            @Parameter(description = "삭제할 운행 일정 ID") @PathVariable String id,
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 운행 일정을 삭제할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String organizationId = (String) userInfo.get("organizationId");

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 운행 일정을 삭제할 수 없습니다.");
        }

        log.info("운행 일정 삭제 요청 - ID: {}, 조직: {}", id, organizationId);
        boolean result = busOperationService.deleteOperationPlan(id, organizationId);

        return ResponseEntity.ok(new ApiResponse<>(result, "운행 일정이 성공적으로 삭제되었습니다."));
    }

    /**
     * 예외 처리
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        log.error("비즈니스 예외 발생: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(null, ex.getMessage()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorizedException(UnauthorizedException ex) {
        log.error("인증 예외 발생: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(null, ex.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFoundException(ResourceNotFoundException ex) {
        log.error("리소스 찾을 수 없음: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(null, ex.getMessage()));
    }
}