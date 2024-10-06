package capston2024.bustracker.service;

import capston2024.bustracker.domain.School;
import capston2024.bustracker.domain.auth.SchoolIdGenerator;
import capston2024.bustracker.exception.DuplicateResourceException;
import capston2024.bustracker.exception.ResourceNotFoundException;
import capston2024.bustracker.exception.UnauthorizedException;
import capston2024.bustracker.repository.SchoolRepository;
import com.univcert.api.UnivCert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class SchoolService {
    @Value("${UNIV_API_KEY}")
    private static String UNIV_API_KEY;

    private final SchoolRepository schoolRepository;

    @Transactional
    public boolean createSchool(String schoolName) {
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

    public School getSchool(String schoolName){
        return schoolRepository.findByName(schoolName).orElseThrow(()->new ResourceNotFoundException("해당 학교는 등록되지 않았습니다"));
    }

    @Transactional
    public boolean deleteSchool(String schoolName) {
        School school = schoolRepository.findByName(schoolName)
                .orElseThrow(() -> new ResourceNotFoundException("해당 학교는 존재하지 않습니다."));
        schoolRepository.delete(school);
        log.info("School deleted: {}", schoolName);
        return true;
    }



    public Map<String, Object> authenticate(String schoolEmail, String schoolName, int code) {
        try {
            return UnivCert.certifyCode(UNIV_API_KEY, schoolEmail, schoolName, code);
        } catch (IOException e){
            throw new ResourceNotFoundException("학교 이메일과 학교 이름을 찾을 수 없습니다.");
        }
    }

    public Map<String, Object> sendToEmail(String schoolEmail, String schoolName) {
        try {
            return UnivCert.certify(UNIV_API_KEY, schoolEmail, schoolName, true);
        } catch (IOException e){
            throw new ResourceNotFoundException("학교 이메일과 학교 이름을 찾을 수 없습니다.");
        }
    }

    public boolean checkBySchoolName(String schoolName){
        try {
            School school = schoolRepository.findByName(schoolName).orElseThrow(()-> new ResourceNotFoundException("해당 학교는 등록되지 않았습니다."));
            return UnivCert.check(school.getName()).get("success").equals(true);
        } catch (IOException e){
            throw new UnauthorizedException("학교를 찾을 수 없거나 인증할 수 없는 학교입니다.");
        }
    }
}
