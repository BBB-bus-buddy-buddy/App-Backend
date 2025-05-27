package capston2024.bustracker.controller;

import capston2024.bustracker.config.dto.*;
import capston2024.bustracker.config.dto.busOperation.BusOperationCreateDTO;
import capston2024.bustracker.config.dto.busOperation.BusOperationDTO;
import capston2024.bustracker.config.dto.busOperation.BusOperationStatusUpdateDTO;
import capston2024.bustracker.domain.BusOperation;
import capston2024.bustracker.exception.BusinessException;
import capston2024.bustracker.exception.ResourceNotFoundException;
import capston2024.bustracker.exception.UnauthorizedException;
import capston2024.bustracker.service.AuthService;
import capston2024.bustracker.service.BusOperationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bus-operations")
@RequiredArgsConstructor
@Slf4j
public class BusOperationController {

    private final BusOperationService busOperationService;
    private final AuthService authService;

    /**
     * 새로운 버스 운행 생성 (STAFF 권한 필요)
     */
    @PostMapping
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<ApiResponse<BusOperationDTO>> createBusOperation(
            @AuthenticationPrincipal OAuth2User principal,
            @RequestBody BusOperationCreateDTO createDTO) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 운행을 생성할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String organizationId = (String) userInfo.get("organizationId");

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 운행을 생성할 수 없습니다.");
        }

        log.info("버스 운행 생성 요청 - 조직: {}", organizationId);
        BusOperationDTO createdOperation = busOperationService.createBusOperation(organizationId, createDTO);

        return ResponseEntity.ok(new ApiResponse<>(createdOperation, "버스 운행이 성공적으로 생성되었습니다."));
    }

    /**
     * 운행 상태 업데이트 (DRIVER, STAFF 권한 필요)
     */
    @PutMapping("/status")
    @PreAuthorize("hasRole('DRIVER') or hasRole('STAFF')")
    public ResponseEntity<ApiResponse<BusOperationDTO>> updateOperationStatus(
            @AuthenticationPrincipal OAuth2User principal,
            @RequestBody BusOperationStatusUpdateDTO updateDTO) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 운행 상태를 업데이트할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String organizationId = (String) userInfo.get("organizationId");

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 운행 상태를 업데이트할 수 없습니다.");
        }

        log.info("운행 상태 업데이트 요청 - 운행 ID: {}, 상태: {}",
                updateDTO.getOperationId(), updateDTO.getStatus());

        BusOperationDTO updatedOperation = busOperationService.updateOperationStatus(organizationId, updateDTO);

        return ResponseEntity.ok(new ApiResponse<>(updatedOperation, "운행 상태가 성공적으로 업데이트되었습니다."));
    }

    /**
     * 조직의 모든 운행 조회
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<BusOperationDTO>>> getAllOperations(
            @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 운행 목록을 조회할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String organizationId = (String) userInfo.get("organizationId");

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 운행 목록을 조회할 수 없습니다.");
        }

        log.info("조직 {}의 모든 운행 조회 요청", organizationId);
        List<BusOperationDTO> operations = busOperationService.getAllOperationsByOrganization(organizationId);

        return ResponseEntity.ok(new ApiResponse<>(operations, "운행 목록이 성공적으로 조회되었습니다."));
    }

    /**
     * 운행 상태별 조회
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<ApiResponse<List<BusOperationDTO>>> getOperationsByStatus(
            @PathVariable BusOperation.OperationStatus status,
            @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 운행 목록을 조회할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String organizationId = (String) userInfo.get("organizationId");

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 운행 목록을 조회할 수 없습니다.");
        }

        log.info("조직 {}의 {} 상태 운행 조회 요청", organizationId, status);
        List<BusOperationDTO> operations = busOperationService.getOperationsByStatus(organizationId, status);

        return ResponseEntity.ok(new ApiResponse<>(operations, status + " 상태의 운행 목록이 성공적으로 조회되었습니다."));
    }

    /**
     * 기사의 오늘 운행 스케줄 조회 (DRIVER 권한 필요)
     */
    @GetMapping("/my-schedule")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<List<BusOperationDTO>>> getMyTodaySchedule(
            @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 기사만 자신의 스케줄을 조회할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String driverId = (String) userInfo.get("email"); // 사용자 ID로 이메일 사용

        // 실제 사용자 ID 조회 필요시 추가 로직 구현
        log.info("기사 {}의 오늘 운행 스케줄 조회 요청", driverId);
        List<BusOperationDTO> operations = busOperationService.getTodayOperationsByDriver(driverId);

        return ResponseEntity.ok(new ApiResponse<>(operations, "오늘의 운행 스케줄이 성공적으로 조회되었습니다."));
    }

    /**
     * 운행 상세 조회
     */
    @GetMapping("/{operationId}")
    public ResponseEntity<ApiResponse<BusOperationDTO>> getOperationById(
            @PathVariable String operationId,
            @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 운행 상세를 조회할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String organizationId = (String) userInfo.get("organizationId");

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 운행 상세를 조회할 수 없습니다.");
        }

        log.info("운행 상세 조회 요청 - 운행 ID: {}", operationId);
        BusOperationDTO operation = busOperationService.getOperationById(organizationId, operationId);

        return ResponseEntity.ok(new ApiResponse<>(operation, "운행 상세가 성공적으로 조회되었습니다."));
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