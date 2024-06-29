package capston2024.bustracker.controller;

import capston2024.bustracker.domain.BusCoordinateDomain;
import capston2024.bustracker.domain.BusStopCoordinateDomain;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 정류장 좌표 전송
 */
@RestController
public class Coordinate {

    private final ArrayList<BusStopCoordinateDomain> BUS_STOP_MODEL = new ArrayList<>();
    private final ArrayList<BusStopCoordinateDomain> MY_BUS_STOP_MODEL = new ArrayList<>();
    private final ArrayList<BusCoordinateDomain> BUS_MODEL = new ArrayList<>();

    @PostMapping("/api/allBusStopCoordinate") // Post (학교 UID)
    public List<BusStopCoordinateDomain> getBusStopCoordinate(@RequestParam("uid") String uid, Model model){
        //대충 데이터베이스에서 UID로 정류장 콜렉션 정보를 모델로 받아오는 작업(객체반환) - 아래는 예시
        BUS_STOP_MODEL.add(new BusStopCoordinateDomain("test", 1234.241, 241.241));
        //반환 값은 해당 모델
        return BUS_STOP_MODEL;
    }

    @PostMapping("/api/myBusStopCoorinate")
    public List<BusStopCoordinateDomain> getMyBusStopCoordinate(Model model){
        //데이터베이스에서 해당 유저 정보와 일치한 유저의 즐겨찾기 정류장 목록 조회(객체반환)
        return MY_BUS_STOP_MODEL;
    }

    @PostMapping("/api/busCoordinate")
    public List<BusCoordinateDomain> getBusCoordinate(Model model){
        //데이터베이스에서 해당 학교정보의 uid를 가지고 해당 버스의 위치를 전송
        return BUS_MODEL;
    }
}