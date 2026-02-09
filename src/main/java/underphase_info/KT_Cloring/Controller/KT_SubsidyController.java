package underphase_info.KT_Cloring.Controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import underphase_info.KT_Cloring.Service.KT_SubsidyService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/kt")
public class KT_SubsidyController {

    private final KT_SubsidyService ktSubsidyService;

    @GetMapping("/crawl")
    public ResponseEntity<String> triggerKtCrawl() {
        // 비동기 실행으로 브라우저 타임아웃 방지
        new Thread(() -> ktSubsidyService.crawlKtSubsidies()).start();
        return ResponseEntity.ok("KT 크롤링이 시작되었습니다. 로그를 확인하세요!");
    }
}
