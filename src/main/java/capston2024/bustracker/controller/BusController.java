package capston2024.bustracker.controller;

import capston2024.bustracker.config.dto.BusDTO;
import capston2024.bustracker.config.dto.BusRegisterDTO;
import capston2024.bustracker.domain.Bus;
import capston2024.bustracker.service.BusService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bus")
@RequiredArgsConstructor
public class BusController {

    private final BusService busService;

    // 1. 버스 추가 (POST)
    @PostMapping("/")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> createBus(@RequestBody BusRegisterDTO busRegisterDTO) {
        String result = busService.createBus(busRegisterDTO);
        if(result.equals(busRegisterDTO.getBusNumber() + "번 버스가 성공적으로 등록되었습니다.")) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }

    // 2. 버스 삭제 (DELETE)
    @DeleteMapping("/{busNumber}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> deleteBus(@PathVariable String busNumber) {
        String result = busService.removeBus(busNumber);
        if(result.equals(busNumber + "번 버스가 성공적으로 삭제되었습니다.")) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }

    // 3. 버스 수정 (PUT)
    @PutMapping("/")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> updateBus(@RequestBody BusDTO busDTO) {
        String result = busService.modifyBus(busDTO);
        if(result.equals(busDTO.getBusNumber() + "번 버스가 성공적으로 수정되었습니다.")) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }

    // 4. 버스 조회 (GET)
    @GetMapping("/")
    public ResponseEntity<List<Bus>> getAllBuses() {
        List<Bus> buses = busService.getAllBuses();
        return ResponseEntity.ok(buses);
    }

    // 특정 버스 조회 (GET)
    @GetMapping("/{busNumber}")
    public ResponseEntity<Bus> getBusByNumber(@PathVariable String busNumber) {
        Bus bus = busService.getBusByNumber(busNumber);
        if (bus != null) {
            return ResponseEntity.ok(bus);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
