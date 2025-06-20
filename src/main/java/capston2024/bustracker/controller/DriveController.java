package capston2024.bustracker.controller;

import capston2024.bustracker.config.dto.*;
import capston2024.bustracker.exception.BusinessException;
import capston2024.bustracker.exception.UnauthorizedException;
import capston2024.bustracker.service.AuthService;
import capston2024.bustracker.service.DriveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

import java.util.Map;

@RestController
@RequestMapping("/api/drives")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Drive", description = "운행 시작/종료 관리 API")
public class DriveController {

    private final DriveService driveService;
    private final AuthService authService;

    /**
     * 운행 시작
     */
    @PostMapping("/start")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(summary = "운행 시작",
            description = "운전자가 운행을 시작합니다. 출발지 확인 후 버스 상태와 운행 일정을 업데이트합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "운행 시작 성공",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청 데이터"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음 (운전자 권한 필요)")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<ApiResponse<DriveStatusDTO>> startDrive(
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal,
            @Parameter(description = "운행 시작 요청 데이터") @RequestBody DriveStartRequestDTO requestDTO) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 운행을 시작할 수 있습니다.");
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
            throw new BusinessException("조직에 속하지 않은 사용자는 운행을 시작할 수 없습니다.");
        }

        log.info("운행 시작 요청 - 운전자: {}, 운행일정ID: {}, 조기출발: {}",
                driverId, requestDTO.getOperationId(), requestDTO.isEarlyStart());

        DriveStatusDTO result = driveService.startDrive(requestDTO, driverId, organizationId);

        return ResponseEntity.ok(new ApiResponse<>(result, "운행이 성공적으로 시작되었습니다."));
    }

    /**
     * 운행 종료
     */
    @PostMapping("/end")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(summary = "운행 종료",
            description = "운전자가 운행을 종료합니다. 버스 상태와 운행 일정을 업데이트합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "운행 종료 성공",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청 데이터"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음 (운전자 권한 필요)")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<ApiResponse<DriveStatusDTO>> endDrive(
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal,
            @Parameter(description = "운행 종료 요청 데이터") @RequestBody DriveEndRequestDTO requestDTO) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 운행을 종료할 수 있습니다.");
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
            throw new BusinessException("조직에 속하지 않은 사용자는 운행을 종료할 수 없습니다.");
        }

        log.info("운행 종료 요청 - 운전자: {}, 운행일정ID: {}", driverId, requestDTO.getOperationId());

        DriveStatusDTO result = driveService.endDrive(requestDTO, driverId, organizationId);

        return ResponseEntity.ok(new ApiResponse<>(result, "운행이 성공적으로 종료되었습니다."));
    }

    /**
     * 운행 중 위치 업데이트
     * @deprecated WebSocket을 통한 실시간 위치 업데이트로 대체됨
     *
     * 이 엔드포인트는 더 이상 사용되지 않습니다.
     * 운전자 앱은 WebSocket 연결을 통해 실시간으로 위치를 전송하며,
     * BusService의 flushLocationUpdates()가 주기적으로 위치 정보를 DB에 반영합니다.
     */
    @Deprecated
    @PostMapping("/location")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(summary = "운행 중 위치 업데이트 (Deprecated)",
            description = "이 API는 더 이상 사용되지 않습니다. WebSocket을 통한 실시간 위치 업데이트를 사용하세요.",
            deprecated = true)
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateLocation(
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal,
            @Parameter(description = "위치 업데이트 요청 데이터") @RequestBody DriveLocationUpdateDTO requestDTO) {

        log.warn("Deprecated API 호출 - /api/drives/location 대신 WebSocket을 사용하세요.");

        return ResponseEntity.ok(new ApiResponse<>(
                Map.of(
                        "warning", "이 API는 더 이상 사용되지 않습니다. WebSocket을 통한 실시간 위치 업데이트를 사용하세요.",
                        "updated", false
                ),
                "WebSocket을 사용하세요."
        ));
    }

    /**
     * 다음 운행 정보 조회
     */
    @GetMapping("/next")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(summary = "다음 운행 정보 조회",
            description = "현재 운행 종료 후 다음 운행 일정이 있는지 조회합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "다음 운행 정보 조회 성공",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음 (운전자 권한 필요)")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<ApiResponse<DriveStatusDTO>> getNextDrive(
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal,
            @Parameter(description = "현재 운행 일정 ID") @RequestParam String currentOperationId,
            @Parameter(description = "버스 번호") @RequestParam String busNumber) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 다음 운행 정보를 조회할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String driverId = (String) userInfo.get("email");
        String organizationId = (String) userInfo.get("organizationId");
        String role = (String) userInfo.get("role");

        // 운전자 권한 확인
        if (!"ROLE_DRIVER".equals(role)) {
            throw new UnauthorizedException("운전자 권한이 필요합니다.");
        }

        log.info("다음 운행 정보 조회 - 운전자: {}, 현재운행: {}, 버스: {}",
                driverId, currentOperationId, busNumber);

        DriveStatusDTO nextDrive = driveService.getNextDrive(currentOperationId, busNumber, driverId, organizationId);

        if (nextDrive != null) {
            return ResponseEntity.ok(new ApiResponse<>(nextDrive, "다음 운행 정보가 조회되었습니다."));
        } else {
            return ResponseEntity.ok(new ApiResponse<>(null, "다음 운행 일정이 없습니다."));
        }
    }

    /**
     * 운행 상태 조회
     * 프론트엔드 drive.js의 getDriveStatus와 호환
     */
    @GetMapping("/status/{operationId}")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(summary = "운행 상태 조회",
            description = "특정 운행 일정의 현재 상태를 조회합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "운행 상태 조회 성공",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음 (운전자 권한 필요)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "운행 일정을 찾을 수 없음")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<ApiResponse<DriveStatusDTO>> getDriveStatus(
            @Parameter(description = "운행 일정 ID") @PathVariable String operationId,
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 운행 상태를 조회할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String driverId = (String) userInfo.get("email");
        String organizationId = (String) userInfo.get("organizationId");
        String role = (String) userInfo.get("role");

        // 운전자 권한 확인
        if (!"ROLE_DRIVER".equals(role)) {
            throw new UnauthorizedException("운전자 권한이 필요합니다.");
        }

        log.info("운행 상태 조회 - 운전자: {}, 운행일정ID: {}", driverId, operationId);

        DriveStatusDTO driveStatus = driveService.getDriveStatus(operationId, driverId, organizationId);

        return ResponseEntity.ok(new ApiResponse<>(driveStatus, "운행 상태가 성공적으로 조회되었습니다."));
    }
}