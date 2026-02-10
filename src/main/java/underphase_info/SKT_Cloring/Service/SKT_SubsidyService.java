package underphase_info.SKT_Cloring.Service;

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
public class SKT_SubsidyService {

    private final SubsidyRepository subsidyRepository;
    private final ChromeOptions chromeOptions;

    private static final String TARGET_URL = "https://shop.tworld.co.kr/notice";

    // 취급 단말기 목록 (iPhone)
    private static final List<String> IPHONE_MODELS = Arrays.asList(
            "iPhone 17 Pro Max", "iPhone 17 Pro", "iPhone 17 Air", "iPhone 17",
            "iPhone 16 Pro Max", "iPhone 16 Pro", "iPhone 16 Plus", "iPhone 16");

    // 취급 단말기 목록 (Galaxy)
    private static final List<String> GALAXY_MODELS = Arrays.asList(
            "갤럭시 Z Fold7", "갤럭시 Z Flip7",
            "갤럭시 S25 Ultra", "갤럭시 S25 엣지", "갤럭시 S25+", "갤럭시 S25");

    // 취급 요금제 목록
    private static final List<String> TARGET_PLANS = Arrays.asList(
            "5GX 플래티넘", "5GX 프리미엄", "5GX 프라임플러스",
            "5GX 프라임", "5GX 레귤러", "슬림");

    @Transactional
    public void crawlSktSubsidies() {
        log.info(">>> [SKT 크롤러 시작] T world 공시지원금 크롤링을 시작합니다.");

        WebDriver driver = new ChromeDriver(chromeOptions);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
        JavascriptExecutor js = (JavascriptExecutor) driver;

        try {
            Map<String, Subsidy> existingSubsidies = loadExistingSktSubsidies();
            log.info(">>> 기존 SKT 데이터 {}건 로드 완료", existingSubsidies.size());

            driver.get(TARGET_URL);
            Thread.sleep(3000);

            // 1. 가입유형 선택: 신규가입 (TODO: 드롭다운 클릭 필요)
            selectSubscriptionType(driver, wait, js, "신규가입");
            Thread.sleep(2000);

            // 2. 요금제별 크롤링
            for (String targetPlan : TARGET_PLANS) {
                log.info("\n>>> [요금제: {}] 처리 시작", targetPlan);

                try {
                    // 2-1. 요금제 선택 (팝업 열기 → 카테고리 → 요금제 선택 → 확인)
                    boolean planFound = selectPlanByName(driver, wait, js, targetPlan);
                    if (!planFound) {
                        log.warn(">>> [{}] 요금제를 찾을 수 없습니다", targetPlan);
                        closePopupIfOpen(driver, js);
                        continue;
                    }
                    Thread.sleep(2000);

                    // 2-3. 요금제 가격 추출
                    String planPrice = extractPlanPrice(driver);
                    log.info(">>> 요금제 가격: {}", planPrice);

                    // 3. 기기 검색 및 지원금 추출
                    List<String> allDevices = new ArrayList<>();
                    allDevices.addAll(IPHONE_MODELS);
                    allDevices.addAll(GALAXY_MODELS);

                    for (String device : allDevices) {
                        extractDeviceSubsidy(driver, wait, js, device, targetPlan, planPrice, existingSubsidies);
                    }

                } catch (Exception e) {
                    log.warn(">>> [{}] 요금제 처리 실패: {}", targetPlan, e.getMessage());
                }
            }

            log.info(">>> [SKT 크롤러 완료]");

        } catch (Exception e) {
            log.error("SKT 크롤링 중 오류 발생", e);
        } finally {
            driver.quit();
        }
    }

    /**
     * 가입유형 선택 (신규가입)
     */
    private void selectSubscriptionType(WebDriver driver, WebDriverWait wait, JavascriptExecutor js, String type) {
        try {
            // 가입유형 드롭다운 클릭
            WebElement typeDropdown = wait.until(ExpectedConditions.elementToBeClickable(
                    By.id("select2-scrb-typ-cd-container")));
            js.executeScript("arguments[0].click();", typeDropdown);
            Thread.sleep(500);

            // 신규가입 옵션 선택
            List<WebElement> options = driver.findElements(By.cssSelector(".select2-results__option"));
            for (WebElement option : options) {
                if (option.getText().contains(type)) {
                    js.executeScript("arguments[0].click();", option);
                    log.info(">>> 가입유형 선택: {}", type);
                    break;
                }
            }
            Thread.sleep(1000);
        } catch (Exception e) {
            log.warn(">>> 가입유형 선택 실패: {}", e.getMessage());
        }
    }

