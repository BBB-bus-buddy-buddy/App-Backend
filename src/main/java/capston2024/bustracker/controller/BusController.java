package capston2024.bustracker.controller;

import capston2024.bustracker.config.dto.*;
import capston2024.bustracker.domain.Bus;
import capston2024.bustracker.exception.BusinessException;
import capston2024.bustracker.exception.ResourceNotFoundException;
import capston2024.bustracker.exception.UnauthorizedException;
import capston2024.bustracker.service.BusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.A;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bus")
@RequiredArgsConstructor
@Slf4j
public class BusController {

    private final BusService busService;

    // 1. 버스 추가 (POST)
    @PostMapping("")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Boolean>> createBus(@RequestBody BusRegisterDTO busRegisterDTO) {
        try {
            boolean result = busService.createBus(busRegisterDTO);
            if (result) {
                return ResponseEntity.ok(new ApiResponse<>(true, "성공적으로 버스가 추가되었습니다."));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ApiResponse<>(false, "버스 추가에 실패했습니다."));
            }
        } catch (IllegalArgumentException e) {
            log.error("버스 추가 중 잘못된 입력값: {}", e.getMessage());
            throw e;
        } catch (BusinessException e) {
            log.error("버스 추가 중 비즈니스 로직 오류: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("버스 추가 중 예기치 않은 오류 발생: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "서버 오류가 발생했습니다."));
        }
    }

    // 2. 버스 삭제 (DELETE)
    @DeleteMapping("/{busNumber}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Boolean>> deleteBus(@PathVariable String busNumber) {
        try {
            boolean result = busService.removeBus(busNumber);
            if (result) {
                return ResponseEntity.ok(new ApiResponse<>(true, "성공적으로 버스가 삭제되었습니다."));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ApiResponse<>(false, "버스 삭제에 실패했습니다."));
            }
        } catch (ResourceNotFoundException e) {
            log.error("버스 삭제 중 리소스를 찾을 수 없음: {}", e.getMessage());
            throw e;
        } catch (BusinessException e) {
            log.error("버스 삭제 중 비즈니스 로직 오류: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("버스 삭제 중 예기치 않은 오류 발생: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, "서버 오류가 발생했습니다."));
        }
    }

    // 4. 버스 조회 (GET)
    @GetMapping("")
    public ResponseEntity<ApiResponse<List<Bus>>> getAllBuses() {
        try {
            List<Bus> buses = busService.getAllBuses();
            return ResponseEntity.ok(new ApiResponse<>(buses, "모든 버스가 성공적으로 조회되었습니다."));
        } catch (BusinessException e) {
            log.error("버스 목록 조회 중 비즈니스 로직 오류: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("버스 목록 조회 중 예기치 않은 오류 발생: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(null, "서버 오류가 발생했습니다."));
        }
    }

    @GetMapping("/stations/{stationId}")
    public ResponseEntity<ApiResponse<List<Bus>>> getBusesByStationId(@PathVariable String stationId) {
        try {
            List<Bus> buses = busService.getBusesByStationId(stationId);
            return ResponseEntity.ok(new ApiResponse<>(buses, "버스 목록이 성공적으로 조회되었습니다."));
        } catch (ResourceNotFoundException e) {
            log.error("정류장 ID로 버스 조회 중 리소스를 찾을 수 없음: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("정류장 ID로 버스 조회 중 예기치 않은 오류 발생: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(null, "서버 오류가 발생했습니다."));
        }
    }

    @GetMapping("/stationNames/{busNumber}")
    public ResponseEntity<ApiResponse<List<String>>> getStationsByBusNumber(@PathVariable String busNumber) {
        try {
            List<String> stationList = busService.getAllStationNames(busNumber);
            return ResponseEntity.ok(new ApiResponse<>(stationList, "해당 버스의 정류장 목록이 성공적으로 조회되었습니다."));
        } catch (ResourceNotFoundException e) {
            log.error("버스 번호로 정류장 목록 조회 중 리소스를 찾을 수 없음: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("버스 번호로 정류장 목록 조회 중 예기치 않은 오류 발생: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(null, "서버 오류가 발생했습니다."));
        }
    }

    @GetMapping("/{busNumber}")
    public ResponseEntity<ApiResponse<Bus>> getBusByNumber(@PathVariable String busNumber) {
        try {
            Bus bus = busService.getBusByNumber(busNumber);
            return ResponseEntity.ok(new ApiResponse<>(bus, "버스가 성공적으로 조회되었습니다."));
        } catch (ResourceNotFoundException e) {
            log.error("버스 번호로 버스 조회 중 리소스를 찾을 수 없음: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("버스 번호로 버스 조회 중 예기치 않은 오류 발생: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(null, "서버 오류가 발생했습니다."));
        }
    }

    @GetMapping("/seats/{busNumber}")
    public ResponseEntity<ApiResponse<BusSeatDTO>> getBusSeatsByBusNumber(@PathVariable String busNumber) {
        try {
            BusSeatDTO seats = busService.getBusSeatsByBusNumber(busNumber);
            return ResponseEntity.ok(new ApiResponse<>(seats, "좌석이 성공적으로 반환되었습니다."));
        } catch (ResourceNotFoundException e) {
            log.error("버스 번호로 좌석 정보 조회 중 리소스를 찾을 수 없음: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("버스 번호로 좌석 정보 조회 중 예기치 않은 오류 발생: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(null, "서버 오류가 발생했습니다."));
        }
    }

    @GetMapping("/location/{busNumber}")
    public ResponseEntity<ApiResponse<LocationDTO>> getBusLocationByBusNumber(@PathVariable String busNumber) {
        try {
            LocationDTO locations = busService.getBusLocationByBusNumber(busNumber);
            return ResponseEntity.ok(new ApiResponse<>(locations, "좌표 정보가 성공적으로 반환되었습니다."));
        } catch (ResourceNotFoundException e) {
            log.error("버스 번호로 위치 정보 조회 중 리소스를 찾을 수 없음: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("버스 번호로 위치 정보 조회 중 예기치 않은 오류 발생: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(null, "서버 오류가 발생했습니다."));
        }
    }

    // ExceptionHandler 부분은 동일하게 유지
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequestException(UnauthorizedException ex) {
        log.error("비지니스 서비스 로직 예외: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(null, ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.error("CSV 형식 에러: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(null, ex.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFoundException(ResourceNotFoundException ex) {
        log.error("존재하지 않는 리소스: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(null, ex.getMessage()));
    }
}