package underphase_info.ICT_Market_Cloring.Controller;

import underphase_info.ICT_Market_Cloring.Service.IctMarketService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ict")
@RequiredArgsConstructor
public class IctMarketController {
    private final IctMarketService ictMarketService;
    @GetMapping("/crawl")
    public String triggerIctCrawling() {
        ictMarketService.crawlCertifiedDealers();
        return "ICT 마켓 크롤링 시작됨 (콘솔 확인)";
    }
}