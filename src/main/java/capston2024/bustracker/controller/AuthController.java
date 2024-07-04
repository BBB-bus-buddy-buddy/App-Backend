package capston2024.bustracker.controller;

import capston2024.bustracker.domain.Auth;
import capston2024.bustracker.service.LoginService;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/**
 * 계정 정보 유효성 검사
 */
@RestController //@Controller + @ResponseBody
@RequestMapping(value = "/login/oauth2", produces = "application/json")
public class AuthController {
    LoginService loginService;

    public AuthController(LoginService loginService) {
        this.loginService = loginService;
    }

    @GetMapping("/code/{registrationId}")
    public void googleLogin(@RequestParam String code, @PathVariable String registrationId){
        loginService.socialLogin(code, registrationId);
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