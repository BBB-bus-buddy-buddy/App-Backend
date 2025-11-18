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
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(30))
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
                "content", "너는 대중교통 데이터를 분석하는 전문가야. 항상 한국어로 답변하고, 수치와 근거, 권장 조치를 포함해 상세히 설명해."
        ));
        messages.add(Map.of(
                "role", "user",
                "content", buildPrompt(question, stationStats, networkStats, externalFactors)
                        + "\n\n위 데이터를 근거로, 정확한 수치와 시점을 언급하며 한국어로 자세히 답해 주세요."
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
        prompt.append("User question: ").append(question).append("\n\n");

        if (stationStats != null) {
            prompt.append("Station Stats:\n")
                    .append("Name: ").append(stationStats.getStationName()).append("\n")
                    .append("Lookback days: ").append(stationStats.getLookbackDays()).append("\n")
                    .append("Boardings: ").append(stationStats.getTotalBoardings()).append("\n")
                    .append("Alightings: ").append(stationStats.getTotalAlightings()).append("\n")
                    .append("Utilization: ").append(String.format("%.0f%%", stationStats.getUtilizationRate() * 100)).append("\n")
                    .append("Recommendation: ").append(stationStats.getRecommendation()).append("\n");
        }

        if (networkStats != null) {
            prompt.append("\nNetwork Stats:\n")
                    .append("Busiest stations: ").append(networkStats.getBusiestStations()).append("\n")
                    .append("Overcrowded stations: ").append(networkStats.getOvercrowdedStations()).append("\n")
                    .append("Recommendations: ").append(networkStats.getRecommendations()).append("\n");
        }

        if (externalFactors != null && !externalFactors.isEmpty()) {
            prompt.append("\nExternal Factors:\n");
            externalFactors.forEach((k, v) ->
                    prompt.append("- ").append(k).append(": ").append(v).append("\n"));
        }

        prompt.append("\nProvide a short answer (2-3 sentences).");
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
