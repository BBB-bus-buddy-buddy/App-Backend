package capston2024.bustracker.controller;

import capston2024.bustracker.config.dto.*;
import capston2024.bustracker.config.dto.bus.BusCreateDTO;
import capston2024.bustracker.config.dto.bus.BusStatusDTO;
import capston2024.bustracker.config.dto.bus.BusUpdateDTO;
import capston2024.bustracker.config.dto.busEtc.BusRouteInfoDTO;
import capston2024.bustracker.config.dto.busEtc.StationBusDTO;
import capston2024.bustracker.domain.Bus;
import capston2024.bustracker.exception.BusinessException;
import capston2024.bustracker.exception.ResourceNotFoundException;
import capston2024.bustracker.exception.UnauthorizedException;
import capston2024.bustracker.service.AuthService;
import capston2024.bustracker.service.BusService;
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
@Tag(name = "버스 관리", description = "버스 등록, 수정, 삭제 및 상태 관리 API")
@SecurityRequirement(name = "bearerAuth")
public class BusController {

    private final BusService busService;
    private final AuthService authService;

    @PostMapping()
    @PreAuthorize("hasRole('STAFF')")
    @Operation(
            summary = "버스 등록",
            description = "새로운 버스를 등록합니다. STAFF 권한이 필요합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "등록 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음")
    })
    public ResponseEntity<ApiResponse<String>> createBus(
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal,
            @Parameter(description = "버스 생성 정보", required = true)
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

    @PutMapping()
    @PreAuthorize("hasRole('STAFF')")
    @Operation(
            summary = "버스 정보 수정",
            description = "기존 버스의 정보를 수정합니다. STAFF 권한이 필요합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "버스를 찾을 수 없음")
    })
    public ResponseEntity<ApiResponse<Boolean>> updateBus(
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal,
            @Parameter(description = "버스 수정 정보", required = true)
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

    @GetMapping()
    @Operation(
            summary = "조직별 모든 버스 조회",
            description = "현재 사용자가 속한 조직의 모든 버스를 조회합니다. 운행 상태 정보도 포함됩니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = BusStatusDTO.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    public ResponseEntity<ApiResponse<List<BusStatusDTO>>> getAllBuses(
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal) {

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

    @GetMapping("/operational-status/{status}")
    @Operation(
            summary = "운영 상태별 버스 조회",
            description = "특정 운영 상태(ACTIVE, INACTIVE, MAINTENANCE, RETIRED)의 버스를 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    public ResponseEntity<ApiResponse<List<BusStatusDTO>>> getBusesByOperationalStatus(
            @Parameter(description = "운영 상태", required = true,
                    schema = @Schema(allowableValues = {"ACTIVE", "INACTIVE", "MAINTENANCE", "RETIRED"}))
            @PathVariable Bus.OperationalStatus status,
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal) {

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

    @GetMapping("/service-status/{status}")
    @Operation(
            summary = "서비스 상태별 버스 조회",
            description = "특정 서비스 상태(NOT_IN_SERVICE, IN_SERVICE, OUT_OF_ORDER, CLEANING)의 버스를 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    public ResponseEntity<ApiResponse<List<BusStatusDTO>>> getBusesByServiceStatus(
            @Parameter(description = "서비스 상태", required = true,
                    schema = @Schema(allowableValues = {"NOT_IN_SERVICE", "IN_SERVICE", "OUT_OF_ORDER", "CLEANING"}))
            @PathVariable Bus.ServiceStatus status,
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 버스를 조회할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String organizationId = (String) userInfo.get("organizationId");

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 버스를 조회할 수 있습니다.");
        }

        log.info("서비스 상태별 버스 조회 요청 - 조직: {}, 상태: {}", organizationId, status);
        List<BusStatusDTO> buses = busService.getBusesByServiceStatus(organizationId, status);

        return ResponseEntity.ok(new ApiResponse<>(buses, status + " 상태의 버스가 성공적으로 조회되었습니다."));
    }

    @GetMapping("/operational")
    @Operation(
            summary = "운행 가능한 버스 조회",
            description = "현재 운행 가능한 상태(ACTIVE & IN_SERVICE)의 버스를 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    public ResponseEntity<ApiResponse<List<BusStatusDTO>>> getOperationalBuses(
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal) {

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

    @PutMapping("/{busNumber}/status")
    @PreAuthorize("hasRole('STAFF')")
    @Operation(
            summary = "버스 상태 변경",
            description = "버스의 운영 상태 또는 서비스 상태를 변경합니다. STAFF 권한이 필요합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "상태 변경 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "버스를 찾을 수 없음")
    })
    public ResponseEntity<ApiResponse<Boolean>> updateBusStatus(
            @Parameter(description = "버스 번호", required = true) @PathVariable String busNumber,
            @Parameter(description = "운영 상태 (선택)") @RequestParam(required = false) Bus.OperationalStatus operationalStatus,
            @Parameter(description = "서비스 상태 (선택)") @RequestParam(required = false) Bus.ServiceStatus serviceStatus,
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal) {

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

    @DeleteMapping("/{busNumber}")
    @PreAuthorize("hasRole('STAFF')")
    @Operation(
            summary = "버스 삭제",
            description = "버스를 삭제합니다. 진행 중인 운행이 없어야 삭제 가능합니다. STAFF 권한이 필요합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "진행 중인 운행이 있어 삭제 불가"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "버스를 찾을 수 없음")
    })
    public ResponseEntity<ApiResponse<Boolean>> deleteBus(
            @Parameter(description = "버스 번호", required = true) @PathVariable String busNumber,
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal) {

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


    @GetMapping("/station/{stationId}")
    @Operation(
            summary = "특정 정류장을 경유하는 버스 조회",
            description = "특정 정류장을 경유하는 모든 운행 중인 버스의 실시간 정보를 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "정류장을 찾을 수 없음")
    })
    public ResponseEntity<ApiResponse<List<StationBusDTO>>> getBusesByStation(
            @Parameter(description = "정류장 ID", required = true) @PathVariable String stationId) {

        log.info("정류장 {}을 경유하는 버스 조회 요청", stationId);
        List<StationBusDTO> buses = busService.getBusesByStation(stationId);

        return ResponseEntity.ok(new ApiResponse<>(buses, "정류장을 경유하는 버스가 성공적으로 조회되었습니다."));
    }

    @GetMapping("/{busNumber}/route-info")
    @Operation(
            summary = "버스 노선 정보 및 현재 위치 조회",
            description = "특정 버스의 노선 정보, 현재 위치, 다음 정류장까지의 시간을 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "버스를 찾을 수 없음")
    })
    public ResponseEntity<ApiResponse<BusRouteInfoDTO>> getBusRouteInfo(
            @Parameter(description = "버스 번호", required = true) @PathVariable String busNumber,
            @Parameter(description = "조직 ID", required = false) @RequestParam(required = false) String organizationId) {

        log.info("버스 {} 노선 정보 조회 요청", busNumber);
        BusRouteInfoDTO routeInfo = busService.getBusRouteInfo(busNumber, organizationId);

        return ResponseEntity.ok(new ApiResponse<>(routeInfo, "버스 노선 정보가 성공적으로 조회되었습니다."));
    }
}