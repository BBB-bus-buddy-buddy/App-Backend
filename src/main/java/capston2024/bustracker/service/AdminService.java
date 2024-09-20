package capston2024.bustracker.service;

import capston2024.bustracker.domain.School;
import capston2024.bustracker.domain.auth.AdditionalAuthAPI;
import capston2024.bustracker.domain.auth.SchoolIdGenerator;
import capston2024.bustracker.repository.SchoolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminService {
    private final AdditionalAuthAPI additionalAuthAPI;
    private final AuthenticationService authenticationService;
    private final SchoolRepository schoolRepository;

    @Transactional
    public boolean performSchool(String schoolName){
        boolean isValid = additionalAuthAPI.checkBySchoolName(schoolName);
        if(isValid){
            School school =
                    School.builder()
                    .id(SchoolIdGenerator.generateSchoolId(schoolName))
                    .name(schoolName).build();
            schoolRepository.save(school);
            return true;
        }
        return false;
    }

    public boolean performAdmin(OAuth2User principal){
        Map<String,Object> obj = authenticationService.getUserDetails(principal);
        return obj.get("role").equals("ADMIN");
    }

}
