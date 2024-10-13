package capston2024.bustracker.config.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@Getter @Setter
public class ArrivalTimeRequestDTO {
    private String origin;          // 출발지 (예: 출발지명(선택),127.11015314141542,37.39472714688412)
    private String destination;     // 목적지 (예: 도착지명(선택),127.10824367964793,37.401937080111644)
    private List<String> waypoints; // 경유지 리스트 (선택)
}
