package capston2024.bustracker.controller;

import capston2024.bustracker.config.dto.BusRegisterRequestDTO;
import capston2024.bustracker.service.BusService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bus")
@RequiredArgsConstructor
public class BusController {

    private final BusService busService;

    @PostMapping("/create")
    public ResponseEntity<String> createBus(@RequestBody BusRegisterRequestDTO busRegisterRequestDTO) {
        String result = busService.createBus(busRegisterRequestDTO);
        if(result.equals(busRegisterRequestDTO.getBusNumber() + "번 버스가 성공적으로 등록되었습니다.")) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }
}
