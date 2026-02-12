package underphase_info.Scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import underphase_info.KT_Cloring.Service.KT_SubsidyService;
import underphase_info.LG_Cloring.Service.LG_SubsidyService;
import underphase_info.SKT_Cloring.Service.SKT_SubsidyService;

/**
 * 통신 3사 크롤러 오케스트레이터
 * 평일 오전 10시에 SKT → KT → LG U+ 순서로 순차 실행합니다.
 * 하나의 크롤러가 완료(또는 실패)된 후 다음 크롤러가 시작됩니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrawlerScheduler {

    private final SKT_SubsidyService sktSubsidyService;
    private final KT_SubsidyService ktSubsidyService;
    private final LG_SubsidyService lgSubsidyService;

    @Scheduled(cron = "0 0 10 * * MON-FRI")
    public void crawlAllCarriers() {
        log.info("========================================");
        log.info(">>> [통신 3사 크롤링 시작] 순차 실행을 시작합니다.");
        log.info("========================================");

        long totalStart = System.currentTimeMillis();

        // 1. SKT 크롤링
        try {
            log.info("\n>>> [1/3] SKT 크롤링 시작");
            long start = System.currentTimeMillis();
            sktSubsidyService.crawlSktSubsidies();
            log.info(">>> [1/3] SKT 크롤링 완료 (소요시간: {}초)", (System.currentTimeMillis() - start) / 1000);
        } catch (Exception e) {
            log.error(">>> [1/3] SKT 크롤링 실패: {}", e.getMessage());
        }

        // 2. KT 크롤링
        try {
            log.info("\n>>> [2/3] KT 크롤링 시작");
            long start = System.currentTimeMillis();
            ktSubsidyService.crawlKtSubsidies();
            log.info(">>> [2/3] KT 크롤링 완료 (소요시간: {}초)", (System.currentTimeMillis() - start) / 1000);
        } catch (Exception e) {
            log.error(">>> [2/3] KT 크롤링 실패: {}", e.getMessage());
        }

        // 3. LG U+ 크롤링
        try {
            log.info("\n>>> [3/3] LG U+ 크롤링 시작");
            long start = System.currentTimeMillis();
            lgSubsidyService.crawlLguSubsidies();
            log.info(">>> [3/3] LG U+ 크롤링 완료 (소요시간: {}초)", (System.currentTimeMillis() - start) / 1000);
        } catch (Exception e) {
            log.error(">>> [3/3] LG U+ 크롤링 실패: {}", e.getMessage());
        }

        long totalElapsed = (System.currentTimeMillis() - totalStart) / 1000;
        log.info("\n========================================");
        log.info(">>> [통신 3사 크롤링 완료] 총 소요시간: {}분 {}초", totalElapsed / 60, totalElapsed % 60);
        log.info("========================================");
    }
}
