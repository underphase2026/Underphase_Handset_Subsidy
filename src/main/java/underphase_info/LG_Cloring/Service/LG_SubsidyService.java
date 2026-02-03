package underphase_info.LG_Cloring.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import underphase_info.Smart_Choice_Cloring.Entity.Subsidy;
import underphase_info.Smart_Choice_Cloring.Repository.SubsidyRepository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LG_SubsidyService {

    private final SubsidyRepository subsidyRepository;
    private final WebDriver driver;

    private final List<String> targetModels = Arrays.asList(
            "아이폰 16 Pro Max", "아이폰 16", "갤럭시 S24 울트라" // 예시 (현재 출시된 모델 위주 테스트 권장)
    );

    private final List<String> lguPlans = Arrays.asList(
            "5G 시그니처", "5G 프리미엄 슈퍼", "5G 프리미어 플러스", "5G 프리미어 에센셜", "5G 데이터 슈퍼", "5G 라이트 +"
    );

    @Transactional
    public void crawlLguSubsidies() {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        String url = "https://www.lguplus.com/mobile/financing-model#assen";

        try {
            driver.get(url);

            // 1. 신규가입 선택 (라벨 클릭)
            WebElement joinType = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//label[@for='3']")));
            joinType.click();

            // 2. 5G폰 선택 (라벨 클릭)
            driver.findElement(By.xpath("//label[@for='00']")).click();

            List<Subsidy> batchResults = new ArrayList<>();

            for (String modelName : targetModels) {
                // 3. 모델명 검색
                WebElement searchInput = driver.findElement(By.id("cfrmSearch-1-1"));
                searchInput.clear();
                searchInput.sendKeys(modelName);
                searchInput.sendKeys(Keys.ENTER);
                Thread.sleep(1000); // 검색 결과 로딩 대기

                for (String planName : lguPlans) {
                    try {
                        // 4. 요금제 선택 버튼 클릭
                        driver.findElement(By.xpath("//button[contains(text(), '더 많은 요금제 보기')]")).click();

                        // 5. 특정 요금제 찾아 클릭 (요금제 타이틀 텍스트 기준)
                        WebElement planOption = wait.until(ExpectedConditions.elementToBeClickable(
                                By.xpath("//p[@class='contents__title' and text()='" + planName + "']")
                        ));
                        planOption.click();

                        // 6. 적용 버튼 클릭
                        driver.findElement(By.cssSelector(".c-btn-solid-1-m")).click();
                        Thread.sleep(1500); // 결과 반영 대기

                        // 7. 지원금 추출
                        String amountStr = driver.findElement(By.cssSelector("td.cl-black")).getText();

                        batchResults.add(Subsidy.builder()
                                .maker(modelName.contains("아이폰") ? "Apple" : "Samsung")
                                .deviceName(modelName)
                                .telecom("LG U+")
                                .planName(planName)
                                .supportType("신규가입")
                                .supportAmount(amountStr)
                                .build());

                        log.info("LGU+ 수집 완료: {} | {} | {}", modelName, planName, amountStr);
                    } catch (Exception e) {
                        log.warn("LGU+ 요금제({}) 처리 중 오류: {}", planName, e.getMessage());
                    }
                }
            }
            // 8. 일괄 저장 (Batch Insert 효과)
            subsidyRepository.saveAll(batchResults);

        } catch (Exception e) {
            log.error("LGU+ 크롤링 치명적 오류: ", e);
        }
    }
}