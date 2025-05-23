package capston2024.bustracker.controller;

import capston2024.bustracker.config.dto.ApiResponse;
import capston2024.bustracker.config.dto.LicenseVerifyRequestDto;
import capston2024.bustracker.exception.BusinessException;
import capston2024.bustracker.service.DriverService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/driver")
@Slf4j
public class DriverController {

    private final DriverService driverService;
    private final ObjectMapper objectMapper;

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<Map<String, String>>> verifyLicense(@RequestBody LicenseVerifyRequestDto dto) {
        log.info("운전면허 진위확인 요청 수신");

        try {
            // 요청 DTO 로깅
            try {
                log.info("요청 DTO: {}", objectMapper.writeValueAsString(dto));
            } catch (Exception e) {
                log.warn("요청 DTO 로깅 실패: {}", e.getMessage());
            }

            Map<String, String> response = driverService.verifyLicense(dto);

            // 응답 로깅
            try {
                log.info("진위확인 응답: {}", objectMapper.writeValueAsString(response));
            } catch (Exception e) {
                log.warn("응답 로깅 실패: {}", e.getMessage());
            }

            // 진위확인 결과 처리 - API 가이드에 따라 "1" 또는 "2"가 성공
            String authenticity = response.get("resAuthenticity");
            String message;

            if ("1".equals(authenticity) || "2".equals(authenticity)) {
                message = "운전면허 진위확인에 성공했습니다: " +
                        (response.containsKey("resAuthenticityDesc1") ?
                                response.get("resAuthenticityDesc1") : "진위확인 완료");
                log.info("진위확인 성공: {}", message);
                return ResponseEntity.ok(new ApiResponse<>(response, message));
            } else {
                // 2단계 인증 필요 등의 특별 메시지 확인
                if (response.containsKey("continue2Way") && "true".equals(response.get("continue2Way"))) {
                    message = "운전면허 진위확인을 위해 추가 인증이 필요합니다.";
                } else {
                    message = "운전면허 진위확인에 실패했습니다: " +
                            (response.containsKey("resAuthenticityDesc1") ?
                                    response.get("resAuthenticityDesc1") : "진위확인 실패");
                }

                log.warn("진위확인 실패: {}", message);
                return ResponseEntity.badRequest()
                        .body(new ApiResponse<>(response, message));
            }
        } catch (BusinessException e) {
            log.error("운전면허 진위확인 중 비즈니스 오류 발생: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(null, e.getMessage()));
        } catch (Exception e) {
            log.error("운전면허 진위확인 중 예외 발생: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(null, "운전면허 진위확인 처리 중 오류가 발생했습니다."));
        }
    }
}