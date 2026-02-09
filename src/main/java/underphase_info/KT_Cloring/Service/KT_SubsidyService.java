package underphase_info.KT_Cloring.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
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
public class KT_SubsidyService {

    private final SubsidyRepository subsidyRepository;
    private final ChromeOptions chromeOptions;

    private static final String TARGET_URL = "https://shop.kt.com/m/smart/supportAmtList.do";

    // 제조사 코드 매핑
    private static final Map<String, String> MAKER_CODES = Map.of(
            "삼성", "13",
            "Apple", "15");

    // 취급 단말기 목록 (iPhone - KT는 영어로 표시)
    private static final List<String> IPHONE_MODELS = Arrays.asList(
            "iPhone 17 Pro Max", "iPhone 17 Pro", "iPhone 17 Air", "iPhone 17",
            "iPhone 16 Pro Max", "iPhone 16 Pro", "iPhone 16 Plus", "iPhone 16");

    // 취급 단말기 목록 (Galaxy - KT는 한글로 표시)
    private static final List<String> GALAXY_MODELS = Arrays.asList(
            "갤럭시 Z Fold7", "갤럭시 Z Flip7",
            "갤럭시 S25 Ultra", "갤럭시 S25 엣지", "갤럭시 S25+", "갤럭시 S25");

    // 취급 요금제 목록 (부분 일치 검색 키워드)
    private static final List<String> TARGET_PLANS = Arrays.asList(
            "초이스 프리미엄", "초이스 스페셜", "초이스 베이직",
            "베이직", "심플 110GB", "슬림 14GB");

    @Transactional
    public void crawlKtSubsidies() {
        log.info(">>> [KT 크롤러 시작] KT Shop 공시지원금 크롤링을 시작합니다.");

        WebDriver driver = new ChromeDriver(chromeOptions);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
        JavascriptExecutor js = (JavascriptExecutor) driver;

        try {
            Map<String, Subsidy> existingSubsidies = loadExistingKtSubsidies();
            log.info(">>> 기존 KT 데이터 {}건 로드 완료", existingSubsidies.size());

            driver.get(TARGET_URL);
            Thread.sleep(3000);

            // 1. 휴대폰 선택 (5GPhone)
            selectDropdown(driver, "selProdType", "5GPhone");
            Thread.sleep(1000);

            // 2. 제조사별 크롤링 (삼성, Apple)
            for (Map.Entry<String, String> maker : MAKER_CODES.entrySet()) {
                String makerName = maker.getKey();
                String makerCode = maker.getValue();
                List<String> targetDevices = makerName.equals("Apple") ? IPHONE_MODELS : GALAXY_MODELS;

                log.info("\n========== [제조사: {}] 크롤링 시작 ==========", makerName);

                selectDropdown(driver, "makrCd", makerCode);
                Thread.sleep(2000);

                // 3. 요금제별 크롤링
                for (String targetPlan : TARGET_PLANS) {
                    log.info("\n>>> [요금제: {}] 처리 시작", targetPlan);

                    try {
                        // 3-1. 요금제 변경 버튼 클릭 (팝업 열기)
                        WebElement changeBtn = wait
                                .until(ExpectedConditions.elementToBeClickable(By.id("btnPayChange")));
                        js.executeScript("arguments[0].click();", changeBtn);
                        Thread.sleep(1500);

                        // 3-2. 전체요금제 탭 클릭
                        WebElement allPlanTab = wait
                                .until(ExpectedConditions.elementToBeClickable(By.id("pplGroupObj_ALL")));
                        js.executeScript("arguments[0].click();", allPlanTab);
                        Thread.sleep(1000);

                        // 3-3. 해당 요금제 찾아서 클릭
                        boolean planFound = selectPlanByName(driver, js, targetPlan);
                        if (!planFound) {
                            log.warn(">>> [{}] 요금제를 찾을 수 없습니다", targetPlan);
                            closePopupIfOpen(driver, js);
                            continue;
                        }
                        Thread.sleep(500);

                        // 3-4. 선택완료 버튼 클릭
                        WebElement confirmBtn = wait
                                .until(ExpectedConditions.elementToBeClickable(By.id("btnLayerItem")));
                        js.executeScript("arguments[0].click();", confirmBtn);
                        Thread.sleep(2000);

                        // 3-5. 신규가입 선택
                        selectDropdown(driver, "sbscTypeCd", "01");
                        Thread.sleep(1000);

                        // 3-6. 심플 코스 선택
                        selectDropdown(driver, "courseCd", "simple");
                        Thread.sleep(2000);

                        // 3-7. 요금제 가격 추출
                        String planPrice = extractPlanPrice(driver);
                        log.info(">>> 요금제 가격: {}", planPrice);

                        // 4. 기기별 지원금 추출
                        extractDeviceSubsidies(driver, wait, js, makerName, targetPlan, planPrice, targetDevices,
                                existingSubsidies);

                    } catch (Exception e) {
                        log.warn(">>> [{}] 요금제 처리 실패: {}", targetPlan, e.getMessage());
                        closePopupIfOpen(driver, js);
                    }
                }
            }

            log.info(">>> [KT 크롤러 완료]");

        } catch (Exception e) {
            log.error("KT 크롤링 중 오류 발생", e);
        } finally {
            driver.quit();
        }
    }

