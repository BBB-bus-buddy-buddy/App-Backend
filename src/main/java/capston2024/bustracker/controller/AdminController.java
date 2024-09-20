package capston2024.bustracker.controller;

import capston2024.bustracker.config.dto.CreateDTO;
import capston2024.bustracker.service.AdminService;
import com.univcert.api.UnivCert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 관리자 페이지 관련 컨트롤러 ( Role : Admin )
 */
@RestController
@RequestMapping("/admin")
public class AdminController {
    @Autowired
    AdminService adminService;
    @PostMapping("/station")
    public ResponseEntity<?> createStationBySchoolName(@RequestBody CreateDTO request, @AuthenticationPrincipal OAuth2User principal){
        return ResponseEntity.ok(Map.of("isCreate", request.getSchoolName()));
    }

    @PostMapping("/bus")
    public ResponseEntity<?> createBusBySchoolName(@RequestBody CreateDTO request, @AuthenticationPrincipal OAuth2User principal){
        return ResponseEntity.ok(Map.of("isCreate", request.getSchoolName()));
    }

    @PostMapping("/school")
    public ResponseEntity<?> createSchoolBySchoolName(@RequestBody CreateDTO request, @AuthenticationPrincipal OAuth2User principal){
        //관리자가 검증되고, 학교도 유효한 학교 이름일 경우 isValid = true;
        //performSchool로 인해 학교 생성됨
        boolean isAdmin = adminService.performAdmin(principal);
        boolean isValid = isAdmin && adminService.performSchool(request.getSchoolName());
        return ResponseEntity.ok(Map.of("isCreate", isValid));
    }


}
