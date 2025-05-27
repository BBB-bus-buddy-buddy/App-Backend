package capston2024.bustracker.controller;

import capston2024.bustracker.config.dto.ApiResponse;
import capston2024.bustracker.config.dto.busOperation.BusOperationCreateDTO;
import capston2024.bustracker.config.dto.busOperation.BusOperationDTO;
import capston2024.bustracker.config.dto.busOperation.BusOperationStatusUpdateDTO;
import capston2024.bustracker.domain.BusOperation;
import capston2024.bustracker.exception.BusinessException;
import capston2024.bustracker.exception.ResourceNotFoundException;
import capston2024.bustracker.exception.UnauthorizedException;
import capston2024.bustracker.service.AuthService;
import capston2024.bustracker.service.BusOperationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
@Tag(name = "버스 운행 관리", description = "버스 운행 스케줄 및 상태 관리 API")
@SecurityRequirement(name = "bearerAuth")
public class BusOperationController {

    private final BusOperationService busOperationService;
    private final AuthService authService;

    @PostMapping
    @PreAuthorize("hasRole('STAFF')")
    @Operation(
            summary = "버스 운행 생성",
            description = "새로운 버스 운행 스케줄을 생성합니다. STAFF 권한이 필요합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201", description = "운행 생성 성공",
                    content = @Content(schema = @Schema(implementation = BusOperationDTO.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음")
    })
    public ResponseEntity<ApiResponse<BusOperationDTO>> createBusOperation(
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal,
            @Parameter(description = "버스 운행 생성 정보", required = true)
            @Valid @RequestBody BusOperationCreateDTO createDTO) {

        String organizationId = validateAndGetOrganizationId(principal);

        log.info("버스 운행 생성 요청 - 조직: {}, 버스: {}", organizationId, createDTO.getBusId());

        BusOperationDTO createdOperation = busOperationService.createBusOperation(organizationId, createDTO);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(createdOperation, "버스 운행이 성공적으로 생성되었습니다."));
    }

    @PutMapping("/status")
    @PreAuthorize("hasRole('DRIVER') or hasRole('STAFF')")
    @Operation(
            summary = "운행 상태 업데이트",
            description = "버스 운행 상태를 업데이트합니다. DRIVER 또는 STAFF 권한이 필요합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "상태 업데이트 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "운행을 찾을 수 없음")
    })
    public ResponseEntity<ApiResponse<BusOperationDTO>> updateOperationStatus(
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal,
            @Parameter(description = "운행 상태 업데이트 정보", required = true)
            @Valid @RequestBody BusOperationStatusUpdateDTO updateDTO) {

        String organizationId = validateAndGetOrganizationId(principal);

        log.info("운행 상태 업데이트 요청 - 운행 ID: {}, 상태: {}",
                updateDTO.getOperationId(), updateDTO.getStatus());

        BusOperationDTO updatedOperation = busOperationService.updateOperationStatus(organizationId, updateDTO);

        return ResponseEntity.ok(new ApiResponse<>(updatedOperation, "운행 상태가 성공적으로 업데이트되었습니다."));
    }

    @GetMapping
    @Operation(
            summary = "조직의 모든 운행 조회",
            description = "현재 사용자 조직의 모든 버스 운행을 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    public ResponseEntity<ApiResponse<List<BusOperationDTO>>> getAllOperations(
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal) {

        String organizationId = validateAndGetOrganizationId(principal);

        log.info("조직 {}의 모든 운행 조회 요청", organizationId);

        List<BusOperationDTO> operations = busOperationService.getAllOperationsByOrganization(organizationId);

        return ResponseEntity.ok(new ApiResponse<>(operations, "운행 목록이 성공적으로 조회되었습니다."));
    }

    @GetMapping("/status/{status}")
    @Operation(
            summary = "운행 상태별 조회",
            description = "특정 상태의 버스 운행들을 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 상태값")
    })
    public ResponseEntity<ApiResponse<List<BusOperationDTO>>> getOperationsByStatus(
            @Parameter(description = "운행 상태", required = true,
                    schema = @Schema(allowableValues = {"SCHEDULED", "IN_PROGRESS", "COMPLETED", "CANCELLED"}))
            @PathVariable BusOperation.OperationStatus status,
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal) {

        String organizationId = validateAndGetOrganizationId(principal);

        log.info("조직 {}의 {} 상태 운행 조회 요청", organizationId, status);

        List<BusOperationDTO> operations = busOperationService.getOperationsByStatus(organizationId, status);

        return ResponseEntity.ok(new ApiResponse<>(operations,
                String.format("%s 상태의 운행 목록이 성공적으로 조회되었습니다.", status)));
    }

    @GetMapping("/my-schedule")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(
            summary = "기사의 오늘 운행 스케줄 조회",
            description = "로그인한 기사의 오늘 운행 스케줄을 조회합니다. DRIVER 권한이 필요합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "기사 권한 필요")
    })
    public ResponseEntity<ApiResponse<List<BusOperationDTO>>> getMyTodaySchedule(
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal) {

        Map<String, Object> userInfo = getUserInfo(principal);
        String driverId = (String) userInfo.get("email");

        log.info("기사 {}의 오늘 운행 스케줄 조회 요청", driverId);

        List<BusOperationDTO> operations = busOperationService.getTodayOperationsByDriver(driverId);

        return ResponseEntity.ok(new ApiResponse<>(operations, "오늘의 운행 스케줄이 성공적으로 조회되었습니다."));
    }

    @GetMapping("/{operationId}")
    @Operation(
            summary = "운행 상세 조회",
            description = "특정 운행의 상세 정보를 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "운행을 찾을 수 없음")
    })
    public ResponseEntity<ApiResponse<BusOperationDTO>> getOperationById(
            @Parameter(description = "운행 ID", required = true) @PathVariable String operationId,
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal) {

        String organizationId = validateAndGetOrganizationId(principal);

        log.info("운행 상세 조회 요청 - 운행 ID: {}", operationId);

        BusOperationDTO operation = busOperationService.getOperationById(organizationId, operationId);

        return ResponseEntity.ok(new ApiResponse<>(operation, "운행 상세가 성공적으로 조회되었습니다."));
    }

    /**
     * 사용자 인증 및 조직 ID 검증
     */
    private String validateAndGetOrganizationId(OAuth2User principal) {
        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 접근할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String organizationId = (String) userInfo.get("organizationId");

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 이 기능을 사용할 수 없습니다.");
        }

        return organizationId;
    }

    /**
     * 사용자 정보 조회
     */
    private Map<String, Object> getUserInfo(OAuth2User principal) {
        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 접근할 수 있습니다.");
        }
        return authService.getUserDetails(principal);
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