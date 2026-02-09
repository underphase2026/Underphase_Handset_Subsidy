package underphase_info.LG_Cloring.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import underphase_info.Smart_Choice_Cloring.Entity.Subsidy;
import underphase_info.Smart_Choice_Cloring.Repository.SubsidyRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LG_SubsidyService {

    private final SubsidyRepository subsidyRepository;
    private final ChromeOptions chromeOptions;

    private static final String TARGET_URL = "https://www.lguplus.com/mobile/financing-model#assen";

    // 기기 키워드 리스트 (이 키워드를 포함하는 모든 기기를 추출합니다)
    private static final List<String> DEVICE_KEYWORDS = Arrays.asList(
            "iPhone 16 Pro Max", "iPhone 16 Pro", "iPhone 16",
            "Galaxy S24 Ultra", "Galaxy S24+", "Galaxy S24");

    // 요금제 매칭 키워드 (이 텍스트를 포함하는 모든 요금제를 긁어옵니다)
    private static final List<String> LGU_PLAN_KEYWORDS = Arrays.asList(
            "5G 시그니처", "5G 프리미어 슈퍼", "5G 프리미어 플러스", "5G 프리미어 에센셜", "5G 데이터 슈퍼", "5G 라이트 +");

    @Transactional
    public void crawlLguSubsidies() {
        log.info(">>> [LG U+ 크롤러 시작] 파생 요금제 포함 전체 동기화를 시작합니다.");

        Map<String, Subsidy> existingSubsidies = loadExistingLguSubsidies();
        WebDriver driver = null;

        try {
            driver = new ChromeDriver(chromeOptions);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
            JavascriptExecutor js = (JavascriptExecutor) driver;

            driver.get(TARGET_URL);
            Thread.sleep(3000);

            // 1. 가입유형: 신규가입 선택
            wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//label[@for='3']"))).click();
            Thread.sleep(1000);

            // ⭐ [핵심 수정] 팝업을 열어 실제 존재하는 모든 요금제(AI구독, 콜라보 등) 명칭 + 가격 수집
            Map<String, String> allActualPlans = discoverAllActualPlans(driver, wait, js);
            log.info(">>> 발견된 총 요금제 수: {}개", allActualPlans.size());

            // 2. 수집된 실제 요금제 명칭으로 순회
            for (Map.Entry<String, String> planEntry : allActualPlans.entrySet()) {
                String actualPlanName = planEntry.getKey();
                String planPrice = planEntry.getValue(); // 예: "130,000원"

                try {
                    log.info("\n========== [요금제: {} ({})] 작업 시작 ==========", actualPlanName, planPrice);

                    // 2-1. 요금제 팝업 열기 (class="c-btn-rect-2")
                    WebElement morePlansBtn = wait.until(ExpectedConditions
                            .elementToBeClickable(By.cssSelector("button.c-btn-rect-2")));
                    js.executeScript("arguments[0].scrollIntoView(true);", morePlansBtn);
                    Thread.sleep(300);
                    js.executeScript("arguments[0].click();", morePlansBtn);
                    log.info(">>> 요금제 팝업 열기 클릭");
                    Thread.sleep(2000);

                    // 2-2. 정확한 전체 이름으로 요금제 선택
                    WebElement planOption = wait.until(ExpectedConditions.elementToBeClickable(
                            By.xpath("//p[@class='contents__title' and text()='" + actualPlanName + "']")));
                    js.executeScript("arguments[0].click();", planOption);
                    log.info(">>> 요금제 클릭 완료: {}", actualPlanName);
                    Thread.sleep(1000);

                    // 2-3. 적용 버튼 클릭 (class="c-btn-solid-1-m")
                    WebElement applyBtn = wait.until(ExpectedConditions.elementToBeClickable(
                            By.cssSelector("button.c-btn-solid-1-m")));
                    js.executeScript("arguments[0].scrollIntoView(true);", applyBtn);
                    Thread.sleep(500);
                    js.executeScript("arguments[0].click();", applyBtn);
                    log.info(">>> 적용 버튼 클릭 완료");
                    Thread.sleep(2500);

                    // 3. 단말기 검색 및 데이터 추출 (키워드별로 검색)
                    for (String deviceKeyword : DEVICE_KEYWORDS) {
                        crawlDeviceData(driver, wait, js, deviceKeyword, actualPlanName, planPrice, existingSubsidies);
                    }

                } catch (Exception e) {
                    log.warn(">>> [오류] 요금제 {} 처리 중 에러: {}", actualPlanName, e.getMessage());
                    closePopupIfOpen(driver);
                }
            }

            // 4. 사라진 데이터 삭제
            if (!existingSubsidies.isEmpty()) {
                log.info(">>> 웹사이트에서 사라진 데이터 {}개를 삭제합니다.", existingSubsidies.size());
                subsidyRepository.deleteAll(existingSubsidies.values());
            }

            log.info(">>> [LG U+ 크롤러 완료] 동기화 작업이 정상 종료되었습니다.");

        } catch (Exception e) {
            log.error(">>> 치명적 오류: ", e);
        } finally {
            if (driver != null)
                driver.quit();
        }
    }

    /**
     * 팝업 내 모든 요금제 중 키워드와 매칭되는 실제 전체 명칭 + 가격을 수집합니다.
     * 
     * @return Map<요금제명, 요금제가격> (예: "(유튜브 프리미엄) 5G 시그니처" -> "130,000원")
     */
    private Map<String, String> discoverAllActualPlans(WebDriver driver, WebDriverWait wait, JavascriptExecutor js)
            throws InterruptedException {
        // 요금제 팝업 열기 (class="c-btn-rect-2")
        WebElement morePlansBtn = wait.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector("button.c-btn-rect-2")));
        js.executeScript("arguments[0].scrollIntoView(true);", morePlansBtn);
        Thread.sleep(300);
        js.executeScript("arguments[0].click();", morePlansBtn);
        Thread.sleep(2000);

        // 각 요금제 라벨(label) 요소를 순회하며 명칭과 가격 추출
        List<WebElement> labelElements = driver.findElements(By.cssSelector("label.text-radio"));
        Map<String, String> discovered = new LinkedHashMap<>();

        for (WebElement label : labelElements) {
            try {
                String fullTitle = label.findElement(By.cssSelector("p.contents__title")).getText().trim();
                String priceText = label.findElement(By.cssSelector("p.contents__price")).getText().trim();
                // "월 130,000원" -> "130,000원" 으로 정리
                String cleanPrice = priceText.replace("월", "").replace("\u00A0", "").trim();

                for (String keyword : LGU_PLAN_KEYWORDS) {
                    if (fullTitle.contains(keyword)) {
                        discovered.put(fullTitle, cleanPrice);
                        break;
                    }
                }
            } catch (org.openqa.selenium.NoSuchElementException ignored) {
                // 가격 요소가 없는 경우 건너뜀
            }
        }

        // 수집 후 팝업 닫기 (ESC)
        driver.findElement(By.tagName("body")).sendKeys(Keys.ESCAPE);
        Thread.sleep(1000);
        return discovered;
    }

    private void crawlDeviceData(WebDriver driver, WebDriverWait wait, JavascriptExecutor js,
            String deviceKeyword, String actualPlanName, String planPrice, Map<String, Subsidy> existingSubsidies) {
        try {
            // 검색창으로 스크롤 (스티키 헤더 문제 방지)
            WebElement searchInput = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("cfrmSearch-1-1")));
            js.executeScript("arguments[0].scrollIntoView({block: 'center'});", searchInput);
            Thread.sleep(500);

            // 검색창에 기기 키워드 입력
            searchInput.clear();
            searchInput.sendKeys(deviceKeyword);

            // ⭐ 검색 버튼 JavaScript 클릭 (element click intercepted 오류 방지)
            WebElement searchBtn = driver.findElement(By.cssSelector("button.c-ibtn-find"));
            js.executeScript("arguments[0].click();", searchBtn);
            Thread.sleep(2500);

            // ⭐ LG U+ 실제 구조: 검색 후 결과 테이블에서 24개월 할인금액 추출
            // 테이블 구조: 기간별 할인금액 | 월 할인금액 | 24개월 할인금액
            List<WebElement> tables = driver.findElements(By.cssSelector("table"));

            for (WebElement table : tables) {
                try {
                    // 테이블 내 모든 행 확인
                    List<WebElement> rows = table.findElements(By.cssSelector("tbody tr"));
                    for (WebElement row : rows) {
                        List<WebElement> cells = row.findElements(By.cssSelector("td"));
                        if (cells.size() >= 2) {
                            // 마지막 셀에서 24개월 할인금액 추출
                            String lastCellText = cells.get(cells.size() - 1).getText().trim();

                            // 숫자만 추출 (예: "510,000원" -> "510000")
                            String subsidyAmount = lastCellText.replaceAll("[^0-9]", "");

                            if (!subsidyAmount.isEmpty() && subsidyAmount.length() >= 4) {
                                // 검색된 기기 키워드를 기기명으로 사용
                                String maker = deviceKeyword.toLowerCase().contains("iphone") ? "Apple" : "Samsung";

                                log.info(">>> [데이터 발견] {} | {} | {}원", deviceKeyword, actualPlanName, subsidyAmount);
                                syncSubsidy(maker, deviceKeyword, actualPlanName, planPrice, subsidyAmount,
                                        existingSubsidies);

                                // 첫 번째 유효한 금액만 저장 (중복 방지)
                                return;
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }

            log.info("  [결과 없음] {} - 해당 요금제에서 지원금 데이터 없음", deviceKeyword);

        } catch (Exception e) {
            log.warn("  [건너뜀] {} 검색 실패: {}", deviceKeyword, e.getMessage());
        }
    }

    private Map<String, Subsidy> loadExistingLguSubsidies() {
        return subsidyRepository.findAll().stream()
                .filter(s -> "LG U+".equals(s.getTelecom()))
                .collect(
                        Collectors.toMap(s -> s.getDeviceName() + "|" + s.getPlanName(), s -> s, (oldV, newV) -> oldV));
    }

    private void syncSubsidy(String maker, String deviceName, String planName, String planRange, String amount,
            Map<String, Subsidy> existingSubsidies) {
        if (amount == null || amount.isEmpty())
            return;
        String key = deviceName + "|" + planName;
        Subsidy existing = existingSubsidies.get(key);

        if (existing != null) {
            if (!existing.getSupportAmount().equals(amount)) {
                log.info(">>> [업데이트] {} | {} ({}원)", deviceName, planName, amount);
                existing.updateAmount(amount); // Entity에 update 메서드 있다고 가정
                subsidyRepository.save(existing);
            }
            existingSubsidies.remove(key);
        } else {
            log.info("    [신규] {} | {} | {} | {}원", deviceName, planName, planRange, amount);
            subsidyRepository.save(Subsidy.builder()
                    .maker(maker).deviceName(deviceName).telecom("LG U+")
                    .planName(planName).planRange(planRange).supportType("신규가입").supportAmount(amount)
                    .crawledAt(LocalDateTime.now()).build());
        }
    }

    private void closePopupIfOpen(WebDriver driver) {
        try {
            driver.findElement(By.tagName("body")).sendKeys(Keys.ESCAPE);
            Thread.sleep(500);
        } catch (Exception ignored) {
        }
    }
}