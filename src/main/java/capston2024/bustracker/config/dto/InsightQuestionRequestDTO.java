package capston2024.bustracker.config.dto;

import lombok.Data;

import java.util.Map;

@Data
public class InsightQuestionRequestDTO {
    private String question;
    private String stationId;
    private Integer lookbackDays;
    private Map<String, String> externalFactors;
}
