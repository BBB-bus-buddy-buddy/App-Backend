package capston2024.bustracker.controller;

import capston2024.bustracker.config.dto.ApiResponse;
import capston2024.bustracker.config.dto.InsightQuestionRequestDTO;
import capston2024.bustracker.config.dto.InsightQuestionResponseDTO;
import capston2024.bustracker.config.dto.NetworkInsightResponseDTO;
import capston2024.bustracker.config.dto.StationStatsResponseDTO;
import capston2024.bustracker.service.StationInsightService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/insight")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Insight", description = "정류장 혼잡/AI 분석 API")
@SecurityRequirement(name = "Bearer Authentication")
public class InsightController {

    private final StationInsightService stationInsightService;

    @GetMapping("/stations/{stationId}/analysis")
    @Operation(summary = "정류장 혼잡 분석", description = "특정 정류장의 최근 혼잡도, 피크 시간대, 이용률을 반환합니다.")
    public ResponseEntity<ApiResponse<StationStatsResponseDTO>> analyzeStation(
            @Parameter(description = "정류장 ID") @PathVariable String stationId,
            @Parameter(description = "분석 기간(일)", example = "7") @RequestParam(defaultValue = "7") int days) {
        StationStatsResponseDTO stats = stationInsightService.analyzeStation(stationId, days);
        return ResponseEntity.ok(new ApiResponse<>(stats, "정류장 분석 결과"));
    }

    @GetMapping("/network/analysis")
    @Operation(summary = "네트워크 전체 혼잡 분석", description = "최근 기간 동안 가장 혼잡한 정류장, 증설 권장 정류장 등을 제공합니다.")
    public ResponseEntity<ApiResponse<NetworkInsightResponseDTO>> analyzeNetwork(
            @Parameter(description = "분석 기간(일)", example = "7") @RequestParam(defaultValue = "7") int days) {
        NetworkInsightResponseDTO stats = stationInsightService.analyzeNetwork(days);
        return ResponseEntity.ok(new ApiResponse<>(stats, "네트워크 분석 결과"));
    }

    @PostMapping("/ask")
    @Operation(summary = "AI 질의응답용 데이터 제공",
            description = "자연어 질문을 입력하면 관련 정류장/네트워크 통계를 함께 반환합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "질의 처리 성공",
                    content = @Content(schema = @Schema(implementation = InsightQuestionResponseDTO.class)))
    })
    public ResponseEntity<ApiResponse<InsightQuestionResponseDTO>> askInsight(
            @RequestBody InsightQuestionRequestDTO requestDTO) {
        InsightQuestionResponseDTO response = stationInsightService.answerQuestion(requestDTO);
        return ResponseEntity.ok(new ApiResponse<>(response, "인사이트 생성 완료"));
    }
}
