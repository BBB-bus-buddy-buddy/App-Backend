package capston2024.bustracker.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 정류장 좌표 전송
 */
@RestController
public class Coordinate {
    @PostMapping("/api/allBusStopCoordinate") // Post (학교 UID)
    public Object getBusStopCoordinate(Model model){
        //대충 데이터베이스에서 정류장 위치 정보를 모델로 받아오는 작업(객체반환)
        model.addAttribute("data", "hello!!");
        //반환 값은 해당 모델
        return "hello";
    }

    @PostMapping("/api/myBusStopCoorinate")
    public Object getMyBusStopCoordinate(Model model){
        //데이터베이스에서 해당 유저 정보와 일치한 유저의 즐겨찾기 정류장 목록 조회(객체반환)
        return "12.214, 124.124";
    }

    @PostMapping("/api/busCoordinate")
    public Object getBusCoordinate(Model model){
        //데이터베이스에서 해당 학교정보의 uid를 가지고 해당 버스의 위치를 전송
        return "123123";
    }
}
