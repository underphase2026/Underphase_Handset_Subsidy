package underphase_info.Smart_Choice_Cloring.Service;

import underphase_info.Smart_Choice_Cloring.Entity.Subsidy;
import underphase_info.Smart_Choice_Cloring.Repository.SubsidyRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubsidyService {
    private final SubsidyRepository subsidyRepository;
    private final WebDriver driver;
    private final String TARGET_URL = "https://m.smartchoice.or.kr/smc/mobile/dantongList.do?type=m";

    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void crawlAndSaveSubsidies() {
        log.info(">>> [스케줄러 시작] 공시지원금 데이터 동기화 작업을 시작합니다.");

        // 0. 기존 데이터 로드 (Sync를 위해)
        Map<String, Subsidy> existingSubsidies = loadExistingSubsidies();
        log.info(">>> 현재 DB 내 데이터 수: {}개", existingSubsidies.size());

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

            log.info(">>> 수집 대상 제조사: {}", makerNames);

            for (String makerName : makerNames) {
                log.info("\n>>> [{}] 수집 시작...", makerName);

                for (int attempt = 0; attempt < 1000; attempt++) {
                    try {
                        driver.getCurrentUrl(); // 세션 체크
                        driver.get(TARGET_URL);

                        WebElement makerSelect = wait
                                .until(ExpectedConditions.presenceOfElementLocated(By.id("dan_Mau")));
                        new Select(makerSelect).selectByVisibleText(makerName);
                        Thread.sleep(800);

                        WebElement prodBtn = wait.until(ExpectedConditions.elementToBeClickable(By.id("product_btn")));
                        js.executeScript("arguments[0].click();", prodBtn);

                        wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.id("spanPhone_name")));
                        List<WebElement> phones = driver.findElements(By.id("spanPhone_name"));

                        if (attempt >= phones.size())
                            break;

                        WebElement targetPhone = phones.get(attempt);
                        String phoneName = targetPhone.getText();

                        log.info(">>> ({}/{}) [{}] 수집 중... ", attempt + 1, phones.size(), phoneName);

                        js.executeScript("arguments[0].click();", targetPhone);
                        Thread.sleep(300);
                        js.executeScript("arguments[0].click();", driver.findElement(By.id("selectPhone")));

                        Thread.sleep(800);
                        handleAlert();

                        String planId = phoneName.contains("LTE") ? "planLTEChoice" : "plan5GChoice";
                        try {
                            WebElement planSelect = wait
                                    .until(ExpectedConditions.presenceOfElementLocated(By.id(planId)));
                            new Select(planSelect).selectByValue("all");
                        } catch (Exception e) {
                            js.executeScript(
                                    "document.querySelectorAll('select[id*=\"Choice\"]').forEach(s => s.value = 'all')");
                        }

                        js.executeScript("danAllSearch('mobile');");
                        handleAlert();

                        wait.until(ExpectedConditions.presenceOfElementLocated(By.className("dantong_resultbox")));

                        // 현재 페이지의 데이터 파싱 및 Sync 처리
                        parseAndSync(phoneName, makerName, existingSubsidies);

                    } catch (WebDriverException e) {
                        if (e.getMessage().contains("invalid session id")
                                || e.getMessage().contains("no such window")) {
                            log.error(">>> [치명적 오류] 세션이 종료되어 작업을 중단합니다.");
                            throw e; // 상위로 던져서 리소스 정리
                        }
                        log.warn(">>> 항목 수집 실패 (사유: {})", e.getMessage().split(":")[0]);
                        handleAlert();
                    } catch (Exception e) {
                        log.warn(">>> 항목 수집 실패 (일반 오류: {})", e.getClass().getSimpleName());
                    }
                }
            }

            // [핵심 로직] 남은 existingSubsidies는 웹사이트에서 사라진 데이터이므로 삭제
            if (!existingSubsidies.isEmpty()) {
                log.info(">>> [삭제 처리] 웹사이트에서 사라진 데이터 {}개를 DB에서 제거합니다.", existingSubsidies.size());
                subsidyRepository.deleteAll(existingSubsidies.values());
            } else {
                log.info(">>> [삭제 처리] 삭제할 데이터가 없습니다.");
            }

            log.info("\n========================================");
            log.info(">>> [동기화 완료] 모든 데이터 처리가 끝났습니다.");
            log.info("========================================\n");

        } catch (Exception e) {
            log.error(">>> 전체 프로세스 오류: ", e);
        }
        // 주의: 스케줄러에서는 System.exit(0)이나 driver.quit()를 호출하면 안 됨 (다음 스케줄을 위해 유지하거나, 빈 종료
        // 시점에 처리)
        // 여기서는 driver가 빈으로 관리되므로 메서드 종료 시 브라우저를 닫지 않고 유지할지, 아니면 매번 새로 띄울지에 따라 다르나
        // 현재 구조상 주입받은 driver를 계속 재사용한다고 가정.
    }

    private Map<String, Subsidy> loadExistingSubsidies() {
        List<Subsidy> all = subsidyRepository.findAll();
        Map<String, Subsidy> map = new HashMap<>();
        for (Subsidy s : all) {
            map.put(generateKey(s.getMaker(), s.getDeviceName(), s.getPlanName(), s.getPlanRange(), s.getSupportType(),
                    s.getTelecom()), s);
        }
        return map;
    }

    private String generateKey(String maker, String device, String plan, String range, String type, String telecom) {
        return maker + "|" + device + "|" + plan + "|" + range + "|" + type + "|" + telecom;
    }

    private void handleAlert() {
        try {
            Alert alert = driver.switchTo().alert();
            alert.dismiss();
        } catch (Exception ignored) {
        }
    }

    private void parseAndSync(String phoneName, String makerName, Map<String, Subsidy> existingSubsidies) {
        List<WebElement> resultBlocks = driver.findElements(By.className("dantong_resultbox"));
        for (WebElement block : resultBlocks) {
            List<WebElement> rows = block.findElements(By.tagName("tr"));
            String currentRange = "구간확인";
            String currentPlanName = "알수없음";

            for (WebElement row : rows) {
                List<WebElement> ths = row.findElements(By.tagName("th"));
                List<WebElement> tds = row.findElements(By.tagName("td"));

                if (ths.isEmpty() || tds.isEmpty())
                    continue;

                StringBuilder rowHeaderBuilder = new StringBuilder();
                for (WebElement th : ths)
                    rowHeaderBuilder.append(th.getText()).append(" ");
                String rowHeaderText = rowHeaderBuilder.toString();

                if (rowHeaderText.contains("만원")) {
                    currentRange = rowHeaderText.trim();
                    try {
                        WebElement nameSpan = row.findElement(By.cssSelector("span.name"));
                        currentPlanName = nameSpan.getText().trim();
                    } catch (NoSuchElementException ignored) {
                    }
                }

                String supportType = null;
                if (rowHeaderText.contains("번호이동"))
                    supportType = "번호이동";
                else if (rowHeaderText.contains("기기변경"))
                    supportType = "기기변경";

                if (supportType != null && tds.size() >= 3) {
                    int size = tds.size();
                    syncSubsidy(makerName, phoneName, currentPlanName, currentRange, supportType, "SKT",
                            tds.get(size - 3).getText(), existingSubsidies);
                    syncSubsidy(makerName, phoneName, currentPlanName, currentRange, supportType, "KT",
                            tds.get(size - 2).getText(), existingSubsidies);
                    syncSubsidy(makerName, phoneName, currentPlanName, currentRange, supportType, "LGU+",
                            tds.get(size - 1).getText(), existingSubsidies);
                }
            }
        }
    }

    private void syncSubsidy(String maker, String device, String plan, String range, String type, String telecom,
            String amount, Map<String, Subsidy> existingSubsidies) {
        if (amount == null || amount.isEmpty() || amount.contains("-") || amount.contains("해당사항"))
            return;

        String cleanAmount = amount.replaceAll("[^0-9]", "");
        if (cleanAmount.isEmpty())
            return;

        String key = generateKey(maker, device, plan, range, type, telecom);
        Subsidy existing = existingSubsidies.get(key);

        if (existing != null) {
            // [UPDATE] 기존 데이터 존재: 금액이 다르면 업데이트
            if (!existing.getSupportAmount().equals(cleanAmount)) {
                log.info(">>> [업데이트] {} {} (기존: {} -> 변경: {})", device, telecom, existing.getSupportAmount(),
                        cleanAmount);
                // JPA Dirty Checking을 이용하려면 객체의 필드를 수정해야 함.
                // 여기서는 간단히 리포지토리 save(엔티티는 Setter가 없으므로 Builder로 새로 생성하여 ID 유지하거나 별도 메서드 필요)
                // @Builder로 불변 객체라면 새로 save하면 됨 (ID가 같으면 update지만 여기선 ID를 모르므로..
                // 아, existing에 객체가 있으니 ID가 있음. Entity에 Setter가 없으므로 Reflection이나 내부 메서드 필요.
                // 가장 깔끔한 건: Builder로 새로 만들고 ID를 세팅해주는 것.

                Subsidy updated = Subsidy.builder()
                        .id(existing.getId()) // ID 유지 -> Update
                        .maker(maker)
                        .deviceName(device)
                        .planName(plan)
                        .planRange(range)
                        .supportType(type)
                        .telecom(telecom)
                        .supportAmount(cleanAmount)
                        .crawledAt(LocalDateTime.now())
                        .build();
                subsidyRepository.save(updated);
            }
            // 현행화되었으므로 맵에서 제거 (나중에 맵에 남은건 삭제됨)
            existingSubsidies.remove(key);
        } else {
            // [CREATE] 없음 -> 신규 저장
            // log.info(">>> [신규] {} {}", device, telecom); // 로그 너무 많으면 생략
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