package underphase_info.Smart_Choice_Cloring.Service;

import underphase_info.Smart_Choice_Cloring.Entity.Subsidy;
import underphase_info.Smart_Choice_Cloring.Repository.SubsidyRepository;
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
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
        JavascriptExecutor js = (JavascriptExecutor) driver;

        try {
            driver.get(TARGET_URL);

            // 1. 제조사 리스트 확보
            wait.until(ExpectedConditions.presenceOfElementLocated(By.id("dan_Mau")));
            List<WebElement> makerOptions = new Select(driver.findElement(By.id("dan_Mau"))).getOptions();
            List<String> makerNames = new ArrayList<>();

            for (WebElement opt : makerOptions) {
                String text = opt.getText().trim();
                if (text.contains("삼성") || text.contains("애플")) {
                    makerNames.add(text);
                }
            }

            System.out.println(">>> 수집 시작 (대상: " + makerNames + ")");

            for (String makerName : makerNames) {
                System.out.println("\n>>> [" + makerName + "] 수집 시작...");

                for (int attempt = 0; attempt < 1000; attempt++) {
                    try {
                        driver.getCurrentUrl();
                        driver.get(TARGET_URL);

                        WebElement makerSelect = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("dan_Mau")));
                        new Select(makerSelect).selectByVisibleText(makerName);
                        Thread.sleep(800);

                        WebElement prodBtn = wait.until(ExpectedConditions.elementToBeClickable(By.id("product_btn")));
                        js.executeScript("arguments[0].click();", prodBtn);

                        wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.id("spanPhone_name")));
                        List<WebElement> phones = driver.findElements(By.id("spanPhone_name"));

                        if (attempt >= phones.size()) break;

                        WebElement targetPhone = phones.get(attempt);
                        String phoneName = targetPhone.getText();

                        if (subsidyRepository.existsByDeviceName(phoneName)) {
                            System.out.println(">>> (" + (attempt + 1) + "/" + phones.size() + ") [" + phoneName + "] 스킵");
                            continue;
                        }

                        System.out.print(">>> (" + (attempt + 1) + "/" + phones.size() + ") [" + phoneName + "] 수집 중... ");

                        js.executeScript("arguments[0].click();", targetPhone);
                        Thread.sleep(300);
                        js.executeScript("arguments[0].click();", driver.findElement(By.id("selectPhone")));

                        Thread.sleep(800);
                        handleAlert();

                        String planId = phoneName.contains("LTE") ? "planLTEChoice" : "plan5GChoice";
                        try {
                            WebElement planSelect = wait.until(ExpectedConditions.presenceOfElementLocated(By.id(planId)));
                            new Select(planSelect).selectByValue("all");
                        } catch (Exception e) {
                            js.executeScript("document.querySelectorAll('select[id*=\"Choice\"]').forEach(s => s.value = 'all')");
                        }

                        js.executeScript("danAllSearch('mobile');");
                        handleAlert();

                        wait.until(ExpectedConditions.presenceOfElementLocated(By.className("dantong_resultbox")));
                        parseAndSave(phoneName, makerName);
                        System.out.println("성공!");

                    } catch (WebDriverException e) {
                        if (e.getMessage().contains("invalid session id") || e.getMessage().contains("no such window")) {
                            System.err.println("\n>>> [치명적 오류] 세션 종료로 중단합니다.");
                            break;
                        }
                        System.out.println("실패 (사유: " + e.getMessage().split(":")[0] + ")");
                        handleAlert();
                    } catch (Exception e) {
                        System.out.println("실패 (일반 오류: " + e.getClass().getSimpleName() + ")");
                    }
                }
            }

            // [핵심 추가] 모든 제조사/기기 수집 완료 후 종료 처리
            System.out.println("\n========================================");
            System.out.println(">>> [수집 완료] 모든 데이터 수집을 마쳤습니다.");
            System.out.println("========================================\n");

        } catch (Exception e) {
            System.err.println(">>> 전체 프로세스 오류: " + e.getMessage());
        } finally {
            // [안정성 강화] 리소스 해제 및 프로세스 종료
            if (driver != null) {
                driver.quit(); // 브라우저 창 닫기 및 세션 종료
                System.out.println(">>> 브라우저 세션을 종료했습니다.");
            }
            System.out.println(">>> 애플리케이션을 종료합니다.");
            System.exit(0); // 자바 프로세스 강제 종료
        }
    }

    private void handleAlert() {
        try {
            Alert alert = driver.switchTo().alert();
            alert.dismiss();
        } catch (Exception ignored) {}
    }

    private void parseAndSave(String phoneName, String makerName) {
        List<WebElement> resultBlocks = driver.findElements(By.className("dantong_resultbox"));
        for (WebElement block : resultBlocks) {
            List<WebElement> rows = block.findElements(By.tagName("tr"));
            String currentRange = "구간확인";
            String currentPlanName = "알수없음";

            for (WebElement row : rows) {
                List<WebElement> ths = row.findElements(By.tagName("th"));
                List<WebElement> tds = row.findElements(By.tagName("td"));

                if (ths.isEmpty() || tds.isEmpty()) continue;

                StringBuilder rowHeaderBuilder = new StringBuilder();
                for (WebElement th : ths) rowHeaderBuilder.append(th.getText()).append(" ");
                String rowHeaderText = rowHeaderBuilder.toString();

                if (rowHeaderText.contains("만원")) {
                    currentRange = rowHeaderText.trim();
                    try {
                        WebElement nameSpan = row.findElement(By.cssSelector("span.name"));
                        currentPlanName = nameSpan.getText().trim();
                    } catch (NoSuchElementException ignored) {}
                }

                String supportType = null;
                if (rowHeaderText.contains("번호이동")) supportType = "번호이동";
                else if (rowHeaderText.contains("기기변경")) supportType = "기기변경";

                if (supportType != null && tds.size() >= 3) {
                    int size = tds.size();
                    saveSubsidy(makerName, phoneName, currentPlanName, currentRange, supportType, "SKT", tds.get(size - 3).getText());
                    saveSubsidy(makerName, phoneName, currentPlanName, currentRange, supportType, "KT", tds.get(size - 2).getText());
                    saveSubsidy(makerName, phoneName, currentPlanName, currentRange, supportType, "LGU+", tds.get(size - 1).getText());
                }
            }
        }
    }

    private void saveSubsidy(String maker, String device, String plan, String range, String type, String telecom, String amount) {
        if (amount == null || amount.isEmpty() || amount.contains("-") || amount.contains("해당사항")) return;

        String cleanAmount = amount.replaceAll("[^0-9]", "");
        if (cleanAmount.isEmpty()) return;

        if (!subsidyRepository.existsByMakerAndDeviceNameAndPlanNameAndPlanRangeAndSupportTypeAndTelecom(
                maker, device, plan, range, type, telecom)) {

            subsidyRepository.save(Subsidy.builder()
                    .maker(maker)
                    .deviceName(device)
                    .planName(plan)
                    .planRange(range)
                    .supportType(type)
                    .telecom(telecom)
                    .supportAmount(cleanAmount)
                    .build());
        }
    }
}