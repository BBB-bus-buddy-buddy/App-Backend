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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "좌석 정보", description = "실시간 버스 좌석 정보 조회 API - 운행별, 버스별, 노선별 좌석 현황 제공")
@SecurityRequirement(name = "bearerAuth")
public class SeatInfoController {

    private final SeatInfoService seatInfoService;
    private final BusOperationService busOperationService;
    private final RealtimeLocationService realtimeLocationService;
    private final AuthService authService;

    /**
     * 특정 운행의 좌석 정보 조회
     */
    @GetMapping("/operation/{operationId}")
    @Operation(
            summary = "운행별 좌석 정보 조회",
            description = "특정 운행 ID의 실시간 좌석 정보를 조회합니다. 총 좌석 수, 현재 승객 수, 가용 좌석 수 등이 포함됩니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "좌석 정보 조회 성공",
                    content = @Content(schema = @Schema(implementation = SeatInfoDTO.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "접근 권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "운행을 찾을 수 없음")
    })
    public ResponseEntity<ApiResponse<SeatInfoDTO>> getOperationSeatInfo(
            @Parameter(description = "운행 ID", required = true, example = "OP12341234567890abcd")
            @PathVariable String operationId,
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal) {

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
    @Operation(
            summary = "버스별 좌석 정보 조회",
            description = "특정 버스 번호의 현재 좌석 정보를 조회합니다. 운행 중이 아닌 경우 기본 정보가 반환됩니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "좌석 정보 조회 성공",
                    content = @Content(schema = @Schema(implementation = SeatInfoDTO.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "접근 권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "버스를 찾을 수 없음")
    })
    public ResponseEntity<ApiResponse<SeatInfoDTO>> getBusSeatInfo(
            @Parameter(description = "버스 번호", required = true, example = "1234")
            @PathVariable String busNumber,
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal) {

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
    @Operation(
            summary = "운행 중인 모든 버스 좌석 정보 조회",
            description = "조직에 속한 모든 운행 중인 버스의 좌석 정보를 조회합니다. 실시간 좌석 현황을 한 번에 확인할 수 있습니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "좌석 정보 목록 조회 성공",
                    content = @Content(schema = @Schema(implementation = SeatInfoDTO.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "접근 권한 없음")
    })
    public ResponseEntity<ApiResponse<List<SeatInfoDTO>>> getActiveOperationsSeatInfo(
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal) {

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
    @Operation(
            summary = "노선별 버스 좌석 정보 조회",
            description = "특정 노선에 할당된 모든 운행 중인 버스의 좌석 정보를 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "노선별 좌석 정보 조회 성공",
                    content = @Content(schema = @Schema(implementation = SeatInfoDTO.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "접근 권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "노선을 찾을 수 없음")
    })
    public ResponseEntity<ApiResponse<List<SeatInfoDTO>>> getRouteSeatInfo(
            @Parameter(description = "노선 ID", required = true, example = "60a1b2c3d4e5f6789012345")
            @PathVariable String routeId,
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal) {

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
    @Operation(
            summary = "실시간 버스 상태 조회",
            description = "조직의 모든 운행 중인 버스의 실시간 상태를 조회합니다. 위치 정보와 좌석 정보가 함께 제공됩니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "실시간 상태 조회 성공",
                    content = @Content(schema = @Schema(implementation = BusRealtimeStatusDTO.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "접근 권한 없음")
    })
    public ResponseEntity<ApiResponse<List<BusRealtimeStatusDTO>>> getRealtimeBusStatus(
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal) {

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
    @Operation(
            summary = "가용 좌석 기준 버스 조회",
            description = "지정된 최소 좌석 수 이상의 가용 좌석을 가진 버스를 조회합니다. 특정 노선으로 필터링할 수 있습니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "가용 버스 조회 성공",
                    content = @Content(schema = @Schema(implementation = SeatInfoDTO.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "접근 권한 없음")
    })
    public ResponseEntity<ApiResponse<List<SeatInfoDTO>>> getAvailableBuses(
            @Parameter(description = "최소 필요 좌석 수", example = "1")
            @RequestParam(defaultValue = "1") int minSeats,
            @Parameter(description = "노선 ID (선택사항)", example = "60a1b2c3d4e5f6789012345")
            @RequestParam(required = false) String routeId,
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal) {

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