package capston2024.bustracker.config.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class RouteUpdateRequestDTO {
    private String prevRouteName;
    private String newRouteName;
    private List<RouteStationRequestDTO> stations;

    @Getter @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RouteStationRequestDTO {
        private int sequence;
        private String stationId;
    }
}