package underphase_info.ICT_Market_Cloring.Service;

import underphase_info.ICT_Market_Cloring.Repository.CertifiedDealerRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class IctMarketService {
    private final CertifiedDealerRepository dealerRepository;
    private final WebDriver driver;

    @Transactional
    public void crawlCertifiedDealers() {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
        JavascriptExecutor js = (JavascriptExecutor) driver;

        // 요청하신 부산, 울산, 경남 지역
        String[] targetRegions = {"경상남도", "울산광역시", "부산광역시"};

        try {
            // DB 초기화
            dealerRepository.deleteAll();
            driver.get("https://ictmarket.or.kr:8443/find/find_01.do");

            for (String region : targetRegions) {
                System.out.println("\n>>> [" + region + "] 지역 조회 시작...");

                // 1. 광역시/도 선택 (SIDO_CD)
                WebElement sidoSelect = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("SIDO_CD")));
                new Select(sidoSelect).selectByValue(region);
                Thread.sleep(800);

                // 2. 검색 버튼 클릭 (onclick="getAgentListNM()" 실행)
                WebElement searchBtn = wait.until(ExpectedConditions.elementToBeClickable(
                        By.xpath("//button[contains(@onclick, 'getAgentListNM()')]")
                ));
                js.executeScript("arguments[0].click();", searchBtn);

                System.out.println(">>> [" + region + "] 검색 버튼 클릭 완료");

                // 3. 결과 테이블 로딩 대기
                Thread.sleep(2500);

                // TODO: 여기에 결과 목록을 긁어오는 parseAndSaveResults(region) 로직이 들어갈 자리입니다.
            }

            System.out.println("\n>>> 지정된 모든 지역 조회 프로세스 완료!");

        } catch (Exception e) {
            System.err.println(">>> ICT 크롤링 중 오류: " + e.getMessage());
            e.printStackTrace();
        }
    }
}