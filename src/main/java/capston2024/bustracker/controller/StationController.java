package capston2024.bustracker.controller;

import capston2024.bustracker.config.dto.ApiResponse;
import capston2024.bustracker.config.dto.StationRequestDTO;
import capston2024.bustracker.domain.Station;
import capston2024.bustracker.exception.BusinessException;
import capston2024.bustracker.exception.UnauthorizedException;
import capston2024.bustracker.service.AuthService;
import capston2024.bustracker.service.StationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@Slf4j
@RequestMapping("/api/station")
@Tag(name = "정류장 관리", description = "버스 정류장 등록, 수정, 삭제 및 조회 API")
@SecurityRequirement(name = "bearerAuth")
public class StationController {

    private final StationService stationService;
    private final AuthService authService;

    public StationController(StationService stationService, AuthService authService) {
        this.stationService = stationService;
        this.authService = authService;
    }

    @GetMapping
    @Operation(
            summary = "정류장 조회",
            description = "조직의 모든 정류장을 조회하거나 이름으로 검색합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = Station.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    public ResponseEntity<ApiResponse<List<Station>>> getStations(
            @Parameter(description = "정류장 이름 (검색어)") @RequestParam(required = false) String name,
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal) {

        log.info("유저의 principal : {} ", principal);
        if (principal == null) {
            log.warn("No authenticated user found");
            throw new UnauthorizedException("인증된 사용자를 찾을 수 없습니다");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String organizationId = (String) userInfo.get("organizationId");

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 라우트를 조회할 수 없습니다.");
        }

        log.info("정류장 조회 요청 - 조직 ID: {}, 검색어: {}", organizationId, name);

        List<Station> stations;
        if (name != null && !name.trim().isEmpty()) {
            log.info("정류장 이름으로 조회: {}", name);
            stations = stationService.searchStationsByNameAndOrganizationId(name, organizationId);
            return ResponseEntity.ok(new ApiResponse<> (stations, "버스 정류장 검색이 성공적으로 완료되었습니다."));
        } else {
            log.info("모든 정류장 조회 요청");
            stations = stationService.getAllStations(organizationId);
            return ResponseEntity.ok(new ApiResponse<>(stations, "모든 정류장 조회 완료"));
        }
    }

    @PostMapping
    @Transactional
    @Operation(
            summary = "정류장 등록",
            description = "새로운 정류장을 등록합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "등록 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "중복된 정류장")
    })
    public ResponseEntity<ApiResponse<Station>> createStation(
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal,
            @Parameter(description = "정류장 정보", required = true) @RequestBody StationRequestDTO createStationDTO) {

        log.info("새로운 정류장 등록 요청: {}", createStationDTO.getName());
        if (principal == null) {
            log.warn("No authenticated user found");
            throw new UnauthorizedException("인증된 사용자만 라우트를 조회할 수 있습니다.");
        }
        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String organizationId = (String) userInfo.get("organizationId");

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 라우트를 조회할 수 없습니다.");
        }

        try {
            Station createdStation = stationService.createStation(organizationId, createStationDTO);
            return ResponseEntity.ok(new ApiResponse<>(createdStation, "정류장이 성공적으로 등록되었습니다."));
        } catch (BusinessException e) {
            log.error("정류장 등록 실패: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiResponse<>(null, "중복된 정류장이 이미 존재합니다."));
        }
    }

    @PutMapping("/{id}")
    @Transactional
    @Operation(
            summary = "정류장 수정",
            description = "기존 정류장의 정보를 수정합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "수정 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "정류장을 찾을 수 없음")
    })
    public ResponseEntity<ApiResponse<String>> updateStation(
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal,
            @Parameter(description = "정류장 ID", required = true) @PathVariable String id,
            @Parameter(description = "수정할 정류장 정보", required = true) @RequestBody StationRequestDTO stationRequestDTO) {

        if (principal == null) {
            log.warn("No authenticated user found");
            throw new UnauthorizedException("인증된 사용자만 라우트를 조회할 수 있습니다.");
        }

        log.info("{} 정류장 업데이트 요청 (ID: {})", stationRequestDTO.getName(), id);
        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String organizationId = (String) userInfo.get("organizationId");

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 라우트를 조회할 수 없습니다.");
        }

        boolean result = stationService.updateStation(organizationId, id, stationRequestDTO);

        if (result) {
            return ResponseEntity.ok(new ApiResponse<>(null, "정류장이 성공적으로 업데이트되었습니다."));
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(null, "정류장 업데이트에 실패했습니다."));
        }
    }

    @DeleteMapping("/{id}")
    @Transactional
    @Operation(
            summary = "정류장 삭제",
            description = "정류장을 삭제합니다. 해당 정류장을 포함하는 모든 노선에서도 제거됩니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "정류장을 찾을 수 없음")
    })
    public ResponseEntity<ApiResponse<Void>> deleteStation(
            @Parameter(description = "정류장 ID", required = true) @PathVariable String id) {

        log.info("정류장 ID {}로 삭제 요청", id);
        stationService.deleteStation(id);
        return ResponseEntity.ok(new ApiResponse<>(null, "정류장이 성공적으로 삭제되었습니다."));
    }
}