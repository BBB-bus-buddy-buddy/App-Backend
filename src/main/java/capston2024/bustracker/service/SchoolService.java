package capston2024.bustracker.service;

import capston2024.bustracker.config.ApiKeyConfig;
import capston2024.bustracker.domain.School;
import capston2024.bustracker.domain.auth.SchoolIdGenerator;
import capston2024.bustracker.exception.BusinessException;
import capston2024.bustracker.exception.DuplicateResourceException;
import capston2024.bustracker.exception.ResourceNotFoundException;
import capston2024.bustracker.repository.SchoolRepository;
import com.univcert.api.UnivCert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class SchoolService {
    private static final String UNIV_API_KEY = ApiKeyConfig.getUnivApiKey();
    private AuthService authService;
    private SchoolRepository schoolRepository;

    @Transactional
    public boolean createSchool(String schoolName, OAuth2User principal) {
        authService.validateAdmin(principal);
        if (schoolRepository.existsByName(schoolName)) {
            throw new DuplicateResourceException("해당 학교는 이미 등록되어 있습니다.");
        }
        School school = School.builder()
                .id(SchoolIdGenerator.generateSchoolId(schoolName))
                .name(schoolName)
                .build();
        schoolRepository.save(school);
        log.info("School created: {}", schoolName);
        return true;
    }

    public List<String> getAllSchools() {
        List<School> schools = schoolRepository.findAll();
        return schools.stream().map(School::getName).collect(Collectors.toList());
    }

    @Transactional
    public boolean deleteSchool(String schoolName, OAuth2User principal) {
        authService.validateAdmin(principal);
        School school = schoolRepository.findByName(schoolName)
                .orElseThrow(() -> new ResourceNotFoundException("해당 학교는 존재하지 않습니다."));
        schoolRepository.delete(school);
        log.info("School deleted: {}", schoolName);
        return true;
    }

    public boolean authenticate(String schoolEmail, String schoolName, int code) {
        try {
            UnivCert.certifyCode(UNIV_API_KEY, schoolEmail, schoolName, code);
        } catch (IOException e){
            throw new ResourceNotFoundException("학교 이메일과 학교 이름을 찾을 수 없습니다.");
        }
        return true;
    }

    public boolean sendToEmail(String schoolEmail, String schoolName) {
        try {
            UnivCert.certify(UNIV_API_KEY, schoolEmail, schoolName, true);
        } catch (IOException e){
            throw new ResourceNotFoundException("학교 이메일과 학교 이름을 찾을 수 없습니다.");
        }
        return true;
    }

    public boolean checkBySchoolName(String schoolName){
        try {
            UnivCert.check(schoolName);
        } catch (IOException e){
            throw new ResourceNotFoundException("학교 이메일과 학교 이름을 찾을 수 없습니다.");
        }
        return true;
    }
}
