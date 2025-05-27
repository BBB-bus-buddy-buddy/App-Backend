package capston2024.bustracker.controller;

import capston2024.bustracker.config.dto.ApiResponse;
import capston2024.bustracker.config.dto.LicenseVerifyRequestDto;
import capston2024.bustracker.exception.BusinessException;
import capston2024.bustracker.service.DriverService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/driver")
@Slf4j
@Tag(name = "운전자 관리", description = "버스 운전자 관련 API - 운전면허 검증 등")
public class DriverController {

    private final DriverService driverService;
    private final ObjectMapper objectMapper;

    @PostMapping("/verify")
    @Operation(
            summary = "운전면허 진위확인",
            description = "운전면허증의 진위를 확인합니다. 외부 API를 통해 실제 면허 정보를 검증합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "진위확인 성공",
                    content = @Content(schema = @Schema(implementation = Map.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "진위확인 실패 또는 잘못된 요청"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 오류")
    })
    public ResponseEntity<ApiResponse<Map<String, String>>> verifyDriverLicense(
            @Parameter(description = "운전면허 검증 정보", required = true)
            @Valid @RequestBody LicenseVerifyRequestDto requestDto) {

        log.info("운전면허 진위확인 요청 수신 - 사용자: {}", requestDto.getLoginUserName());

        try {
            logRequestSafely(requestDto);

            Map<String, String> verificationResult = driverService.verifyLicense(requestDto);

            logResponseSafely(verificationResult);

            String authenticity = verificationResult.get("resAuthenticity");
            String resultMessage = buildResultMessage(authenticity, verificationResult);

            if (isVerificationSuccessful(authenticity)) {
                log.info("운전면허 진위확인 성공 - 사용자: {}", requestDto.getLoginUserName());
                return ResponseEntity.ok(new ApiResponse<>(verificationResult, resultMessage));
            } else {
                log.warn("운전면허 진위확인 실패 - 사용자: {}, 사유: {}",
                        requestDto.getLoginUserName(), resultMessage);
                return ResponseEntity.badRequest()
                        .body(new ApiResponse<>(verificationResult, resultMessage));
            }

        } catch (BusinessException e) {
            log.error("운전면허 진위확인 비즈니스 오류 - 사용자: {}, 오류: {}",
                    requestDto.getLoginUserName(), e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(null, e.getMessage()));
        } catch (Exception e) {
            log.error("운전면허 진위확인 시스템 오류 - 사용자: {}, 오류: {}",
                    requestDto.getLoginUserName(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(null, "운전면허 진위확인 처리 중 오류가 발생했습니다."));
        }
    }

    @GetMapping("/organization/default")
    @Operation(
            summary = "기본 조직 정보 조회",
            description = "운전면허 검증 시 사용할 기본 조직 정보를 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    })
    public ResponseEntity<ApiResponse<Map<String, String>>> getDefaultOrganization() {
        Map<String, String> organizationInfo = Map.of(
                "organization", driverService.getDefaultOrganization(),
                "loginType", driverService.getDefaultLoginType()
        );

        return ResponseEntity.ok(new ApiResponse<>(organizationInfo, "기본 조직 정보 조회 성공"));
    }

    /**
     * 요청 데이터 안전하게 로깅 (민감한 정보 마스킹)
     */
    private void logRequestSafely(LicenseVerifyRequestDto dto) {
        try {
            LicenseVerifyRequestDto maskedDto = createMaskedDto(dto);
            log.info("요청 DTO: {}", objectMapper.writeValueAsString(maskedDto));
        } catch (Exception e) {
            log.warn("요청 DTO 로깅 실패: {}", e.getMessage());
        }
    }

    /**
     * 응답 데이터 안전하게 로깅
     */
    private void logResponseSafely(Map<String, String> response) {
        try {
            log.info("진위확인 응답 - 결과: {}, 설명: {}",
                    response.get("resAuthenticity"),
                    response.get("resAuthenticityDesc1"));
        } catch (Exception e) {
            log.warn("응답 로깅 실패: {}", e.getMessage());
        }
    }

    /**
     * 민감한 정보를 마스킹한 DTO 생성
     */
    private LicenseVerifyRequestDto createMaskedDto(LicenseVerifyRequestDto original) {
        LicenseVerifyRequestDto masked = new LicenseVerifyRequestDto();
        masked.setLoginUserName(original.getLoginUserName());
        masked.setIdentity(maskSensitiveInfo(original.getIdentity()));
        masked.setPhoneNo(maskPhoneNumber(original.getPhoneNo()));
        masked.setBirthDate(original.getBirthDate());
        masked.setLicenseNo01(original.getLicenseNo01());
        masked.setLicenseNo02("****");
        masked.setLicenseNo03("****");
        masked.setLicenseNo04(original.getLicenseNo04());
        return masked;
    }

    /**
     * 민감한 정보 마스킹
     */
    private String maskSensitiveInfo(String info) {
        if (info == null || info.length() < 4) {
            return "****";
        }
        return info.substring(0, 2) + "****" + info.substring(info.length() - 2);
    }

    /**
     * 전화번호 마스킹
     */
    private String maskPhoneNumber(String phoneNo) {
        if (phoneNo == null || phoneNo.length() < 4) {
            return "****";
        }
        return phoneNo.substring(0, 3) + "****" + phoneNo.substring(phoneNo.length() - 4);
    }

    /**
     * 검증 성공 여부 확인
     */
    private boolean isVerificationSuccessful(String authenticity) {
        return "1".equals(authenticity) || "2".equals(authenticity);
    }

    /**
     * 결과 메시지 생성
     */
    private String buildResultMessage(String authenticity, Map<String, String> response) {
        if (isVerificationSuccessful(authenticity)) {
            return "운전면허 진위확인에 성공했습니다: " +
                    response.getOrDefault("resAuthenticityDesc1", "진위확인 완료");
        } else {
            if (response.containsKey("continue2Way") && "true".equals(response.get("continue2Way"))) {
                return "운전면허 진위확인을 위해 추가 인증이 필요합니다.";
            } else {
                return "운전면허 진위확인에 실패했습니다: " +
                        response.getOrDefault("resAuthenticityDesc1", "진위확인 실패");
            }
        }
    }

    /**
     * 예외 처리
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        log.error("비즈니스 예외 발생: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(null, ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(Exception ex) {
        log.error("예기치 않은 오류 발생", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(null, "서버 오류가 발생했습니다."));
    }
}