package capston2024.bustracker.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
@Slf4j
@Tag(name = "앱 다운로드", description = "모바일 앱 다운로드 안내 페이지를 제공하는 API")
public class AppDownloadController {

    @GetMapping("/app-download/user")
    @Operation(
            summary = "사용자 앱 다운로드 안내 페이지",
            description = "일반 사용자용 모바일 앱 다운로드 안내 페이지를 반환합니다. Android와 iOS 다운로드 링크가 포함됩니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "페이지 반환 성공")
    })
    public String userAppDownload(@Parameter(hidden = true) Model model) {
        log.info("USER 앱 다운로드 안내 페이지 접근");
        model.addAttribute("appType", "USER");
        model.addAttribute("androidAppUrl", "https://play.google.com/store/apps/details?id=com.busbuddybuddy");
        model.addAttribute("iosAppUrl", "https://apps.apple.com/app/busbuddybuddy/id123456789");
        return "app-download";
    }

    @GetMapping("/app-download/driver")
    @Operation(
            summary = "기사 앱 다운로드 안내 페이지",
            description = "버스 기사용 모바일 앱 다운로드 안내 페이지를 반환합니다. Android와 iOS 다운로드 링크가 포함됩니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "페이지 반환 성공")
    })
    public String driverAppDownload(@Parameter(hidden = true) Model model) {
        log.info("DRIVER 앱 다운로드 안내 페이지 접근");
        model.addAttribute("appType", "DRIVER");
        model.addAttribute("androidAppUrl", "https://play.google.com/store/apps/details?id=com.driver");
        model.addAttribute("iosAppUrl", "https://apps.apple.com/app/driver/id987654321");
        return "app-download";
    }
}