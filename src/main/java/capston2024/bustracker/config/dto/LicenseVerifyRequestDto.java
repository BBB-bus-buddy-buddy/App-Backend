package capston2024.bustracker.config.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LicenseVerifyRequestDto {
    // 고정 값은 서비스에서 설정
    private String organization = "0001"; // 0001로 고정
    private String loginType = "5"; // 5로 고정(간편 인증)

    // 사용자 입력값
    private String loginUserName; // 면허 소유자명(userName과 같은 값이지만 API에서 필요로 함)
    private String identity; // 주민등록번호
    private String loginTypeLevel; // 간편인증 수단
    private String phoneNo; // 전화번호
    private String telecom; // 통신사
    private String birthDate; // 생년월일
    private String licenseNo01;
    private String licenseNo02;
    private String licenseNo03;
    private String licenseNo04;
    private String serialNo; // 면허 일련번호
    private String userName; // 면허 소유자명
}