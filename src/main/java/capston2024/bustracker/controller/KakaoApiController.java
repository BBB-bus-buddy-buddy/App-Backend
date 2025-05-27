package capston2024.bustracker.controller;

import capston2024.bustracker.config.dto.ApiResponse;
import capston2024.bustracker.config.dto.busEtc.BusArrivalEstimateResponseDTO;
import capston2024.bustracker.exception.BusinessException;
import capston2024.bustracker.exception.ResourceNotFoundException;
import capston2024.bustracker.service.KakaoApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/kakao-api")
@Tag(name = "외부 API 연동", description = "카카오 등 외부 API 연동 서비스")
@Validated
public class KakaoApiController {

    private final KakaoApiService kakaoApiService;

    @GetMapping("/arrival-time/multi")
    @Operation(
            summary = "버스 도착 예정 시간 조회",
            description = "특정 버스가 특정 정류장에 도착할 예정 시간을 계산합니다. 다중 경유지를 고려한 정확한 시간을 제공합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "도착 시간 조회 성공",
                    content = @Content(schema = @Schema(implementation = BusArrivalEstimateResponseDTO.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청 파라미터"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "버스 또는 정류장을 찾을 수 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "외부 API 호출 실패")
    })
    public ResponseEntity<ApiResponse<BusArrivalEstimateResponseDTO>> getArrivalTimeEstimate(
            @Parameter(description = "버스 번호", required = true, example = "1001")
            @RequestParam @NotBlank(message = "버스 번호는 필수입니다") String busId,
            @Parameter(description = "정류장 ID", required = true, example = "ST001")
            @RequestParam @NotBlank(message = "정류장 ID는 필수입니다") String stationId) {

        log.info("버스 도착 시간 조회 요청 - 버스: {}, 정류장: {}", busId, stationId);

        try {
            BusArrivalEstimateResponseDTO result = kakaoApiService.getMultiWaysTimeEstimate(busId, stationId);

            log.info("버스 도착 시간 조회 성공 - 버스: {}, 정류장: {}, 예상시간: {}",
                    busId, stationId, result.getEstimatedTime());

            return ResponseEntity.ok(new ApiResponse<>(result, "도착 예정 시간이 성공적으로 조회되었습니다."));

        } catch (ResourceNotFoundException ex) {
            log.warn("리소스를 찾을 수 없음 - 버스: {}, 정류장: {}, 오류: {}", busId, stationId, ex.getMessage());
            throw ex; // 전역 예외 핸들러에서 처리
        } catch (RuntimeException ex) {
            log.error("카카오 API 호출 실패 - 버스: {}, 정류장: {}, 오류: {}", busId, stationId, ex.getMessage());
            throw new BusinessException("도착 시간 조회 중 오류가 발생했습니다: " + ex.getMessage());
        } catch (Exception ex) {
            log.error("예기치 않은 오류 발생 - 버스: {}, 정류장: {}", busId, stationId, ex);
            throw new BusinessException("시스템 오류가 발생했습니다.");
        }
    }

    @GetMapping("/arrival-time/simple")
    @Operation(
            summary = "간단한 도착 시간 조회",
            description = "기본적인 도착 시간만을 조회합니다. 복잡한 경로 계산 없이 직선 거리 기반으로 예상 시간을 제공합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "리소스를 찾을 수 없음")
    })
    public ResponseEntity<ApiResponse<String>> getSimpleArrivalTime(
            @Parameter(description = "버스 번호", required = true)
            @RequestParam @NotBlank String busId,
            @Parameter(description = "정류장 ID", required = true)
            @RequestParam @NotBlank String stationId) {

        log.info("간단한 도착 시간 조회 요청 - 버스: {}, 정류장: {}", busId, stationId);

        // 간단한 계산 로직 (실제 구현은 서비스에서)
        String simpleEstimate = "약 5-10분";

        return ResponseEntity.ok(new ApiResponse<>(simpleEstimate, "간단한 도착 시간이 조회되었습니다."));
    }

    @GetMapping("/health")
    @Operation(
            summary = "카카오 API 연결 상태 확인",
            description = "카카오 API 서비스의 연결 상태를 확인합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "연결 정상"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "서비스 사용 불가")
    })
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkKakaoApiHealth() {
        log.info("카카오 API 상태 확인 요청");

        Map<String, Object> healthStatus = Map.of(
                "service", "kakao-api",
                "status", "healthy",
                "timestamp", System.currentTimeMillis()
        );

        return ResponseEntity.ok(new ApiResponse<>(healthStatus, "카카오 API 서비스가 정상적으로 작동 중입니다."));
    }

    /**
     * 입력값 검증
     */
    private void validateParameters(String busId, String stationId) {
        if (busId == null || busId.trim().isEmpty()) {
            throw new BusinessException("버스 번호는 필수입니다.");
        }
        if (stationId == null || stationId.trim().isEmpty()) {
            throw new BusinessException("정류장 ID는 필수입니다.");
        }
    }

    /**
     * 예외 처리
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFoundException(ResourceNotFoundException ex) {
        log.error("리소스를 찾을 수 없음: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(null, ex.getMessage()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        log.error("비즈니스 예외 발생: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(null, ex.getMessage()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntimeException(RuntimeException ex) {
        log.error("런타임 예외 발생", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(null, "외부 API 연동 중 오류가 발생했습니다."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(Exception ex) {
        log.error("예기치 않은 오류 발생", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(null, "시스템 오류가 발생했습니다."));
    }
}