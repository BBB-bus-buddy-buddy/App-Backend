package capston2024.bustracker.controller;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 계정 정보 유효성 검사
 */
@RestController
public class AuthVerify {
    @PostMapping("/api/authVerify")
    public boolean getAuthVerify(Model model){
        //대충 모델과 정보 일치 유무를 판단해서 일치하면 true/false를 판단하는 로직
        return true;
    }

    @PostMapping("/api/authRegister")
    public boolean setRegisterAuthInfo(Model model){
        //유저 데이터를 Model측으로 보내주는 로직
        //Model측에서 객체를 데이터베이스로 처리
        return true;
    }

    @PostMapping("/api/authInfo")
    public Object getAuthInfo(Model model){
        return "계정정보";
    }
}
