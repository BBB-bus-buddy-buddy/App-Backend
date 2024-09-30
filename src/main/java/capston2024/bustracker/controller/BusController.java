package capston2024.bustracker.controller;

import capston2024.bustracker.config.dto.BusRegisterRequestDTO;
import capston2024.bustracker.service.BusService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bus")
@RequiredArgsConstructor
public class BusController {

    private final BusService busService;

    // 1. 버스 추가 (POST)
    @PostMapping("/create")
    public ResponseEntity<String> createBus(@RequestBody BusRegisterRequestDTO busRegisterRequestDTO) {
        String result = busService.createBus(busRegisterRequestDTO);
        if(result.equals(busRegisterRequestDTO.getBusNumber() + "번 버스가 성공적으로 등록되었습니다.")) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }

    // 2. 버스 삭제 (DELETE)
    @DeleteMapping("/delete/{busNumber}")
    public ResponseEntity<String> deleteBus(@PathVariable String busNumber) {
        String result = busService.removeBus(busNumber);
        if(result.equals(busNumber + "번 버스가 성공적으로 삭제되었습니다.")) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }

    // 3. 버스 수정 (PUT)
    @PutMapping("/update")
    public ResponseEntity<String> updateBus(@RequestBody BusRegisterRequestDTO busRegisterRequestDTO) {
        String result = busService.modifyBus(busRegisterRequestDTO);
        if(result.equals(busRegisterRequestDTO.getBusNumber() + "번 버스가 성공적으로 수정되었습니다.")) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }

    // 4. 버스 조회 (GET)
    @GetMapping("/list")
    public ResponseEntity<List<BusRegisterRequestDTO>> getAllBuses() {
        List<BusRegisterRequestDTO> buses = busService.getAllBuses();
        return ResponseEntity.ok(buses);
    }

    // 특정 버스 조회 (GET)
    @GetMapping("/get/{busNumber}")
    public ResponseEntity<BusRegisterRequestDTO> getBusByNumber(@PathVariable String busNumber) {
        BusRegisterRequestDTO bus = busService.getBusByNumber(busNumber);
        if (bus != null) {
            return ResponseEntity.ok(bus);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
