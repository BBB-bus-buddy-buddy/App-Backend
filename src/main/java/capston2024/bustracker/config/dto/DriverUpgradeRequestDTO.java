package capston2024.bustracker.config.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverUpgradeRequestDTO {

    @NotBlank(message = "조직 ID는 필수입니다.")
    private String organizationId;

    // 개인 정보
    @NotBlank(message = "주민등록번호는 필수입니다.")
    private String identity;

    @NotBlank(message = "생년월일은 필수입니다.")
    private String birthDate;

    @NotBlank(message = "전화번호는 필수입니다.")
    private String phoneNumber;

    // 면허 정보
    @NotBlank(message = "운전면허번호는 필수입니다.")
    private String licenseNumber;

    @NotBlank(message = "면허 일련번호는 필수입니다.")
    private String licenseSerial;

    @NotBlank(message = "면허 종류는 필수입니다.")
    private String licenseType;

    @NotBlank(message = "면허 만료일은 필수입니다.")
    private String licenseExpiryDate;
}