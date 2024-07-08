package capston2024.bustracker.controller;

import capston2024.bustracker.domain.BusCoordinate;
import capston2024.bustracker.domain.BusStopCoordinate;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 정류장 좌표 전송 (갈아엎는게 좋아보임)
 */
@RestController
public class CoordinateController {

    private final ArrayList<BusStopCoordinate> BUS_STOP_MODEL = new ArrayList<>();
    private final ArrayList<BusStopCoordinate> MY_BUS_STOP_MODEL = new ArrayList<>();
    private final ArrayList<BusCoordinate> BUS_MODEL = new ArrayList<>();

    @PostMapping("/api/allBusStopCoordinate") // Post (학교 UID)
    public List<BusStopCoordinate> getBusStopCoordinate(@RequestParam("uid") String uid, Model model){
        //대충 데이터베이스에서 UID로 정류장 콜렉션 정보를 모델로 받아오는 작업(객체반환) - 아래는 예시
        BUS_STOP_MODEL.add(new BusStopCoordinate("test", 1234.241, 241.241));
        //반환 값은 해당 모델
        return BUS_STOP_MODEL;
    }

    @PostMapping("/api/myBusStopCoorinate")
    public List<BusStopCoordinate> getMyBusStopCoordinate(Model model){
        //데이터베이스에서 해당 유저 정보와 일치한 유저의 즐겨찾기 정류장 목록 조회(객체반환)
        return MY_BUS_STOP_MODEL;
    }

    @PostMapping("/api/busCoordinate")
    public List<BusCoordinate> getBusCoordinate(Model model){
        //데이터베이스에서 해당 학교정보의 uid를 가지고 해당 버스의 위치를 전송
        return BUS_MODEL;
    }
}