package Smart_Choice_Cloring.Service;

import Smart_Choice_Cloring.Entity.Subsidy;
import Smart_Choice_Cloring.Repository.SubsidyRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SubsidyService {
    private final SubsidyRepository subsidyRepository;
    private final WebDriver driver;

    @Transactional
    public void crawlAndSaveSubsidies() {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
        JavascriptExecutor js = (JavascriptExecutor) driver;
        String targetPhone = "[5G] 갤럭시 A17 ZEM폰 포켓피스"; // 검색 대상 기기명 고정

        try {
            // 1. MariaDB 초기화
            subsidyRepository.deleteAll();
            driver.get("https://m.smartchoice.or.kr/smc/mobile/dantongList.do?type=m");

            // 2. 제조사 선택 (정확한 ID: dan_Mau)
            WebElement makerSelect = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("select#dan_Mau")));
            new Select(makerSelect).selectByValue("삼성전자");
            System.out.println(">>> 제조사 선택 완료");

            // 3. 휴대폰 선택 팝업 오픈
            WebElement phoneBtn = wait.until(ExpectedConditions.elementToBeClickable(By.id("product_btn")));
            phoneBtn.click();

            // 4. 팝업 내에서 기기 클릭
            WebElement phoneItem = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//span[contains(text(), '" + targetPhone + "')]")
            ));
            js.executeScript("arguments[0].click();", phoneItem);
            System.out.println(">>> 기기 선택 완료");

            // 5. [선택하기] 버튼 클릭 (ID: selectPhone)
            WebElement selectConfirmBtn = wait.until(ExpectedConditions.elementToBeClickable(By.id("selectPhone")));
            js.executeScript("arguments[0].click();", selectConfirmBtn);
            System.out.println(">>> [선택하기] 버튼 클릭 완료");

            // 6. 요금수준 선택 (전체보기: value="all")
            WebElement planSelect = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("select#plan5GChoice")));
            new Select(planSelect).selectByValue("all");
            System.out.println(">>> 요금제 '전체보기' 선택 완료");

            // 7. [검색] 버튼 클릭
            WebElement searchBtn = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("a.h_btn.fill.size_l")));
            js.executeScript("arguments[0].click();", searchBtn);

            // 8. 결과 데이터 추출
            // 결과가 뜰 때까지 대기
            wait.until(ExpectedConditions.presenceOfElementLocated(By.className("dantong_resultbox")));

            List<WebElement> resultBlocks = driver.findElements(By.className("dantong_resultbox"));
            System.out.println(">>> 검색 결과 블록 수: " + resultBlocks.size());

            for (WebElement block : resultBlocks) {
                List<WebElement> rows = block.findElements(By.tagName("tr"));
                String currentPlanName = "기본";

                for (WebElement row : rows) {
                    // th 태그에서 요금제 구간 확인
                    List<WebElement> ths = row.findElements(By.tagName("th"));
                    if (!ths.isEmpty()) {
                        String thText = ths.get(0).getText().trim();

                        if (thText.contains("만원")) {
                            currentPlanName = thText;
                        }

                        // '번호이동' 행 데이터 추출
                        if (thText.equals("번호이동")) {
                            List<WebElement> tds = row.findElements(By.tagName("td"));

                            // 인덱스 0(SKT), 1(KT), 2(LGU+)
                            if (tds.size() >= 3) {
                                saveSubsidy(targetPhone, currentPlanName + "(번호이동)", "SKT", tds.get(0).getText());
                                saveSubsidy(targetPhone, currentPlanName + "(번호이동)", "KT", tds.get(1).getText());
                                saveSubsidy(targetPhone, currentPlanName + "(번호이동)", "LGU+", tds.get(2).getText());
                            }
                        }
                    }
                }
            }
            System.out.println(">>> [성공] " + targetPhone + " 데이터 저장 완료!");

        } catch (Exception e) {
            System.err.println(">>> 크롤링 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void saveSubsidy(String device, String plan, String telecom, String amount) {
        if (amount == null || amount.isEmpty() || amount.contains("해당사항 없음") || amount.equals("-")) return;

        subsidyRepository.save(Subsidy.builder()
                .telecom(telecom)
                .deviceName(device)
                .planName(plan)
                .supportAmount(amount)
                .build());
    }
}