    /**
     * 드롭다운(select) 요소에서 값 선택
     */
    private void selectDropdown(WebDriver driver, String selectId, String value) {
        try {
            WebElement selectElement = driver.findElement(By.id(selectId));
            Select select = new Select(selectElement);
            select.selectByValue(value);
            log.info(">>> [{}] 선택: {}", selectId, value);
        } catch (Exception e) {
            log.warn(">>> [{}] 선택 실패: {}", selectId, e.getMessage());
        }
    }

    /**
     * 기존 KT 데이터 로드
     */
    private Map<String, Subsidy> loadExistingKtSubsidies() {
        return subsidyRepository.findAll().stream()
                .filter(s -> "KT".equals(s.getTelecom()))
                .collect(
                        Collectors.toMap(s -> s.getDeviceName() + "|" + s.getPlanName(), s -> s, (oldV, newV) -> oldV));
    }

    /**
     * DB 동기화 (신규 저장 또는 업데이트)
     */
    private void syncSubsidy(String maker, String deviceName, String planName, String planRange, String amount,
            Map<String, Subsidy> existingSubsidies) {
        if (amount == null || amount.isEmpty())
            return;
        String key = deviceName + "|" + planName;
        Subsidy existing = existingSubsidies.get(key);

        if (existing != null) {
            if (!existing.getSupportAmount().equals(amount)) {
                log.info(">>> [업데이트] {} | {} ({}원)", deviceName, planName, amount);
                existing.updateAmount(amount);
                subsidyRepository.save(existing);
            }
            existingSubsidies.remove(key);
        } else {
            log.info("    [신규] {} | {} | {} | {}원", deviceName, planName, planRange, amount);
            subsidyRepository.save(Subsidy.builder()
                    .maker(maker).deviceName(deviceName).telecom("KT")
                    .planName(planName).planRange(planRange).supportType("신규가입").supportAmount(amount)
                    .crawledAt(LocalDateTime.now()).build());
        }
    }

