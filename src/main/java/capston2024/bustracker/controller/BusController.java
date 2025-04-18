package capston2024.bustracker.controller;

import capston2024.bustracker.config.dto.*;
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
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> createBus(
            @AuthenticationPrincipal OAuth2User principal,
            @RequestBody BusRegisterDTO busRegisterDTO) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 버스를 등록할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String organizationId = (String) userInfo.get("organizationId");

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 버스를 등록할 수 없습니다.");
        }

        log.info("버스 등록 요청 - 조직: {}, 노선: {}", organizationId, busRegisterDTO.getRouteId());
        String busNumber = busService.createBus(busRegisterDTO, organizationId);

        return ResponseEntity.ok(new ApiResponse<>(busNumber, "성공적으로 버스가 추가되었습니다."));
    }

    /**
     * 버스 삭제
     */
    @DeleteMapping("/{busNumber}")
    @PreAuthorize("hasRole('ADMIN')")
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
     * 버스 수정
     */
    @PutMapping()
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Boolean>> updateBus(
            @AuthenticationPrincipal OAuth2User principal,
            @RequestBody BusModifyDTO busModifyDTO) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 버스를 수정할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String organizationId = (String) userInfo.get("organizationId");

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 버스를 수정할 수 없습니다.");
        }

        if (busModifyDTO.getBusNumber() == null || busModifyDTO.getBusNumber().trim().isEmpty()) {
            throw new BusinessException("버스 번호는 필수입니다.");
        }

        log.info("버스 수정 요청 - 버스 번호: {}, 조직: {}", busModifyDTO.getBusNumber(), organizationId);
        boolean result = busService.modifyBus(busModifyDTO, organizationId);

        return ResponseEntity.ok(new ApiResponse<>(result, "버스가 성공적으로 수정되었습니다."));
    }

    /**
     * 조직별 모든 버스 조회
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
     * 특정 버스 조회
     */
    @GetMapping("/{busNumber}")
    public ResponseEntity<ApiResponse<BusStatusDTO>> getBusByNumber(
            @PathVariable String busNumber,
            @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 버스를 조회할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String organizationId = (String) userInfo.get("organizationId");

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 버스를 조회할 수 없습니다.");
        }

        log.info("특정 버스 조회 요청 - 버스 번호: {}, 조직: {}", busNumber, organizationId);
        Bus bus = busService.getBusByNumberAndOrganization(busNumber, organizationId);
        List<BusStatusDTO> allBuses = busService.getAllBusStatusByOrganizationId(organizationId);

        BusStatusDTO busStatus = allBuses.stream()
                .filter(dto -> dto.getBusNumber().equals(busNumber))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("버스를 찾을 수 없습니다: " + busNumber));

        return ResponseEntity.ok(new ApiResponse<>(busStatus, "버스가 성공적으로 조회되었습니다."));
    }

    /**
     * 특정 정류장을 경유하는 버스 조회
     */
    @GetMapping("/station/{stationId}")
    public ResponseEntity<ApiResponse<List<BusStatusDTO>>> getBusesByStation(
            @PathVariable String stationId,
            @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 버스를 조회할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String organizationId = (String) userInfo.get("organizationId");

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 버스를 조회할 수 없습니다.");
        }

        log.info("특정 정류장 경유 버스 조회 요청 - 정류장 ID: {}, 조직: {}", stationId, organizationId);
        List<BusStatusDTO> buses = busService.getBusesByStationAndOrganization(stationId, organizationId);

        return ResponseEntity.ok(new ApiResponse<>(buses, "정류장을 경유하는 버스가 성공적으로 조회되었습니다."));
    }

    /**
     * 버스 좌석 조회
     */
    @GetMapping("/seats/{busNumber}")
    public ResponseEntity<ApiResponse<BusSeatDTO>> getBusSeatsByBusNumber(
            @PathVariable String busNumber,
            @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 좌석 정보를 조회할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String organizationId = (String) userInfo.get("organizationId");

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 좌석 정보를 조회할 수 없습니다.");
        }

        log.info("버스 좌석 조회 요청 - 버스 번호: {}, 조직: {}", busNumber, organizationId);
        BusSeatDTO seats = busService.getBusSeatsByBusNumber(busNumber, organizationId);

        return ResponseEntity.ok(new ApiResponse<>(seats, "좌석이 성공적으로 반환되었습니다."));
    }

    /**
     * 버스 위치 조회
     */
    @GetMapping("/location/{busNumber}")
    public ResponseEntity<ApiResponse<LocationDTO>> getBusLocationByBusNumber(
            @PathVariable String busNumber,
            @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 위치 정보를 조회할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String organizationId = (String) userInfo.get("organizationId");

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 위치 정보를 조회할 수 없습니다.");
        }

        log.info("버스 위치 조회 요청 - 버스 번호: {}, 조직: {}", busNumber, organizationId);
        LocationDTO locations = busService.getBusLocationByBusNumber(busNumber, organizationId);

        return ResponseEntity.ok(new ApiResponse<>(locations, "좌표 정보가 성공적으로 반환되었습니다."));
    }

    /**
     * 비즈니스 로직 예외 처리
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequestException(BusinessException ex) {
        log.error("비지니스 서비스 로직 예외: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(null, ex.getMessage()));
    }

    /**
     * 인증 예외 처리
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorizedException(UnauthorizedException ex) {
        log.error("인가되지 않은 리소스: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(null, ex.getMessage()));
    }

    /**
     * 리소스 찾을 수 없음 예외 처리
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFoundException(ResourceNotFoundException ex) {
        log.error("존재하지 않는 리소스: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(null, ex.getMessage()));
    }

    /**
     * 일반 예외 처리
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(Exception ex) {
        log.error("일반 예외 발생: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(null, "서버 오류가 발생했습니다."));
    }
}