package capston2024.bustracker.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * ** 웹 MVC의 컨트롤러 역할 **
 * 계정 정보 유효성 검사
 */
@Controller //@Controller + @ResponseBody
//@RequestMapping(value = "/login/google", produces = "application/json")
public class AuthController {
    @GetMapping("/loginForm")
    public String home() {
        return "loginForm";
    }

    @GetMapping("/private")
    public String privatePage() {
        return "privatePage";
    }

//    @PostMapping("/api/authVerify")
//    public boolean getAuthVerify(@RequestParam("id") String id, @RequestParam("pw") String pw, Model model){
//        //대충 모델과 정보 일치 유무를 판단해서 일치하면 true/false를 판단하는 로직
//        return true;
//    }
//
//    @PostMapping("/api/authRegister")
//    public boolean setRegisterAuthInfo(Model model){
//        //유저 데이터를 Model측으로 보내주는 로직
//        //Model측에서 객체를 데이터베이스로 처리
//        return true;
//    }

//    @PostMapping("/api/authInfo")
//    public Auth getAuthInfo(Model model){
//        return new Auth();
//    }
}