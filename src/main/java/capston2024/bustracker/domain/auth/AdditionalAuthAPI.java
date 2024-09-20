package capston2024.bustracker.domain.auth;

import capston2024.bustracker.config.ApiKeyConfig;
import com.univcert.api.UnivCert;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class AdditionalAuthAPI {
    private static final String UNIV_API_KEY = ApiKeyConfig.getUnivApiKey();

    public boolean authenticate(String schoolEmail, String schoolName, int code) {
        try {
            UnivCert.certifyCode(UNIV_API_KEY, schoolEmail, schoolName, code);
        } catch (IOException e){
            return false;
        }
        return true;
    }

    public boolean sendToEmail(String schoolEmail, String schoolName) {
        try {
            UnivCert.certify(UNIV_API_KEY, schoolEmail, schoolName, true);
        } catch (IOException e){
            return false;
        }
        return true;
    }
}
