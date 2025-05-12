package capston2024.bustracker.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
@Slf4j
public class AppDownloadController {

    /**
     * USER 앱 다운로드 안내 페이지
     */
    @GetMapping("/app-download/user")
    public String userAppDownload(Model model) {
        log.info("USER 앱 다운로드 안내 페이지 접근");
        model.addAttribute("appType", "USER");
        model.addAttribute("androidAppUrl", "https://play.google.com/store/apps/details?id=com.busbuddybuddy");
        model.addAttribute("iosAppUrl", "https://apps.apple.com/app/busbuddybuddy/id123456789");
        return "app-download";
    }

    /**
     * DRIVER 앱 다운로드 안내 페이지
     */
    @GetMapping("/app-download/driver")
    public String driverAppDownload(Model model) {
        log.info("DRIVER 앱 다운로드 안내 페이지 접근");
        model.addAttribute("appType", "DRIVER");
        model.addAttribute("androidAppUrl", "https://play.google.com/store/apps/details?id=com.driver");
        model.addAttribute("iosAppUrl", "https://apps.apple.com/app/driver/id987654321");
        return "app-download";
    }
}