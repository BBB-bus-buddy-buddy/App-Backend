package capston2024.bustracker.controller;

import capston2024.bustracker.config.dto.ApiResponse;
import capston2024.bustracker.config.dto.RouteDTO;
import capston2024.bustracker.config.dto.RouteRequestDTO;
import capston2024.bustracker.config.dto.RouteUpdateRequestDTO;
import capston2024.bustracker.exception.BusinessException;
import capston2024.bustracker.exception.ResourceNotFoundException;
import capston2024.bustracker.exception.UnauthorizedException;
import capston2024.bustracker.service.AuthService;
import capston2024.bustracker.service.RouteService;
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
@RequestMapping("/api/routes")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "노선 관리", description = "버스 노선 등록, 수정, 삭제 및 조회 API")
@SecurityRequirement(name = "bearerAuth")
public class RouteController {

    private final RouteService routeService;
    private final AuthService authService;

    @GetMapping
    @Operation(
            summary = "노선 조회",
            description = "조직의 모든 노선을 조회하거나 이름으로 검색합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = RouteDTO.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    public ResponseEntity<ApiResponse<List<RouteDTO>>> getRoutesByOrganization(
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal,
            @Parameter(description = "노선 이름 (검색어)") @RequestParam(required = false) String name) {

        if (principal == null) {
            log.warn("No authenticated user found");
            throw new UnauthorizedException("인증된 사용자만 라우트를 조회할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String organizationId = (String) userInfo.get("organizationId");

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 라우트를 조회할 수 없습니다.");
        }

        log.info("라우트 조회 요청 - 조직 ID: {}, 검색어: {}", organizationId, name);

        List<RouteDTO> routes;
        if (name != null && !name.trim().isEmpty()) {
            routes = routeService.searchRoutesByNameAndOrganizationId(name, organizationId);
            return ResponseEntity.ok(new ApiResponse<>(routes, "라우트 검색 결과입니다."));
        } else {
            routes = routeService.getAllRoutesByOrganizationId(organizationId);
            return ResponseEntity.ok(new ApiResponse<>(routes, "조직의 모든 라우트 조회 결과입니다."));
        }
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "노선 상세 조회",
            description = "특정 노선의 상세 정보를 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "접근 권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "노선을 찾을 수 없음")
    })
    public ResponseEntity<ApiResponse<RouteDTO>> getRouteById(
            @Parameter(description = "노선 ID", required = true) @PathVariable String id,
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 라우트를 조회할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String organizationId = (String) userInfo.get("organizationId");

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 라우트를 조회할 수 없습니다.");
        }

        log.info("라우트 상세 조회 요청 - ID: {}, 조직 ID: {}", id, organizationId);
        RouteDTO route = routeService.getRouteById(id, organizationId);
        return ResponseEntity.ok(new ApiResponse<>(route, "라우트 조회 결과입니다."));
    }

    @PostMapping
    @PreAuthorize("hasRole('STAFF')")
    @Operation(
            summary = "노선 생성",
            description = "새로운 노선을 생성합니다. STAFF 권한이 필요합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "생성 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "중복된 노선명")
    })
    public ResponseEntity<ApiResponse<RouteDTO>> createRoute(
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal,
            @Parameter(description = "노선 생성 정보", required = true) @RequestBody RouteRequestDTO requestDTO) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 라우트를 생성할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String organizationId = (String) userInfo.get("organizationId");

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 라우트를 생성할 수 없습니다.");
        }

        log.info("라우트 생성 요청 - 라우트 이름: {}, 조직 ID: {}", requestDTO.getRouteName(), organizationId);
        RouteDTO createdRoute = routeService.createRoute(principal, requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(createdRoute, "라우트가 성공적으로 생성되었습니다."));
    }

    @PutMapping
    @PreAuthorize("hasRole('STAFF')")
    @Operation(
            summary = "노선 수정",
            description = "기존 노선의 정보를 수정합니다. STAFF 권한이 필요합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "노선을 찾을 수 없음")
    })
    public ResponseEntity<ApiResponse<RouteDTO>> updateRoute(
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal,
            @Parameter(description = "노선 수정 정보", required = true) @RequestBody RouteUpdateRequestDTO requestDTO) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 라우트를 수정할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String organizationId = (String) userInfo.get("organizationId");

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 라우트를 수정할 수 없습니다.");
        }

        log.info("라우트 수정 요청 - 이름: {}, 조직 ID: {}", requestDTO.getPrevRouteName(), organizationId);
        RouteDTO updatedRoute = routeService.updateRouteByNameAndOrganizationId(organizationId, requestDTO);
        return ResponseEntity.ok(new ApiResponse<>(updatedRoute, "라우트가 성공적으로 수정되었습니다."));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('STAFF')")
    @Operation(
            summary = "노선 삭제",
            description = "노선을 삭제합니다. STAFF 권한이 필요합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "노선을 찾을 수 없음")
    })
    public ResponseEntity<ApiResponse<Void>> deleteRoute(
            @Parameter(description = "노선 ID", required = true) @PathVariable String id,
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 라우트를 삭제할 수 있습니다.");
        }

        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String organizationId = (String) userInfo.get("organizationId");

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 라우트를 삭제할 수 없습니다.");
        }

        log.info("라우트 삭제 요청 - ID: {}, 조직 ID: {}", id, organizationId);
        routeService.deleteRoute(id, principal);
        return ResponseEntity.ok(new ApiResponse<>(null, "라우트가 성공적으로 삭제되었습니다."));
    }
}