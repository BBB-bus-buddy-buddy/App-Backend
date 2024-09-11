package capston2024.bustracker.service;

import capston2024.bustracker.config.auth.dto.GoogleInfoDto;
import capston2024.bustracker.config.auth.dto.OAuthAttributes;
import capston2024.bustracker.domain.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class LoginService {
    @Autowired
    private UserFindService userFindService;
    @Autowired
    private UserRegisterService userRegisterService;

    @Autowired
    private JwtService jwtService;

    public Map<String, String> processUserLogin(OAuthAttributes oAuthAttributes){
        User user = getOrCreateUser(oAuthAttributes);
        Map<String, String> stringStringMap = generateAuthTokens(user);
        log.info("accessToken, RefreshToken : {}", stringStringMap);
        return stringStringMap;
    }

    private User getOrCreateUser(OAuthAttributes oAuthAttributes){
        return userFindService.findUserByEmail(oAuthAttributes.getEmail())
                .orElseGet(()-> userRegisterService.registerUser(oAuthAttributes));
    }

    private Map<String, String> generateAuthTokens(User user){
        Map<String, String> tokens = new HashMap<>();
        tokens.put("accessToken", jwtService.generateAccessToken(user));
        tokens.put("refreshToken", jwtService.generateRefreshToken(user));
        return tokens;
    }
}
