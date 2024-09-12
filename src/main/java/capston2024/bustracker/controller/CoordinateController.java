package capston2024.bustracker.controller;

import capston2024.bustracker.domain.BusCoordinate;
import capston2024.bustracker.domain.BusStopCoordinate;
import capston2024.bustracker.service.CoordinateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 정류장 좌표 전송 (갈아엎는게 좋아보임)
 */
@RestController
@RequestMapping("/api")
public class CoordinateController {

    private final CoordinateService coordinateService;

    public CoordinateController(CoordinateService coordinateService) {
        this.coordinateService = coordinateService;
    }

    @PostMapping("/api/allBusStopCoordinate") // Post (학교 UID)
    public ResponseEntity<List<BusStopCoordinate>> getBusStopCoordinate(@RequestParam("uid") String uid){
        List<BusStopCoordinate> busStop = coordinateService.getAllBusStops(uid);
        if(busStop.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(busStop);
    }

    @PostMapping("/api/myBusStopCoorinate")
    public ResponseEntity<List<BusStopCoordinate>> getMyBusStopCoordinate(@RequestParam("userId") String userId){
        //데이터베이스에서 해당 유저 정보와 일치한 유저의 즐겨찾기 정류장 목록 조회(객체반환)
        List<BusStopCoordinate> myBusStops = coordinateService.getUserBusStops(userId);
        if(myBusStops.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(myBusStops);
    }

    @PostMapping("/api/busCoordinate")
    public ResponseEntity<List<BusCoordinate>> getBusCoordinate(@RequestParam("uid") String uid){
        //데이터베이스에서 해당 학교정보의 uid를 가지고 해당 버스의 위치를 전송
        List<BusCoordinate> busCoordinates = coordinateService.getBusCoordinates(uid);
        if(busCoordinates.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(busCoordinates);
    }
}