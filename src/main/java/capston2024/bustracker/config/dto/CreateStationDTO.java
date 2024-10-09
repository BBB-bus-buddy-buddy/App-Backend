package capston2024.bustracker.config.dto;

import lombok.Getter;
import lombok.Setter;

import java.awt.*;

@Getter @Setter
public class CreateStationDTO {
    String name;
    Point coordinate;
}
