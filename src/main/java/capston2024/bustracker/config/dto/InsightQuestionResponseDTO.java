package capston2024.bustracker.config.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InsightQuestionResponseDTO {
    private String question;
    private String answer;
    private Map<String, String> externalFactors;
    private StationStatsResponseDTO stationStats;
    private NetworkInsightResponseDTO networkStats;
}
