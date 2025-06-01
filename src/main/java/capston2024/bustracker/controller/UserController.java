package capston2024.bustracker.controller;

import capston2024.bustracker.config.dto.ApiResponse;
import capston2024.bustracker.config.dto.UserFavoriteStationRequestDTO;
import capston2024.bustracker.domain.Station;
import capston2024.bustracker.domain.User;
import capston2024.bustracker.exception.BusinessException;
import capston2024.bustracker.exception.UnauthorizedException;
import capston2024.bustracker.service.AuthService;
import capston2024.bustracker.service.UserService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// Swagger 어노테이션 추가
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@AllArgsConstructor
@RestController
@RequestMapping("/api/user")
@Slf4j
@Tag(name = "User", description = "사용자 관리 관련 API")
public class UserController {

    private final UserService userService;
    private final AuthService authService;

    /**
     * 조직별 모든 사용자 조회 (관리자 전용)
     */
    @GetMapping()
    @PreAuthorize("hasRole('STAFF')")
    @Operation(summary = "조직별 사용자 목록 조회",
            description = "현재 사용자 조직의 모든 사용자를 조회합니다. 관리자 권한이 필요합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "사용자 목록 조회 성공",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음 (관리자 권한 필요)")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<ApiResponse<List<User>>> getUsersByOrganization(
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) {
            log.warn("인증된 사용자를 찾을 수 없음");
            throw new UnauthorizedException("인증된 사용자를 찾을 수 없습니다");
        }

        // 관리자 권한 검증
        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String userOrganizationId = (String) userInfo.get("organizationId");
        String role = (String) userInfo.get("role");

        // 관리자만 접근 가능하며, 자신의 조직 데이터만 조회 가능
        if (!"STAFF".equals(role)) {
            throw new UnauthorizedException("관리자 권한이 필요합니다");
        }

        List<User> users = userService.getUsersByOrganizationId(userOrganizationId);

