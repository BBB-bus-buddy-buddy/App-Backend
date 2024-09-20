package capston2024.bustracker.service;

import capston2024.bustracker.domain.School;
import capston2024.bustracker.domain.auth.SchoolIdGenerator;
import capston2024.bustracker.exception.DuplicateResourceException;
import capston2024.bustracker.exception.ResourceNotFoundException;
import capston2024.bustracker.exception.UnauthorizedException;
import capston2024.bustracker.repository.SchoolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final AuthenticationService authenticationService;
    private final SchoolRepository schoolRepository;

    @Transactional
    public boolean createStation(String schoolName, OAuth2User principal) {
        validateAdmin(principal);
        // Implementation for creating a station
        log.info("해당 학교에 새로운 정류장이 생성되었습니다: {}", schoolName);
        return true;
    }

    @Transactional
    public boolean createBus(String schoolName, OAuth2User principal) {
        validateAdmin(principal);
        // Implementation for creating a bus
        log.info("해당 학교에 버스가 생성되었습니다: {}", schoolName);
        return true;
    }

    @Transactional
    public boolean createSchool(String schoolName, OAuth2User principal) {
        validateAdmin(principal);
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
        validateAdmin(principal);
        School school = schoolRepository.findByName(schoolName)
                .orElseThrow(() -> new ResourceNotFoundException("해당 학교는 존재하지 않습니다."));
        schoolRepository.delete(school);
        log.info("School deleted: {}", schoolName);
        return true;
    }

    private void validateAdmin(OAuth2User principal) {
        if (!isAdmin(principal)) {
            throw new UnauthorizedException("해당 유저에게 권한이 없습니다.");
        }
    }

    private boolean isAdmin(OAuth2User principal) {
        Map<String,Object> obj = authenticationService.getUserDetails(principal);
        return obj.get("role").equals("ADMIN");
    }
}
