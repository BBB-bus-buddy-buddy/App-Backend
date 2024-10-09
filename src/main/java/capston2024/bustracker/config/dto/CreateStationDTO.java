package capston2024.bustracker.config.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class CreateStationDTO {
    private String name;
    private Double latitude;
    private Double longitude;
}
