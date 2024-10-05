package capston2024.bustracker.controller;

import capston2024.bustracker.config.dto.MyStationRequestDTO;
import capston2024.bustracker.domain.Station;
import capston2024.bustracker.service.UserService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@AllArgsConstructor
@RestController
@RequestMapping("/api/user")
@Slf4j
public class UserController {

    private final UserService userService;

    // 내 정류장 조회
    @GetMapping("my-station/read")
    public ResponseEntity<List<Station>> getMyStationList(@RequestParam String userId) {
        log.info("{}님의 내 정류장 정보를 불러들입니다.", userId);
        List<Station> myStationList = userService.getMyStationList(userId);

        if(myStationList.isEmpty()) {
            log.warn("{}님의 내 정류장이 없습니다.", userId);
            return ResponseEntity.status(HttpStatus.NO_CONTENT).body(myStationList);
        }

        log.info("{}님의 내 정류장이 조회되었습니다.", userId);
        return ResponseEntity.ok(myStationList);
    }


    // 내 정류장 추가
    @PostMapping("/my-station/add")
    public ResponseEntity<String> addMyStation(@RequestBody MyStationRequestDTO request) {
        log.info("정류장 {}을 사용자 {}의 내 정류장 목록에 추가 요청", request.getStationId(), request.getUserId());
        boolean isSuccess = userService.addMyStation(request.getUserId(), request.getStationId());
        if (isSuccess)
            return ResponseEntity.ok("내 정류장에 성공적으로 추가되었습니다.");
        else
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("내 정류장 추가에 실패했습니다.");
    }

    // 내 정류장 삭제
    @DeleteMapping("my-station/delete")
    public ResponseEntity<String> deleteMyStation(@RequestBody MyStationRequestDTO request) {
        log.info("정류장 {}을 사용자 {}의 내 정류장 목록에서 삭제 요청", request.getStationId(), request.getUserId());
        boolean isSuccess = userService.deleteMyStation(request.getUserId(), request.getStationId());
        if(isSuccess)
            return ResponseEntity.ok("내 정류장이 성공적으로 삭제되었습니다.");
        else
            return ResponseEntity.badRequest().body("내 정류장 삭제에 실패하였습니다.");
    }

}
