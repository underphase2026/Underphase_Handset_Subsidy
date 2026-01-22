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
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SubsidyService {
    private final SubsidyRepository subsidyRepository;
    private final WebDriver driver;
    private final String TARGET_URL = "https://m.smartchoice.or.kr/smc/mobile/dantongList.do?type=m";

    @Transactional
    public void crawlAndSaveSubsidies() {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
        JavascriptExecutor js = (JavascriptExecutor) driver;

        try {
            subsidyRepository.deleteAll(); //
            driver.get(TARGET_URL);

            // 1. 제조사 리스트 확보
            wait.until(ExpectedConditions.presenceOfElementLocated(By.id("dan_Mau")));
            List<WebElement> makerOptions = new Select(driver.findElement(By.id("dan_Mau"))).getOptions();
            List<String> makerNames = new ArrayList<>();
            for (WebElement opt : makerOptions) {
                String text = opt.getText();
                if (!text.contains("제조사") && !text.isEmpty()) makerNames.add(text);
            }

            System.out.println(">>> 전수조사 시작: " + makerNames);

            for (String makerName : makerNames) {
                System.out.println("\n>>> [" + makerName + "] 수집 시작...");

                for (int attempt = 0; attempt < 1000; attempt++) { // 기기별 루프
                    try {
                        // 매번 페이지를 새로 고침하여 'stale element' 및 팝업 꼬임 방지
                        driver.get(TARGET_URL);

                        // 제조사 선택
                        WebElement makerSelect = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("dan_Mau")));
                        new Select(makerSelect).selectByVisibleText(makerName);
                        Thread.sleep(800);

                        // 휴대폰 팝업 열기
                        WebElement prodBtn = wait.until(ExpectedConditions.elementToBeClickable(By.id("product_btn")));
                        js.executeScript("arguments[0].click();", prodBtn);

                        // 현재 순서(attempt)에 해당하는 기기 찾기
                        wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.id("spanPhone_name")));
                        List<WebElement> phones = driver.findElements(By.id("spanPhone_name"));

                        if (attempt >= phones.size()) break; // 해당 제조사 기기 수집 끝

                        WebElement targetPhone = phones.get(attempt);
                        String phoneName = targetPhone.getText();
                        System.out.print(">>> (" + (attempt + 1) + "/" + phones.size() + ") [" + phoneName + "] 수집 중... ");

                        // 기기 클릭 및 [선택하기] 클릭
                        js.executeScript("arguments[0].click();", targetPhone);
                        Thread.sleep(300);
                        js.executeScript("arguments[0].click();", driver.findElement(By.id("selectPhone")));

                        // 팝업이 닫힐 때까지 대기
                        Thread.sleep(500);
                        handleAlert(); // "휴대폰을 선택해주세요" 경고창 방어

                        // 요금제 '전체보기' 및 검색
                        String planId = phoneName.contains("LTE") ? "planLTEChoice" : "plan5GChoice";
                        try {
                            WebElement planSelect = wait.until(ExpectedConditions.presenceOfElementLocated(By.id(planId)));
                            new Select(planSelect).selectByValue("all");
                        } catch (Exception e) {
                            js.executeScript("document.querySelectorAll('select[id*=\"Choice\"]').forEach(s => s.value = 'all')");
                        }

                        js.executeScript("danAllSearch('mobile');");
                        handleAlert();

                        // 결과 테이블 파싱 및 저장
                        wait.until(ExpectedConditions.presenceOfElementLocated(By.className("dantong_resultbox")));
                        parseAndSave(phoneName);
                        System.out.println("성공!");

                    } catch (Exception e) {
                        System.out.println("실패 (사유: " + e.getMessage().split(":")[0] + ")");
                        handleAlert();
                    }
                }
            }
            System.out.println("\n>>> [전수조사 완료] 모든 데이터를 저장했습니다!");

        } catch (Exception e) {
            System.err.println(">>> 치명적 오류: " + e.getMessage());
        }
    }

    // 예기치 못한 경고창(Alert)을 끄는 메서드
    private void handleAlert() {
        try {
            Alert alert = driver.switchTo().alert();
            alert.dismiss();
        } catch (Exception ignored) {}
    }

    private void parseAndSave(String phoneName) {
        List<WebElement> resultBlocks = driver.findElements(By.className("dantong_resultbox"));
        for (WebElement block : resultBlocks) {
            List<WebElement> rows = block.findElements(By.tagName("tr"));
            String currentPlan = "구간확인";
            for (WebElement row : rows) {
                List<WebElement> ths = row.findElements(By.tagName("th"));
                if (!ths.isEmpty()) {
                    String thText = ths.get(0).getText().trim();
                    if (thText.contains("만원")) currentPlan = thText;
                    if (thText.equals("번호이동")) {
                        List<WebElement> tds = row.findElements(By.tagName("td"));
                        if (tds.size() >= 3) {
                            saveSubsidy(phoneName, currentPlan + "(번호이동)", "SKT", tds.get(0).getText());
                            saveSubsidy(phoneName, currentPlan + "(번호이동)", "KT", tds.get(1).getText());
                            saveSubsidy(phoneName, currentPlan + "(번호이동)", "LGU+", tds.get(2).getText());
                        }
                    }
                }
            }
        }
    }

    private void saveSubsidy(String device, String plan, String telecom, String amount) {
        if (amount == null || amount.isEmpty() || amount.contains("해당사항 없음") || amount.equals("-")) return;
        subsidyRepository.save(Subsidy.builder()
                .telecom(telecom).deviceName(device).planName(plan).supportAmount(amount).build());
    }
}