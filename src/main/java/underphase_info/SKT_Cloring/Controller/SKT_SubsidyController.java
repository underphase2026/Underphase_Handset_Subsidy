package underphase_info.SKT_Cloring.Controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import underphase_info.SKT_Cloring.Service.SKT_SubsidyService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/skt")
public class SKT_SubsidyController {

    private final SKT_SubsidyService sktSubsidyService;

    @GetMapping("/crawl")
    public ResponseEntity<String> triggerSktCrawl() {
        // 비동기 실행으로 브라우저 타임아웃 방지
        new Thread(() -> sktSubsidyService.crawlSktSubsidies()).start();
        return ResponseEntity.ok("SKT 크롤링이 시작되었습니다. 로그를 확인하세요!");
    }
}
