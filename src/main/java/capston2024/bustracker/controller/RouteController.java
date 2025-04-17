package capston2024.bustracker.controller;

import capston2024.bustracker.config.dto.ApiResponse;
import capston2024.bustracker.config.dto.RouteDTO;
import capston2024.bustracker.config.dto.RouteRequestDTO;
import capston2024.bustracker.exception.BusinessException;
import capston2024.bustracker.exception.ResourceNotFoundException;
import capston2024.bustracker.exception.UnauthorizedException;
import capston2024.bustracker.service.AuthService;
import capston2024.bustracker.service.RouteService;
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
public class RouteController {

    private final RouteService routeService;
    private final AuthService authService;

    /**
     * 조직 ID로 라우트 조회 (검색 기능 포함)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<RouteDTO>>> getRoutesByOrganization(
            @AuthenticationPrincipal OAuth2User principal,
            @RequestParam(required = false) String name) {

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

    /**
     * 특정 라우트 상세 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RouteDTO>> getRouteById(
            @PathVariable String id,
            @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 라우트를 조회할 수 있습니다.");
        }

        //OrganizationId 추출
        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String organizationId = (String) userInfo.get("organizationId");

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 라우트를 조회할 수 없습니다.");
        }

        log.info("라우트 상세 조회 요청 - ID: {}, 조직 ID: {}", id, organizationId);
        RouteDTO route = routeService.getRouteById(id, organizationId);
        return ResponseEntity.ok(new ApiResponse<>(route, "라우트 조회 결과입니다."));
    }

    /**
     * 새로운 라우트 생성
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<RouteDTO>> createRoute(
            @AuthenticationPrincipal OAuth2User principal,
            @RequestBody RouteRequestDTO requestDTO) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 라우트를 생성할 수 있습니다.");
        }

        //Organization 추출
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

    /**
     * 라우트 수정
     */
    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<RouteDTO>> updateRoute(
            @AuthenticationPrincipal OAuth2User principal,
            @RequestBody RouteRequestDTO requestDTO) {

        if (principal == null) {
            throw new UnauthorizedException("인증된 사용자만 라우트를 수정할 수 있습니다.");
        }

        // Organization 추출
        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String organizationId = (String) userInfo.get("organizationId");

        if (organizationId == null || organizationId.isEmpty()) {
            throw new BusinessException("조직에 속하지 않은 사용자는 라우트를 수정할 수 없습니다.");
        }

        log.info("라우트 수정 요청 - 이름: {}, 조직 ID: {}", requestDTO.getRouteName(), organizationId);
        RouteDTO updatedRoute = routeService.updateRouteByNameAndOrganizationId(requestDTO.getRouteName(), organizationId, requestDTO);
        return ResponseEntity.ok(new ApiResponse<>(updatedRoute, "라우트가 성공적으로 수정되었습니다."));
    }

    /**
     * 라우트 삭제
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteRoute(
            @PathVariable String id,
            @AuthenticationPrincipal OAuth2User principal) {

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

    /**
     * 비즈니스 로직 예외 처리
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        log.error("비즈니스 예외 발생: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(null, ex.getMessage()));
    }

    /**
     * 리소스 찾을 수 없음 예외 처리
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFoundException(ResourceNotFoundException ex) {
        log.error("리소스 찾을 수 없음: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(null, ex.getMessage()));
    }

    /**
     * 인증 예외 처리
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorizedException(UnauthorizedException ex) {
        log.error("인증 예외 발생: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(null, ex.getMessage()));
    }
}