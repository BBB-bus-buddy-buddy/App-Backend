// src/main/java/capston2024/bustracker/controller/BusOperationController.java
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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/operation-plan")
@RequiredArgsConstructor
@Slf4j
public class BusOperationController {

    private final BusOperationService busOperationService;
    private final AuthService authService;

    /**
     * 버스 운행 일정 추가
     */
    @PostMapping
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<ApiResponse<List<OperationPlanDTO>>> createOperationPlan(
            @AuthenticationPrincipal OAuth2User principal,
            @RequestBody OperationPlanDTO operationPlanDTO) {

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
     * 버스 운행 일정 일별 조회
     */
    @GetMapping("/{date}")
    public ResponseEntity<ApiResponse<List<OperationPlanDTO>>> getOperationPlansByDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 운행 일정을 조회할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String organizationId = (String) userInfo.get("organizationId");

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 운행 일정을 조회할 수 없습니다.");
        }

        log.info("일별 운행 일정 조회 - 날짜: {}, 조직: {}", date, organizationId);
        List<OperationPlanDTO> plans = busOperationService.getOperationPlansByDate(date, organizationId);

        return ResponseEntity.ok(new ApiResponse<>(plans, "일별 운행 일정이 성공적으로 조회되었습니다."));
    }

    /**
     * 버스 운행 일정 오늘자 조회
     */
    @GetMapping("/today")
    public ResponseEntity<ApiResponse<List<OperationPlanDTO>>> getTodayOperationPlans(
            @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 운행 일정을 조회할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String organizationId = (String) userInfo.get("organizationId");

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 운행 일정을 조회할 수 없습니다.");
        }

        log.info("오늘 운행 일정 조회 - 조직: {}", organizationId);
        List<OperationPlanDTO> plans = busOperationService.getTodayOperationPlans(organizationId);

        return ResponseEntity.ok(new ApiResponse<>(plans, "오늘 운행 일정이 성공적으로 조회되었습니다."));
    }

    /**
     * 버스 운행 일정 주별 조회
     */
    @GetMapping("/weekly")
    public ResponseEntity<ApiResponse<List<OperationPlanDTO>>> getWeeklyOperationPlans(
            @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 운행 일정을 조회할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String organizationId = (String) userInfo.get("organizationId");

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 운행 일정을 조회할 수 없습니다.");
        }

        log.info("주별 운행 일정 조회 - 조직: {}", organizationId);
        List<OperationPlanDTO> plans = busOperationService.getWeeklyOperationPlans(organizationId);

        return ResponseEntity.ok(new ApiResponse<>(plans, "주별 운행 일정이 성공적으로 조회되었습니다."));
    }

    /**
     * 버스 운행 일정 월별 조회
     */
    @GetMapping("/monthly")
    public ResponseEntity<ApiResponse<List<OperationPlanDTO>>> getMonthlyOperationPlans(
            @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 운행 일정을 조회할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String organizationId = (String) userInfo.get("organizationId");

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 운행 일정을 조회할 수 없습니다.");
        }

        log.info("월별 운행 일정 조회 - 조직: {}", organizationId);
        List<OperationPlanDTO> plans = busOperationService.getMonthlyOperationPlans(organizationId);

        return ResponseEntity.ok(new ApiResponse<>(plans, "월별 운행 일정이 성공적으로 조회되었습니다."));
    }

    /**
     * 특정 버스 운행 일정 상세 조회
     */
    @GetMapping("/detail/{id}")
    public ResponseEntity<ApiResponse<OperationPlanDTO>> getOperationPlanDetail(
            @PathVariable String id,
            @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 운행 일정을 조회할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String organizationId = (String) userInfo.get("organizationId");

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 운행 일정을 조회할 수 없습니다.");
        }

        log.info("운행 일정 상세 조회 - ID: {}, 조직: {}", id, organizationId);
        OperationPlanDTO plan = busOperationService.getOperationPlanDetail(id, organizationId);

        return ResponseEntity.ok(new ApiResponse<>(plan, "운행 일정이 성공적으로 조회되었습니다."));
    }

    /**
     * 버스 운행 일정 삭제
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<ApiResponse<Boolean>> deleteOperationPlan(
            @PathVariable String id,
            @AuthenticationPrincipal OAuth2User principal) {

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
     * 버스 운행 일정 수정
     */
    @PutMapping
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<ApiResponse<OperationPlanDTO>> updateOperationPlan(
            @AuthenticationPrincipal OAuth2User principal,
            @RequestBody OperationPlanDTO operationPlanDTO) {

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
     * 비즈니스 로직 예외 처리
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        log.error("비즈니스 예외 발생: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(null, ex.getMessage()));
    }

    /**
     * 인증 예외 처리
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorizedException(UnauthorizedException ex) {
        log.error("인증 예외 발생: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(null, ex.getMessage()));
    }

    /**
     * 리소스 찾을 수 없음 예외 처리
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFoundException(ResourceNotFoundException ex) {
        log.error("리소스 찾을 수 없음: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(null, ex.getMessage()));
    }
}