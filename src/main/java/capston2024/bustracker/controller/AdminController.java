package capston2024.bustracker.controller;

import capston2024.bustracker.config.dto.CreateDTO;
import capston2024.bustracker.config.dto.ApiResponse;
import capston2024.bustracker.service.AdminService;
import capston2024.bustracker.exception.UnauthorizedException;
import capston2024.bustracker.exception.ResourceNotFoundException;
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
public class AdminController {

    private final AdminService adminService;

    @PostMapping("/station")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Boolean>> createStation(@Valid @RequestBody CreateDTO request,
                                                              @AuthenticationPrincipal OAuth2User principal) {
        log.info("Creating station for school: {}", request.getSchoolName());
        boolean created = adminService.createStation(request.getSchoolName(), principal);
        return ResponseEntity.ok(new ApiResponse<>(created, "Station created successfully"));
    }

    @PostMapping("/bus")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Boolean>> createBus(@Valid @RequestBody CreateDTO request,
                                                          @AuthenticationPrincipal OAuth2User principal) {
        log.info("Creating bus for school: {}", request.getSchoolName());
        boolean created = adminService.createBus(request.getSchoolName(), principal);
        return ResponseEntity.ok(new ApiResponse<>(created, "Bus created successfully"));
    }

    @PostMapping("/school")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Boolean>> createSchool(@Valid @RequestBody CreateDTO request,
                                                             @AuthenticationPrincipal OAuth2User principal) {
        log.info("Creating school: {}", request.getSchoolName());
        boolean created = adminService.createSchool(request.getSchoolName(), principal);
        return ResponseEntity.ok(new ApiResponse<>(created, "School created successfully"));
    }

    @GetMapping("/school")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<String>>> getAllSchools() {
        log.info("Retrieving all schools");
        List<String> schools = adminService.getAllSchools();
        return ResponseEntity.ok(new ApiResponse<>(schools, "Schools retrieved successfully"));
    }

    @DeleteMapping("/school")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Boolean>> deleteSchool(@Valid @RequestBody CreateDTO request,
                                                             @AuthenticationPrincipal OAuth2User principal) {
        log.info("Deleting school: {}", request.getSchoolName());
        boolean deleted = adminService.deleteSchool(request.getSchoolName(), principal);
        return ResponseEntity.ok(new ApiResponse<>(deleted, "School deleted successfully"));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorizedException(UnauthorizedException ex) {
        log.error("Unauthorized access attempt: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(null, ex.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFoundException(ResourceNotFoundException ex) {
        log.error("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(null, ex.getMessage()));
    }
}