package capston2024.bustracker.controller;

import capston2024.bustracker.config.dto.*;
import capston2024.bustracker.config.dto.bus.BusCreateDTO;
import capston2024.bustracker.config.dto.bus.BusStatusDTO;
import capston2024.bustracker.config.dto.bus.BusUpdateDTO;
import capston2024.bustracker.domain.Bus;
import capston2024.bustracker.exception.BusinessException;
import capston2024.bustracker.exception.ResourceNotFoundException;
import capston2024.bustracker.exception.UnauthorizedException;
import capston2024.bustracker.service.AuthService;
import capston2024.bustracker.service.BusService;
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
@RequestMapping("/api/bus")
@RequiredArgsConstructor
@Slf4j
public class BusController {

    private final BusService busService;
    private final AuthService authService;

    /**
     * 버스 추가
     */
    @PostMapping()
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<ApiResponse<String>> createBus(
            @AuthenticationPrincipal OAuth2User principal,
            @RequestBody BusCreateDTO busCreateDTO) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 버스를 등록할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String organizationId = (String) userInfo.get("organizationId");

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 버스를 등록할 수 없습니다.");
        }

        log.info("버스 등록 요청 - 조직: {}, 노선: {}", organizationId, busCreateDTO.getRouteId());
        String busNumber = busService.createBus(busCreateDTO, organizationId);

        return ResponseEntity.ok(new ApiResponse<>(busNumber, "성공적으로 버스가 추가되었습니다."));
    }

    /**
     * 버스 수정
     */
    @PutMapping()
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<ApiResponse<Boolean>> updateBus(
            @AuthenticationPrincipal OAuth2User principal,
            @RequestBody BusUpdateDTO busUpdateDTO) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 버스를 수정할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String organizationId = (String) userInfo.get("organizationId");

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 버스를 수정할 수 없습니다.");
        }

        log.info("버스 수정 요청 - 버스 번호: {}, 조직: {}", busUpdateDTO.getBusNumber(), organizationId);
        boolean result = busService.updateBus(busUpdateDTO, organizationId);

        return ResponseEntity.ok(new ApiResponse<>(result, "버스가 성공적으로 수정되었습니다."));
    }

    /**
     * 조직별 모든 버스 조회 (상태 정보 포함)
     */
    @GetMapping()
    public ResponseEntity<ApiResponse<List<BusStatusDTO>>> getAllBuses(
            @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 버스를 조회할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String organizationId = (String) userInfo.get("organizationId");

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 버스를 조회할 수 없습니다.");
        }

        log.info("조직별 버스 목록 조회 요청 - 조직: {}", organizationId);
        List<BusStatusDTO> buses = busService.getAllBusStatusByOrganizationId(organizationId);

        return ResponseEntity.ok(new ApiResponse<>(buses, "모든 버스가 성공적으로 조회되었습니다."));
    }

    /**
     * 운영 상태별 버스 조회
     */
    @GetMapping("/operational-status/{status}")
    public ResponseEntity<ApiResponse<List<BusStatusDTO>>> getBusesByOperationalStatus(
            @PathVariable Bus.OperationalStatus status,
            @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 버스를 조회할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String organizationId = (String) userInfo.get("organizationId");

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 버스를 조회할 수 없습니다.");
        }

        log.info("운영 상태별 버스 조회 요청 - 조직: {}, 상태: {}", organizationId, status);
        List<BusStatusDTO> buses = busService.getBusesByOperationalStatus(organizationId, status);

        return ResponseEntity.ok(new ApiResponse<>(buses, status + " 상태의 버스가 성공적으로 조회되었습니다."));
    }

    /**
     * 서비스 상태별 버스 조회
     */
    @GetMapping("/service-status/{status}")
    public ResponseEntity<ApiResponse<List<BusStatusDTO>>> getBusesByServiceStatus(
            @PathVariable Bus.ServiceStatus status,
            @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 버스를 조회할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String organizationId = (String) userInfo.get("organizationId");

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 버스를 조회할 수 없습니다.");
        }

        log.info("서비스 상태별 버스 조회 요청 - 조직: {}, 상태: {}", organizationId, status);
        List<BusStatusDTO> buses = busService.getBusesByServiceStatus(organizationId, status);

        return ResponseEntity.ok(new ApiResponse<>(buses, status + " 상태의 버스가 성공적으로 조회되었습니다."));
    }

    /**
     * 현재 운행 가능한 버스 조회
     */
    @GetMapping("/operational")
    public ResponseEntity<ApiResponse<List<BusStatusDTO>>> getOperationalBuses(
            @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 버스를 조회할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String organizationId = (String) userInfo.get("organizationId");

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 버스를 조회할 수 없습니다.");
        }

        log.info("운행 가능한 버스 목록 조회 요청 - 조직: {}", organizationId);
        List<BusStatusDTO> operationalBuses = busService.getOperationalBuses(organizationId);

        return ResponseEntity.ok(new ApiResponse<>(operationalBuses, "운행 가능한 버스가 성공적으로 조회되었습니다."));
    }

    /**
     * 버스 상태 변경
     */
    @PutMapping("/{busNumber}/status")
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<ApiResponse<Boolean>> updateBusStatus(
            @PathVariable String busNumber,
            @RequestParam(required = false) Bus.OperationalStatus operationalStatus,
            @RequestParam(required = false) Bus.ServiceStatus serviceStatus,
            @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 버스 상태를 변경할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String organizationId = (String) userInfo.get("organizationId");

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 버스 상태를 변경할 수 없습니다.");
        }

        log.info("버스 상태 변경 요청 - 버스 번호: {}, 조직: {}, 운영상태: {}, 서비스상태: {}",
                busNumber, organizationId, operationalStatus, serviceStatus);

        boolean result = busService.updateBusStatus(busNumber, organizationId, operationalStatus, serviceStatus);

        return ResponseEntity.ok(new ApiResponse<>(result, "버스 상태가 성공적으로 변경되었습니다."));
    }

    /**
     * 버스 삭제
     */
    @DeleteMapping("/{busNumber}")
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<ApiResponse<Boolean>> deleteBus(
            @PathVariable String busNumber,
            @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 버스를 삭제할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String organizationId = (String) userInfo.get("organizationId");

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 버스를 삭제할 수 없습니다.");
        }

        log.info("버스 삭제 요청 - 버스 번호: {}, 조직: {}", busNumber, organizationId);
        boolean result = busService.removeBus(busNumber, organizationId);

        return ResponseEntity.ok(new ApiResponse<>(result, "성공적으로 버스가 삭제되었습니다."));
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