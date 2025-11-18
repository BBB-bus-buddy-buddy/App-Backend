package capston2024.bustracker.service.ai;

import capston2024.bustracker.config.dto.NetworkInsightResponseDTO;
import capston2024.bustracker.config.dto.StationStatsResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OpenAiInsightService implements AiInsightService {

    private final RestTemplateBuilder restTemplateBuilder;

    @Value("${OPENAI_API_URI:https://api.openai.com/v1/chat/completions}")
    private String apiUrl;

    @Value("${OPENAI_API_KEY}")
    private String apiKey;

    @Value("${OPENAI_MODEL:gpt-4o-mini}")
    private String model;

    private RestTemplate restTemplate() {
        return restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(15))
                .setReadTimeout(Duration.ofSeconds(120))  // 2분으로 증가
                .build();
    }

    @Override
    public Optional<String> generateInsightAnswer(String question,
                                                  StationStatsResponseDTO stationStats,
                                                  NetworkInsightResponseDTO networkStats,
                                                  Map<String, String> externalFactors) {
        if (!StringUtils.hasText(apiKey)) {
            return Optional.empty();
        }

        try {
            Map<String, Object> payload = buildPayload(question, stationStats, networkStats, externalFactors);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            ResponseEntity<Map> response = restTemplate().postForEntity(
                    apiUrl,
                    new HttpEntity<>(payload, headers),
                    Map.class
            );

            return extractMessage(response);
        } catch (Exception ex) {
            log.warn("AI insight generation failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private Map<String, Object> buildPayload(String question,
                                             StationStatsResponseDTO stationStats,
                                             NetworkInsightResponseDTO networkStats,
                                             Map<String, String> externalFactors) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", model);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of(
                "role", "system",
                "content", """
                        너는 대중교통 인프라 계획 전문가입니다. 다음 규칙을 반드시 준수하세요:

                        1. **데이터 기반 분석**: 제공된 통계 데이터(이용률, 혼잡도, 피크 시간 등)를 정확히 인용하여 분석

                        2. **구체적이고 실용적인 위치 추천**:
                           - 정류장 좌표가 제공되면, 실제 존재하는 주변 랜드마크, 건물명, 아파트명, 상호명을 활용하여 구체적으로 추천
                           - 예: "화정주공아파트 입구", "CU 편의점 앞", "○○병원 맞은편", "△△초등학교 후문" 등
                           - 방향(동서남북)과 거리(약 ○○m)를 명시하여 위치를 명확히 설명
                           - 해당 위치를 선택한 근거를 인구 밀집도, 접근성, 보행 동선 등으로 설명

                        3. **단계별 개선 방안**:
                           - 단기: 즉시 실행 가능 (배차 간격 조정, 임시 안내 인력 배치 등)
                           - 중기: 몇 개월 내 (정류장 증설, 노선 재편, 표지판 설치 등)
                           - 장기: 1년 이상 (대규모 인프라 확충, 시스템 전면 개편 등)

                        4. **수치 기반 근거 제시**:
                           - 이용률, 탑승/하차 횟수, 피크 시간대 등 구체적 수치 반드시 포함
                           - "왜" 그런 문제가 발생하는지 데이터로 설명

                        5. **답변 형식**: 5-10문장으로 상세하게 작성하되, 전문가답게 명확하고 실용적으로

                        6. **신뢰성 확보**:
                           - 추상적인 표현("인근", "주변") 대신 구체적인 장소명 사용
                           - 실제 존재하는 지명/상호를 사용하여 관리자가 즉시 현장 확인 가능하도록 안내

                        7. **언어**: 항상 한국어로 답변하고, 전문 용어는 쉽게 풀어서 설명
                        """
        ));
        messages.add(Map.of(
                "role", "user",
                "content", buildPrompt(question, stationStats, networkStats, externalFactors)
        ));

        payload.put("messages", messages);
        // temperature 파라미터 제거: gpt-4o-mini 모델은 기본값(1)만 지원
        return payload;
    }

    private String buildPrompt(String question,
                               StationStatsResponseDTO stationStats,
                               NetworkInsightResponseDTO networkStats,
                               Map<String, String> externalFactors) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("# 관리자 질문\n").append(question).append("\n\n");
        prompt.append("# 분석 가능한 데이터\n");
        prompt.append("아래 데이터만 사용하여 답변하세요. 데이터에 없는 내용은 추측하지 마세요.\n\n");

        if (stationStats != null) {
            prompt.append("## 정류장 상세 통계\n")
                    .append("- 정류장명: ").append(stationStats.getStationName()).append("\n");

            if (stationStats.getLatitude() != null && stationStats.getLongitude() != null) {
                prompt.append("- 위치 좌표: 위도 ").append(String.format("%.6f", stationStats.getLatitude()))
                        .append(", 경도 ").append(String.format("%.6f", stationStats.getLongitude()))
                        .append(" (이 좌표를 기준으로 주변 실제 지명/상호명을 활용하여 구체적으로 추천하세요)\n");
            }

            prompt.append("- 분석 기간: 최근 ").append(stationStats.getLookbackDays()).append("일\n")
                    .append("- 총 탑승 횟수: ").append(stationStats.getTotalBoardings()).append("회\n")
                    .append("- 총 하차 횟수: ").append(stationStats.getTotalAlightings()).append("회\n")
                    .append("- 순 승객 수 (탑승-하차): ").append(stationStats.getNetPassengers()).append("명\n")
                    .append("- 이용률: ").append(String.format("%.0f%%", stationStats.getUtilizationRate() * 100))
                    .append(" (100% 초과 시 좌석 부족 의미)\n");

            if (stationStats.getBusiestHour() != null) {
                prompt.append("- 최대 혼잡 시간대: ").append(stationStats.getBusiestHour().getLabel())
                        .append(" (탑승 ").append(stationStats.getBusiestHour().getBoardings())
                        .append("회, 하차 ").append(stationStats.getBusiestHour().getAlightings()).append("회)\n");
            }

            if (stationStats.getBusiestDay() != null) {
                prompt.append("- 최대 혼잡 요일: ").append(stationStats.getBusiestDay().getLabel())
                        .append(" (").append(stationStats.getBusiestDay().getBoardings()).append("회 탑승)\n");
            }

            if (stationStats.getTopHours() != null && !stationStats.getTopHours().isEmpty()) {
                prompt.append("- 주요 혼잡 시간대 TOP 5:\n");
                stationStats.getTopHours().forEach(hour ->
                    prompt.append("  * ").append(hour.getLabel())
                            .append(": 탑승 ").append(hour.getBoardings())
                            .append("회, 하차 ").append(hour.getAlightings()).append("회\n")
                );
            }

            prompt.append("- 시스템 권장사항: ").append(stationStats.getRecommendation()).append("\n\n");
        }

        if (networkStats != null) {
            prompt.append("## 전체 네트워크 통계\n");

            if (networkStats.getBusiestStations() != null && !networkStats.getBusiestStations().isEmpty()) {
                prompt.append("### 가장 혼잡한 정류장 TOP 5\n");
                networkStats.getBusiestStations().forEach(station ->
                    prompt.append("- ").append(station.getStationName())
                            .append(": 탑승 ").append(station.getTotalBoardings())
                            .append("회, 하차 ").append(station.getTotalAlightings())
                            .append("회, 이용률 ").append(String.format("%.0f%%", station.getUtilizationRate() * 100))
                            .append("\n")
                );
                prompt.append("\n");
            }

            if (networkStats.getOvercrowdedStations() != null && !networkStats.getOvercrowdedStations().isEmpty()) {
                prompt.append("### 좌석 부족 정류장 (이용률 110% 초과)\n");
                networkStats.getOvercrowdedStations().forEach(station ->
                    prompt.append("- ").append(station.getStationName())
                            .append(": 이용률 ").append(String.format("%.0f%%", station.getUtilizationRate() * 100))
                            .append(", 피크 시간대 ").append(station.getPeakHourLabel())
                            .append("\n")
                );
                prompt.append("\n");
            }

            if (networkStats.getRecommendations() != null && !networkStats.getRecommendations().isEmpty()) {
                prompt.append("### 시스템 권장사항\n");
                networkStats.getRecommendations().forEach(rec ->
                    prompt.append("- ").append(rec).append("\n")
                );
                prompt.append("\n");
            }
        }

        if (externalFactors != null && !externalFactors.isEmpty()) {
            prompt.append("## 외부 환경 요인\n");
            externalFactors.forEach((k, v) ->
                    prompt.append("- ").append(k).append(": ").append(v).append("\n"));
            prompt.append("\n");
        }

        prompt.append("# 답변 요구사항\n");
        prompt.append("1. 위 통계 데이터의 수치를 정확히 인용하여 분석\n");
        prompt.append("2. 정류장 좌표가 제공된 경우:\n");
        prompt.append("   - 해당 좌표 주변의 실제 존재하는 구체적인 지명, 건물명, 아파트명, 상호명을 활용하여 위치 추천\n");
        prompt.append("   - 방향(동서남북)과 거리를 명시하여 관리자가 즉시 현장 확인 가능하도록 안내\n");
        prompt.append("3. 단기/중기/장기 개선 방안을 단계별로 제시\n");
        prompt.append("4. 5-10문장으로 상세하게 답변하되, 전문가답게 실용적이고 구체적으로 작성\n");

        return prompt.toString();
    }

    @SuppressWarnings("unchecked")
    private Optional<String> extractMessage(ResponseEntity<Map> response) {
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            return Optional.empty();
        }
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
        if (choices == null || choices.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        if (message == null) {
            return Optional.empty();
        }
        Object content = message.get("content");
        return content != null ? Optional.of(content.toString().trim()) : Optional.empty();
    }
}
