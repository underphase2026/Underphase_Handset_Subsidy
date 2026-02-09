package underphase_info.LG_Cloring.Controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import underphase_info.LG_Cloring.Service.LG_SubsidyService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/lg")
public class LG_SubsidyController {

    private final LG_SubsidyService lgSubsidyService;

    @GetMapping("/crawl")
    public ResponseEntity<String> triggerLguCrawl() {
        // 비동기 실행으로 브라우저 타임아웃 방지
        new Thread(() -> lgSubsidyService.crawlLguSubsidies()).start();
        return ResponseEntity.ok("LG U+ 크롤링이 시작되었습니다. 로그를 확인하세요!");
    }
}
