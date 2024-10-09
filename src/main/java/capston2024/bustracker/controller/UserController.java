package capston2024.bustracker.controller;

import capston2024.bustracker.config.dto.ApiResponse;
import capston2024.bustracker.config.dto.MyStationRequestDTO;
import capston2024.bustracker.domain.Station;
import capston2024.bustracker.exception.UnauthorizedException;
import capston2024.bustracker.service.AuthService;
import capston2024.bustracker.service.UserService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    // 내 정류장 조회
    @GetMapping("my-station")
    public ResponseEntity<ApiResponse<List<Station>>> getMyStationList(@AuthenticationPrincipal OAuth2User principal) {
        log.info("유저의 principal : {} ", principal);
        if (principal == null) {
            log.warn("No authenticated user found");
            throw new UnauthorizedException("인증된 사용자를 찾을 수 없습니다");
        }
        Map<String, Object> obj = authService.getUserDetails(principal);
        String email = (String)obj.get("email");
        List<Station> myStationList = userService.getMyStationList(email);

        if(myStationList.isEmpty()) {
            log.warn("{}님의 내 정류장이 없습니다.", email);
            return ResponseEntity.status(HttpStatus.NO_CONTENT).body(new ApiResponse<>(null, "내 정류장 목록이 비어있습니다."));
        }

        log.info("{}님의 내 정류장이 조회되었습니다.", email);
        return ResponseEntity.ok(new ApiResponse<>(myStationList, "내 정류장 조회가 성공적으로 완료되었습니다."));
    }



    // 내 정류장 추가
    @PostMapping("/my-station")
    public ResponseEntity<ApiResponse<Boolean>> addMyStation(@RequestBody MyStationRequestDTO request, @AuthenticationPrincipal OAuth2User principal) {
        log.info("유저의 principal : {} ", principal);
        if (principal == null) {
            log.warn("No authenticated user found");
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
    @DeleteMapping("my-station")
    public ResponseEntity<ApiResponse<Boolean>> deleteMyStation(@RequestBody MyStationRequestDTO request, @AuthenticationPrincipal OAuth2User principal) {
        log.info("유저의 principal : {} ", principal);
        if (principal == null) {
            log.warn("No authenticated user found");
            throw new UnauthorizedException("인증된 사용자를 찾을 수 없습니다");
        }
        Map<String, Object> obj = authService.getUserDetails(principal);
        String email = (String)obj.get("email");

        log.info("정류장 {}을 사용자 {}의 내 정류장 목록에서 삭제 요청", request.getStationId(), email);
        boolean isSuccess = userService.deleteMyStation(email, request.getStationId());
        if(isSuccess)
            return ResponseEntity.ok(new ApiResponse<>(true, "내 정류장이 성공적으로 삭제되었습니다."));
        else
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, "내 정류장 삭제에 실패하였습니다."));
    }

}
