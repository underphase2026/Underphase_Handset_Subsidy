package underphase_info.LG_Cloring.Controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import underphase_info.LG_Cloring.Service.LG_SubsidyService;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/crawl")
public class LG_SubsidyController {

    private final LG_SubsidyService lgSubsidyService;

    @GetMapping("/lgu")
    public ResponseEntity<String> triggerLguCrawl() {
        log.info(">>> [수동 실행] LG U+ 공시지원금 크롤링을 시작합니다.");

        try {
            lgSubsidyService.crawlLguSubsidies();
            return ResponseEntity.ok("LG U+ 크롤링이 성공적으로 완료되었습니다. DB를 확인하세요!");
        } catch (Exception e) {
            log.error("크롤링 중 오류 발생: ", e);
            return ResponseEntity.internalServerError().body("크롤링 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
}