    /**
     * 요금제 이름으로 해당 요금제 선택
     * 
     * @return 요금제를 찾아서 클릭했으면 true, 못 찾으면 false
     */
    private boolean selectPlanByName(WebDriver driver, JavascriptExecutor js, String planName) {
        try {
            // 요금제 목록에서 해당 이름을 포함하는 div 찾기
            // 예: <div id="selPplNm_0940">티빙/지니/밀리 초이스 프리미엄</div>
            List<WebElement> planElements = driver.findElements(By.cssSelector("div[id^='selPplNm_']"));

            for (WebElement planEl : planElements) {
                String text = planEl.getText().trim();
                if (text.contains(planName)) {
                    js.executeScript("arguments[0].scrollIntoView({block: 'center'});", planEl);
                    Thread.sleep(300);
                    js.executeScript("arguments[0].click();", planEl);
                    log.info(">>> [요금제 클릭] {}", text);
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            log.warn(">>> 요금제 선택 중 오류: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 팝업이 열려있으면 닫기
     */
    private void closePopupIfOpen(WebDriver driver, JavascriptExecutor js) {
        try {
            // 팝업 닫기 버튼 찾기 (예: X 버튼 또는 취소 버튼)
            List<WebElement> closeButtons = driver.findElements(By.cssSelector(".btnLayerClose, .btn-close, .close"));
            for (WebElement btn : closeButtons) {
                if (btn.isDisplayed()) {
                    js.executeScript("arguments[0].click();", btn);
                    Thread.sleep(500);
                    break;
                }
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 현재 선택된 요금제의 가격 추출
     * 예: "90,000원 (요금할인 25% 시 67,500원)" → "90,000원"
     */
    private String extractPlanPrice(WebDriver driver) {
        try {
            WebElement priceElement = driver.findElement(By.id("rcmdPplChargeDsp"));
            String fullText = priceElement.getText().trim();
            // "90,000원 (요금할인..." 에서 첫 번째 가격만 추출
            if (fullText.contains("(")) {
                return fullText.substring(0, fullText.indexOf("(")).trim();
            }
            return fullText;
        } catch (Exception e) {
            log.warn(">>> 요금제 가격 추출 실패: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 기기별 공시지원금 추출
     * 페이지에 표시된 기기 목록에서 targetDevices에 해당하는 기기의 공통지원금 추출
     */
    private void extractDeviceSubsidies(WebDriver driver, WebDriverWait wait, JavascriptExecutor js,
            String makerName, String planName, String planPrice, List<String> targetDevices,
            Map<String, Subsidy> existingSubsidies) {
        try {
            // 페이지에 표시된 모든 기기 카드 찾기
            List<WebElement> deviceCards = driver.findElements(By.cssSelector(".prodName"));

            for (WebElement deviceCard : deviceCards) {
                String deviceFullName = deviceCard.getText().trim();

                // targetDevices 목록과 매칭되는지 확인
                for (String targetDevice : targetDevices) {
                    if (deviceFullName.contains(targetDevice)) {
                        try {
                            // 해당 기기 카드의 부모 요소에서 공통지원금 찾기
                            WebElement parentCard = deviceCard.findElement(By.xpath(
                                    "./ancestor::li[contains(@class,'prodItem')] | ./ancestor::div[contains(@class,'prodItem')]"));

                            // 공통지원금 추출: <li class="discount"><div class="tit">공통지원금</div><div
                            // class="conts">500,000원</div></li>
                            WebElement subsidyElement = parentCard.findElement(By.cssSelector("li.discount .conts"));
                            String subsidyText = subsidyElement.getText().trim();
                            String subsidyAmount = subsidyText.replaceAll("[^0-9]", "");

                            if (!subsidyAmount.isEmpty()) {
                                log.info(">>> [데이터 발견] {} | {} | {}원", deviceFullName, planName, subsidyAmount);
                                syncSubsidy(makerName, deviceFullName, planName, planPrice, subsidyAmount,
                                        existingSubsidies);
                            }
                        } catch (Exception e) {
                            log.debug(">>> {} 기기 지원금 추출 실패: {}", deviceFullName, e.getMessage());
                        }
                        break; // 매칭된 targetDevice 찾았으면 다음 기기로
                    }
                }
            }
        } catch (Exception e) {
            log.warn(">>> 기기별 지원금 추출 중 오류: {}", e.getMessage());
        }
    }
}