    /**
     * 요금제 선택 (select-fee 클릭 → 팝업 창 전환 → 카테고리 선택 → 요금제 선택 → 확인 → 원래 창 복귀)
     */
    private boolean selectPlanByName(WebDriver driver, WebDriverWait wait, JavascriptExecutor js, String planName) {
        String originalWindow = driver.getWindowHandle();
        try {
            Set<String> beforeHandles = driver.getWindowHandles();

            // 1. select-fee 버튼 클릭하여 팝업 열기
            WebElement feeButton = wait.until(ExpectedConditions.elementToBeClickable(By.id("select-fee")));

            // 시도 1: 네이티브 클릭
            feeButton.click();
            Thread.sleep(2000);

            Set<String> afterHandles = driver.getWindowHandles();

            // 시도 2: JS 클릭
            if (afterHandles.size() <= beforeHandles.size()) {
                log.info(">>> 네이티브 클릭으로 팝업 안 열림, JS 클릭 시도");
                js.executeScript("arguments[0].click();", feeButton);
                Thread.sleep(2000);
                afterHandles = driver.getWindowHandles();
            }

            // 시도 3: window.open으로 직접 열기
            if (afterHandles.size() <= beforeHandles.size()) {
                log.info(">>> JS 클릭으로도 팝업 안 열림, window.open으로 직접 열기");
                js.executeScript(
                        "window.open('https://shop.tworld.co.kr/wireless/product/subscription/list', '_blank');");
                Thread.sleep(2000);
                afterHandles = driver.getWindowHandles();
            }

            // 새 창으로 전환
            String popupHandle = null;
            for (String handle : afterHandles) {
                if (!beforeHandles.contains(handle)) {
                    popupHandle = handle;
                    break;
                }
            }

            if (popupHandle == null) {
                log.warn(">>> 요금제 선택 팝업 창을 열 수 없습니다");
                return false;
            }

            driver.switchTo().window(popupHandle);
            log.info(">>> 요금제 선택 팝업 창으로 전환 완료 (URL: {})", driver.getCurrentUrl());
            Thread.sleep(2000);

            // 2. 카테고리 선택 (e.g. "#5G 5GX플랜")
            String targetCategory = getCategoryForPlan(planName);
            WebElement categoryList = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.id("subscriptionCategory")));
            List<WebElement> categoryItems = categoryList.findElements(
                    By.cssSelector("li.type-item a.link-block"));
            log.info(">>> 찾은 카테고리 수: {}", categoryItems.size());

            boolean categoryClicked = false;
            for (WebElement categoryLink : categoryItems) {
                String text = categoryLink.getText().trim();
                log.info(">>> 카테고리: {}", text);
                if (text.equals(targetCategory)) {
                    js.executeScript("arguments[0].click();", categoryLink);
                    log.info(">>> [카테고리 클릭] {}", text);
                    Thread.sleep(2000);
                    categoryClicked = true;
                    break;
                }
            }

            if (!categoryClicked) {
                log.warn(">>> 카테고리 '{}' 를 찾을 수 없음", targetCategory);
                closePopupWindow(driver, originalWindow);
                return false;
            }

            // 3. 요금제 목록에서 해당 요금제 찾기 (li.charge-item 내 span.sub-tit)
            Thread.sleep(1000);
            List<WebElement> planItems = driver.findElements(By.cssSelector("li.charge-item"));
            log.info(">>> 찾은 요금제 수: {}", planItems.size());

            for (WebElement planItem : planItems) {
                try {
                    WebElement subTit = planItem.findElement(By.cssSelector("span.sub-tit"));
                    String planText = subTit.getText().trim();
                    log.info(">>> 요금제 항목: '{}' (검색: '{}')", planText, planName);

                    if (planText.contains(planName)) {
                        // 더 긴 요금제명 매칭 방지 (예: "5GX 프라임" 검색 시 "5GX 프라임 플러스" 제외)
                        if (hasLongerPlanMatch(planText, planName)) {
                            log.info(">>> 더 긴 요금제 매칭으로 스킵: {}", planText);
                            continue;
                        }

                        // 스크롤 후 라디오 버튼 클릭
                        js.executeScript("arguments[0].scrollIntoView({block: 'center'});", planItem);
                        Thread.sleep(300);

                        WebElement radio = planItem.findElement(By.cssSelector("input[type='radio']"));
                        js.executeScript("arguments[0].click();", radio);
                        log.info(">>> [요금제 선택] {}", planText);
                        Thread.sleep(500);

                        // 4. "요금제 선택" 버튼 클릭
                        WebElement confirmBtn = wait.until(
                                ExpectedConditions.elementToBeClickable(By.id("confirm")));
                        js.executeScript("arguments[0].click();", confirmBtn);
                        log.info(">>> [요금제 선택 버튼 클릭]");
                        Thread.sleep(2000);

                        // 5. 팝업 창 닫히고 원래 창으로 복귀
                        closePopupWindow(driver, originalWindow);
                        return true;
                    }
                } catch (Exception e) {
                    log.warn(">>> 요금제 항목 처리 중 오류: {}", e.getMessage());
                    continue;
                }
            }

