package capston2024.bustracker.controller;

import capston2024.bustracker.config.dto.ApiResponse;
import capston2024.bustracker.config.dto.busEtc.SeatInfoDTO;
import capston2024.bustracker.config.dto.realtime.BusRealtimeStatusDTO;
import capston2024.bustracker.exception.BusinessException;
import capston2024.bustracker.exception.ResourceNotFoundException;
import capston2024.bustracker.exception.UnauthorizedException;
import capston2024.bustracker.service.AuthService;
import capston2024.bustracker.service.BusOperationService;
import capston2024.bustracker.service.RealtimeLocationService;
import capston2024.bustracker.service.SeatInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/seats")
@RequiredArgsConstructor
@Slf4j
public class SeatInfoController {

    private final SeatInfoService seatInfoService;
    private final BusOperationService busOperationService;
    private final RealtimeLocationService realtimeLocationService;
    private final AuthService authService;

    /**
     * 특정 운행의 좌석 정보 조회
     */
    @GetMapping("/operation/{operationId}")
    public ResponseEntity<ApiResponse<SeatInfoDTO>> getOperationSeatInfo(
            @PathVariable String operationId,
            @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 좌석 정보를 조회할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String organizationId = (String) userInfo.get("organizationId");

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 좌석 정보를 조회할 수 없습니다.");
        }

        log.info("운행 {} 좌석 정보 조회 요청", operationId);
        SeatInfoDTO seatInfo = busOperationService.getOperationSeatInfo(operationId, organizationId);

        return ResponseEntity.ok(new ApiResponse<>(seatInfo, "좌석 정보가 성공적으로 조회되었습니다."));
    }

    /**
     * 특정 버스 번호의 현재 좌석 정보 조회
     */
    @GetMapping("/bus/{busNumber}")
    public ResponseEntity<ApiResponse<SeatInfoDTO>> getBusSeatInfo(
            @PathVariable String busNumber,
            @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 좌석 정보를 조회할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String organizationId = (String) userInfo.get("organizationId");

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 좌석 정보를 조회할 수 있습니다.");
        }

        log.info("버스 {} 좌석 정보 조회 요청", busNumber);
        SeatInfoDTO seatInfo = seatInfoService.getBusSeatInfo(busNumber, organizationId);

        return ResponseEntity.ok(new ApiResponse<>(seatInfo, "좌석 정보가 성공적으로 조회되었습니다."));
    }

    /**
     * 조직의 모든 운행 중인 버스 좌석 정보 조회
     */
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<SeatInfoDTO>>> getActiveOperationsSeatInfo(
            @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 좌석 정보를 조회할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String organizationId = (String) userInfo.get("organizationId");

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 좌석 정보를 조회할 수 없습니다.");
        }

        log.info("조직 {} 운행 중인 버스 좌석 정보 조회 요청", organizationId);
        List<SeatInfoDTO> seatInfoList = seatInfoService.getActiveOperationsSeatInfo(organizationId);

        return ResponseEntity.ok(new ApiResponse<>(seatInfoList,
                "운행 중인 버스 좌석 정보가 성공적으로 조회되었습니다."));
    }

    /**
     * 특정 노선의 모든 운행 중인 버스 좌석 정보 조회
     */
    @GetMapping("/route/{routeId}")
    public ResponseEntity<ApiResponse<List<SeatInfoDTO>>> getRouteSeatInfo(
            @PathVariable String routeId,
            @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 좌석 정보를 조회할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String organizationId = (String) userInfo.get("organizationId");

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 좌석 정보를 조회할 수 없습니다.");
        }

        log.info("노선 {} 버스 좌석 정보 조회 요청", routeId);
        List<SeatInfoDTO> seatInfoList = seatInfoService.getRouteSeatInfo(routeId, organizationId);

        return ResponseEntity.ok(new ApiResponse<>(seatInfoList,
                "노선의 버스 좌석 정보가 성공적으로 조회되었습니다."));
    }

    /**
     * 실시간 버스 상태 조회 (좌석 정보 포함)
     */
    @GetMapping("/realtime")
    public ResponseEntity<ApiResponse<List<BusRealtimeStatusDTO>>> getRealtimeBusStatus(
            @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 실시간 정보를 조회할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String organizationId = (String) userInfo.get("organizationId");

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 실시간 정보를 조회할 수 없습니다.");
        }

        log.info("조직 {} 실시간 버스 상태 조회 요청", organizationId);
        List<BusRealtimeStatusDTO> realtimeStatuses = realtimeLocationService
                .getOrganizationBusStatuses(organizationId);

        return ResponseEntity.ok(new ApiResponse<>(realtimeStatuses,
                "실시간 버스 상태가 성공적으로 조회되었습니다."));
    }

    /**
     * 좌석 가용성 기준 버스 조회 (최소 좌석 수 이상)
     */
    @GetMapping("/available")
    public ResponseEntity<ApiResponse<List<SeatInfoDTO>>> getAvailableBuses(
            @RequestParam(defaultValue = "1") int minSeats,
            @RequestParam(required = false) String routeId,
            @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 좌석 정보를 조회할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String organizationId = (String) userInfo.get("organizationId");

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 좌석 정보를 조회할 수 없습니다.");
        }

        log.info("최소 {}석 이상 가용한 버스 조회 요청 - 노선: {}", minSeats, routeId);
        List<SeatInfoDTO> availableBuses = seatInfoService
                .getAvailableBuses(organizationId, minSeats, routeId);

        return ResponseEntity.ok(new ApiResponse<>(availableBuses,
                String.format("최소 %d석 이상 가용한 버스가 조회되었습니다.", minSeats)));
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