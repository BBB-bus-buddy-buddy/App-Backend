package capston2024.bustracker.service.ai;

import capston2024.bustracker.config.dto.NetworkInsightResponseDTO;
import capston2024.bustracker.config.dto.StationStatsResponseDTO;

import java.util.Map;
import java.util.Optional;

public interface  AiInsightService {
    Optional<String> generateInsightAnswer(String question,
                                           StationStatsResponseDTO stationStats,
                                           NetworkInsightResponseDTO networkStats,
                                           Map<String, String> externalFactors);
}
