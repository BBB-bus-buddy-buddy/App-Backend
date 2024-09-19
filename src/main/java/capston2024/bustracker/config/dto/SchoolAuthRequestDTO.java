package capston2024.bustracker.config.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class SchoolAuthRequestDTO {
    private String schoolEmail;
    private String schoolName;
    private int code;
}