        return ResponseEntity.ok(new ApiResponse<>(users, "조직의 모든 사용자가 성공적으로 조회되었습니다."));
    }

    /**
     * 조직의 특정 사용자 조회 (관리자 전용)
     */
    @GetMapping("/{userId}")
    @PreAuthorize("hasRole('STAFF')")
    @Operation(summary = "특정 사용자 조회",
            description = "조직의 특정 사용자 정보를 조회합니다. 관리자 권한이 필요합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "사용자 정보 조회 성공",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음 (관리자 권한 필요)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<ApiResponse<User>> getUserByIdAndOrganizationId(
            @Parameter(description = "조회할 사용자 ID") @PathVariable String userId,
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) {
            log.warn("인증된 사용자를 찾을 수 없음");
            throw new UnauthorizedException("인증된 사용자를 찾을 수 없습니다");
        }

        // 관리자 권한 검증
        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String userOrganizationId = (String) userInfo.get("organizationId");
        String role = (String) userInfo.get("role");

        // 관리자만 접근 가능하며, 자신의 조직 데이터만 조회 가능
        if (!"STAFF".equals(role)) {
            throw new UnauthorizedException("관리자 권한이 필요합니다");
        }

        User user = userService.getUserByIdAndOrganizationId(userOrganizationId, userId);

        return ResponseEntity.ok(new ApiResponse<>(user, "조직의 모든 사용자가 성공적으로 조회되었습니다."));
    }

    /**
     * 조직의 특정 사용자 삭제 (관리자 전용)
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('STAFF')")
    @Operation(summary = "사용자 삭제",
            description = "조직의 특정 사용자를 삭제합니다. 관리자 권한이 필요합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "사용자 삭제 성공",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음 (관리자 권한 필요)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<ApiResponse<Boolean>> deleteUserByIdAndOrganizationId(
            @Parameter(description = "삭제할 사용자 ID") @PathVariable String id,
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) {
            log.warn("인증된 사용자를 찾을 수 없음");
            throw new UnauthorizedException("인증된 사용자를 찾을 수 없습니다");
        }

        // 관리자 권한 검증
        Map<String, Object> userInfo = authService.getUserDetails(principal);
        String userOrganizationId = (String) userInfo.get("organizationId");
        String role = (String) userInfo.get("role");

        // 관리자만 접근 가능하며, 자신의 조직 데이터만 조회 가능
        if (!"STAFF".equals(role)) {
            throw new UnauthorizedException("관리자 권한이 필요합니다");
        }
        userService.deleteUserById(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "해당 사용자가 성공적으로 삭제되었습니다."));
    }

    /**
     * 내 정류장 목록 조회
     */
    @GetMapping("/my-station")
    @Operation(summary = "내 정류장 목록 조회",
            description = "현재 사용자가 즐겨찾기로 등록한 정류장 목록을 조회합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "내 정류장 목록 조회 성공",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증되지 않은 사용자")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<ApiResponse<List<Station>>> getMyStationList(
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal) {

        log.info("유저의 principal : {} ", principal);
        if (principal == null) {
            log.warn("No authenticated user found");
            throw new UnauthorizedException("인증된 사용자를 찾을 수 없습니다");
        }
        Map<String, Object> obj = authService.getUserDetails(principal);
        String email = (String)obj.get("email");
        List<Station> myStationList = userService.getMyStationList(email);

        log.info("{}님의 내 정류장이 조회되었습니다.", email);
        return ResponseEntity.ok(new ApiResponse<>(myStationList, "내 정류장 조회가 성공적으로 완료되었습니다."));
    }

    /**
     * 내 정류장 추가
     */
    @PostMapping("/my-station")
    @Operation(summary = "내 정류장 추가",
            description = "현재 사용자의 즐겨찾기 정류장 목록에 새로운 정류장을 추가합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "내 정류장 추가 성공",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "내 정류장 추가 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "정류장을 찾을 수 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 등록된 정류장")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<ApiResponse<Boolean>> addMyStation(
            @Parameter(description = "즐겨찾기 정류장 추가 요청 데이터") @RequestBody UserFavoriteStationRequestDTO request,
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal) {

        log.info("유저의 principal : {} ", principal);
        if (principal == null) {
            log.warn("인증된 사용자를 찾을 수 없음");
            throw new UnauthorizedException("인증된 사용자를 찾을 수 없습니다");
        }
        Map<String, Object> obj = authService.getUserDetails(principal);
        String email = (String)obj.get("email");

        log.info("정류장 {}을 사용자 {}의 내 정류장 목록에 추가 요청", request.getStationId(), email);
        boolean isSuccess = userService.addMyStation(email, request.getStationId());
        if (isSuccess)
            return ResponseEntity.ok(new ApiResponse<>(true, "내 정류장에 성공적으로 추가되었습니다."));
        else
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse<>(false, "내 정류장 추가에 실패했습니다."));
    }

    /**
     * 내 정류장 삭제
     */
    @DeleteMapping("/my-station/{stationId}")
    @Operation(summary = "내 정류장 삭제",
            description = "현재 사용자의 즐겨찾기 정류장 목록에서 특정 정류장을 삭제합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "내 정류장 삭제 성공",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "내 정류장 삭제 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "정류장을 찾을 수 없음")
    })
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<ApiResponse<Boolean>> deleteMyStation(
            @Parameter(description = "삭제할 정류장 ID") @PathVariable String stationId,
            @Parameter(hidden = true) @AuthenticationPrincipal OAuth2User principal) {

        log.info("유저의 principal : {} ", principal);
        if (principal == null) {
            log.warn("No authenticated user found");
            throw new UnauthorizedException("인증된 사용자를 찾을 수 없습니다");
        }
        String email = principal.getAttribute("email");

        log.info("정류장 {}을 사용자 {}의 내 정류장 목록에서 삭제 요청", stationId, email);
        boolean isSuccess = userService.deleteMyStation(email, stationId);
        if(isSuccess)
            return ResponseEntity.ok(new ApiResponse<>(true, "내 정류장이 성공적으로 삭제되었습니다."));
        else
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, "내 정류장 삭제에 실패하였습니다."));
    }

    /**
     * 비즈니스 예외 처리
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        log.error("비즈니스 예외 발생: {}", ex.getMessage());

        HttpStatus status = switch (ex.getErrorCode()) {
            case USER_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case DUPLICATE_ENTITY -> HttpStatus.CONFLICT;
            case ACCESS_DENIED -> HttpStatus.FORBIDDEN;
            case INVALID_TOKEN, TOKEN_EXPIRED -> HttpStatus.UNAUTHORIZED;
            case ENTITY_NOT_FOUND -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.BAD_REQUEST;
        };

        return ResponseEntity.status(status)
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

    /**
     * 일반 예외 처리
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(Exception ex) {
        log.error("예기치 않은 오류 발생", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(null, "서버 오류가 발생했습니다."));
    }
}