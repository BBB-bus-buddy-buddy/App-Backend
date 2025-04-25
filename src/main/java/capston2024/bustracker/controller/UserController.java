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

@AllArgsConstructor
@RestController
@RequestMapping("/api/user")
@Slf4j
public class UserController {

    private final UserService userService;
    private final AuthService authService;


    /**
     * 조직별 모든 사용자 조회 (관리자 전용)
     */
    @GetMapping()
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<ApiResponse<List<User>>> getUsersByOrganization(
            @AuthenticationPrincipal OAuth2User principal) {

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
    public ResponseEntity<ApiResponse<User>> getUserByIdAndOrganizationId(
            @PathVariable String id,
            @AuthenticationPrincipal OAuth2User principal) {

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

        User user = userService.getUserByIdAndOrganizationId(userOrganizationId, id);

        return ResponseEntity.ok(new ApiResponse<>(user, "조직의 모든 사용자가 성공적으로 조회되었습니다."));
    }

    /**
     * 조직의 특정 사용자 삭제 (관리자 전용)
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<ApiResponse<Boolean>> deleteUserByIdAndOrganizationId(
            @PathVariable String id,
            @AuthenticationPrincipal OAuth2User principal) {

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
        return ResponseEntity.ok(new ApiResponse<>(true, "해당 사용자가 성공작으로 삭제되었습니다."));
    }

    // 내 정류장 조회
    @GetMapping("/my-station")
    public ResponseEntity<ApiResponse<List<Station>>> getMyStationList(@AuthenticationPrincipal OAuth2User principal) {
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
     * @param request - stationId
     * @param principal
     * @return
     */
    // 내 정류장 추가
    @PostMapping("/my-station")
    public ResponseEntity<ApiResponse<Boolean>> addMyStation(@RequestBody UserFavoriteStationRequestDTO request, @AuthenticationPrincipal OAuth2User principal) {
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

    // 내 정류장 삭제
    @DeleteMapping("/my-station/{stationId}")
    public ResponseEntity<ApiResponse<Boolean>> deleteMyStation(@PathVariable String stationId, @AuthenticationPrincipal OAuth2User principal) {
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

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        log.error("비즈니스 예외 발생: {}", ex.getMessage());

        HttpStatus status = switch (ex.getErrorCode()) {
            case USER_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case DUPLICATE_ENTITY -> HttpStatus.CONFLICT;
            case ACCESS_DENIED -> HttpStatus.FORBIDDEN;
            case INVALID_TOKEN, TOKEN_EXPIRED -> HttpStatus.UNAUTHORIZED;
            default -> HttpStatus.BAD_REQUEST;
        };

        return ResponseEntity.status(status)
                .body(new ApiResponse<>(null, ex.getMessage()));
    }
}
