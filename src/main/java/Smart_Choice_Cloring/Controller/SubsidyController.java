package Smart_Choice_Cloring.Controller;

import Smart_Choice_Cloring.Service.SubsidyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/subsidy")
@RequiredArgsConstructor
public class SubsidyController {

    private final SubsidyService subsidyService;

    @GetMapping("/crawl")
    public String triggerCrawling() {
        subsidyService.crawlAndSaveSubsidies();
        return "크롤링 및 데이터 저장이 완료되었습니다.";
    }
}
