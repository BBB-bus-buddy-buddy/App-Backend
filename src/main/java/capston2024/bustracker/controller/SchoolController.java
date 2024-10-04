package capston2024.bustracker.controller;

import capston2024.bustracker.config.dto.SchoolRegisterDTO;
import capston2024.bustracker.config.dto.ApiResponse;
import capston2024.bustracker.exception.DuplicateResourceException;
import capston2024.bustracker.exception.UnauthorizedException;
import capston2024.bustracker.exception.ResourceNotFoundException;
import capston2024.bustracker.service.SchoolService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Validated
public class SchoolController {

    private final SchoolService schoolService;

    @PostMapping("/school")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Boolean>> createSchool(@Valid @RequestBody SchoolRegisterDTO request,
                                                             @AuthenticationPrincipal OAuth2User principal) {
        log.info("Creating school: {}", request.getSchoolName());
        boolean created = schoolService.createSchool(request.getSchoolName(), principal);
        return ResponseEntity.ok(new ApiResponse<>(created, "학교를 성공적으로 생성을 완료하였습니다."));
    }

    @GetMapping("/school")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<String>>> getAllSchools() {
        log.info("Retrieving all schools");
        List<String> schools = schoolService.getAllSchools();
        return ResponseEntity.ok(new ApiResponse<>(schools, "모든 학교 정보를 성공적으로 반환하였습니다."));
    }

    @DeleteMapping("/school/{schoolName}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Boolean>> deleteSchool(@PathVariable String schoolName,
                                                             @AuthenticationPrincipal OAuth2User principal) {
        log.info("Deleting school: {}", schoolName);
        boolean deleted = schoolService.deleteSchool(schoolName, principal);
        return ResponseEntity.ok(new ApiResponse<>(deleted, "해당 학교를 성공적으로 삭제하였습니다."));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorizedException(UnauthorizedException ex) {
        log.error("인가되지 않은 리소스: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(null, ex.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFoundException(ResourceNotFoundException ex) {
        log.error("찾을 수 없는 리소스: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(null, ex.getMessage()));
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateResourceException(DuplicateResourceException ex) {
        log.error("중복된 리소스: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(null, ex.getMessage()));
    }
}