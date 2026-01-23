package underphase_info.ICT_Market_Cloring.Controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import underphase_info.ICT_Market_Cloring.Service.IctMarketService;

@RestController
@RequestMapping("/ict")
@RequiredArgsConstructor
public class IctMarketController {

    private final IctMarketService ictMarketService;

    @GetMapping("/crawl")
    public String startCrawl() {
        ictMarketService.crawlCertifiedDealers();
        return "크롤링이 시작되었습니다. 서버 콘솔을 확인하세요.";
    }
}