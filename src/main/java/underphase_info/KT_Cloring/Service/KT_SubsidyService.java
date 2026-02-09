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

    // 취급 단말기 목록 (iPhone)
    private static final List<String> IPHONE_MODELS = Arrays.asList(
            "아이폰 17 Pro Max", "아이폰 17 Pro", "아이폰 17 Air", "아이폰 17",
            "아이폰 16 Pro Max", "아이폰 16 Max", "아이폰 16 Plus", "아이폰 16");

    // 취급 단말기 목록 (Galaxy)
    private static final List<String> GALAXY_MODELS = Arrays.asList(
            "갤럭시 Z 폴드7", "갤럭시 Z 플립7",
            "갤럭시 S25 울트라", "갤럭시 S25 엣지", "갤럭시 S25+", "갤럭시 S25");

    // 취급 요금제 목록
    private static final List<String> TARGET_PLANS = Arrays.asList(
            "5G 초이스 프리미엄", "5G 초이스 스페셜", "5G 초이스 베이직",
            "5G 베이직", "5G 심플 100GB", "5G 슬림 14GB");

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

                log.info("\n========== [제조사: {}] 크롤링 시작 ==========", makerName);

                selectDropdown(driver, "makrCd", makerCode);
                Thread.sleep(2000);

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
}
