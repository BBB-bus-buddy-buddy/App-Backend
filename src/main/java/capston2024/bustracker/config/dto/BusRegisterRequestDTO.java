package capston2024.bustracker.config.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BusRegisterRequestDTO {
    private String busNumber;         // 버스 번호
    private List<String> stationNames; // 버스 노선에 포함될 정류장 이름 리스트
    private BusSeatDTO busSeat;
}
