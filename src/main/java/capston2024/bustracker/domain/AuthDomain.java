package capston2024.bustracker.domain;

import java.util.ArrayList;

public class AuthDomain {
    private Long id; // 데이터베이스 자동생성 id
    private String userId; // 실제 id
    private String pw; // 실제 비번
    private String name; // 이름
    private String schoolCode; // 인증된 학교 코드
    private boolean isValid; // 검증상태 여부

    public boolean isValid() {
        return isValid;
    }

    public String getSchoolCode() {
        return schoolCode;
    }

    public void setSchoolCode(String schoolCode) {
        this.schoolCode = schoolCode;
    }

    public void setValid(boolean valid) {
        isValid = valid;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPw() {
        return pw;
    }

    public void setPw(String pw) {
        this.pw = pw;
    }
}
