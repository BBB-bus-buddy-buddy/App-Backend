package capston2024.bustracker.config.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LicenseVerifyRequestDto {
    // 고정 값은 서비스에서 설정
    private String organization;
    private String loginType;

    // 사용자 입력값
    private String loginUserName;
    private String identity;
    private String loginTypeLevel;
    private String phoneNo;
    private String telecom;
    private String birthDate;
    private String licenseNo01;
    private String licenseNo02;
    private String licenseNo03;
    private String licenseNo04;
    private String serialNo;
    private String userName;
}