            log.warn(">>> 요금제 '{}' 를 목록에서 찾을 수 없음", planName);
            closePopupWindow(driver, originalWindow);
            return false;

        } catch (Exception e) {
            log.warn(">>> 요금제 선택 중 오류: {}", e.getMessage());
            closePopupWindow(driver, originalWindow);
            return false;
        }
    }

    /**
     * 요금제명에 따른 카테고리 반환 (현재 취급 요금제는 모두 #5G 5GX플랜)
     */
    private String getCategoryForPlan(String planName) {
        return "#5G 5GX플랜";
    }

    /**
     * 더 긴 요금제명이 매칭되는지 확인 (예: "5GX 프라임" 검색 시 "5GX 프라임 플러스" 제외)
     */
    private boolean hasLongerPlanMatch(String planText, String currentPlanName) {
        for (String otherPlan : TARGET_PLANS) {
            if (!otherPlan.equals(currentPlanName)
                    && otherPlan.contains(currentPlanName)
                    && planText.contains(otherPlan)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 팝업 창 닫고 원래 창으로 복귀
     */
    private void closePopupWindow(WebDriver driver, String originalWindow) {
        try {
            if (!driver.getWindowHandle().equals(originalWindow)) {
                driver.close();
                driver.switchTo().window(originalWindow);
                log.info(">>> 원래 창으로 복귀 완료");
            }
        } catch (Exception e) {
            try {
                driver.switchTo().window(originalWindow);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 기기별 지원금 추출
     */
    private void extractDeviceSubsidy(WebDriver driver, WebDriverWait wait, JavascriptExecutor js,
            String deviceName, String planName, String planPrice, Map<String, Subsidy> existingSubsidies) {
        try {
            // 검색창에 기기명 입력
            WebElement searchInput = driver.findElement(By.id("companyNmOrModelName"));
            searchInput.clear();
            searchInput.sendKeys(deviceName);

            // 검색 버튼 클릭
            WebElement searchBtn = driver.findElement(By.id("search-name"));
            js.executeScript("arguments[0].click();", searchBtn);
            Thread.sleep(2000);

            // 공통지원금 추출: td.twoLine 내의 span.num
            List<WebElement> subsidyElements = driver.findElements(By.cssSelector("td.twoLine .price .num"));

            if (!subsidyElements.isEmpty()) {
                String subsidyAmount = subsidyElements.get(0).getText().replaceAll("[^0-9]", "");
                if (!subsidyAmount.isEmpty()) {
                    String maker = deviceName.toLowerCase().contains("iphone") ? "Apple" : "삼성";
                    log.info(">>> [데이터 발견] {} | {} | {}원", deviceName, planName, subsidyAmount);
                    syncSubsidy(maker, deviceName, planName, planPrice, subsidyAmount, existingSubsidies);
                }
            } else {
                log.debug(">>> {} - 지원금 데이터 없음", deviceName);
            }

        } catch (Exception e) {
            log.debug(">>> {} 기기 지원금 추출 실패: {}", deviceName, e.getMessage());
        }
    }

    /**
     * 요금제 가격 추출
     */
    private String extractPlanPrice(WebDriver driver) {
        try {
            WebElement priceElement = driver.findElement(By.cssSelector("#select-fee .text"));
            return priceElement.getText().trim();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 팝업 닫기
     */
    private void closePopupIfOpen(WebDriver driver, JavascriptExecutor js) {
        try {
            List<WebElement> closeButtons = driver.findElements(By.cssSelector(".btn-close, .close, .popup-close"));
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
     * 기존 SKT 데이터 로드
     */
    private Map<String, Subsidy> loadExistingSktSubsidies() {
        return subsidyRepository.findAll().stream()
                .filter(s -> "SKT".equals(s.getTelecom()))
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
                    .maker(maker).deviceName(deviceName).telecom("SKT")
                    .planName(planName).planRange(planRange).supportType("신규가입").supportAmount(amount)
                    .crawledAt(LocalDateTime.now()).build());
        }
    }
}
