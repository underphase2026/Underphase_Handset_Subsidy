package underphase_info.ICT_Market_Cloring.Service;

import underphase_info.ICT_Market_Cloring.Entity.CertifiedDealer;
import underphase_info.ICT_Market_Cloring.Repository.CertifiedDealerRepository;
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
public class IctMarketService {
    private final CertifiedDealerRepository dealerRepository;
    private final WebDriver driver;

    public void crawlCertifiedDealers() {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
        JavascriptExecutor js = (JavascriptExecutor) driver;

        String[] targetRegions = {"경상남도", "울산광역시", "부산광역시"};

        try {
            // 전체 수집 건수 카운트
            int totalSaved = 0;

            for (String region : targetRegions) {
                System.out.println("\n========================================");
                System.out.println(">>> [" + region + "] 수집 시작...");
                System.out.println("========================================");

                driver.get("https://ictmarket.or.kr:8443/find/find_01.do");
                Thread.sleep(2000);

                WebElement sidoSelect = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("SIDO_CD")));
                new Select(sidoSelect).selectByValue(region);
                Thread.sleep(1000);

                WebElement searchBtn = wait.until(ExpectedConditions.elementToBeClickable(
                        By.xpath("//button[contains(@onclick, 'getAgentListNM()')]")
                ));
                js.executeScript("arguments[0].click();", searchBtn);

                boolean hasNext = true;
                int pageCount = 1;
                int lastPageNum = 0;

                while (hasNext) {
                    System.out.print(">>> " + region + " [" + pageCount + "] 페이지 수집 중... ");

                    // [보강] 데이터 행이 실제로 존재할 때까지 대기
                    wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.className("form01")));
                    Thread.sleep(1500);

                    // 현재 페이지 파싱 및 저장 (건수 반환)
                    int savedInPage = parseAndSaveResults(region);
                    totalSaved += savedInPage;

                    int currentPageNum = getCurrentPageNumber();
                    System.out.println("완료 (" + savedInPage + "건 신규 저장 / 현재 총합: " + totalSaved + ")");

                    // 마지막 페이지 검증
                    if (pageCount > 1 && currentPageNum == lastPageNum) {
                        System.out.println(">>> [" + region + "] 수집 완료.");
                        break;
                    }
                    lastPageNum = currentPageNum;

                    hasNext = goToNextPage(wait, js);
                    pageCount++;
                    if (pageCount > 600) break; // 안전장치
                }
                Thread.sleep(3000);
            }
            System.out.println("\n>>> [최종 완료] 총 " + totalSaved + "건의 새로운 데이터가 추가되었습니다.");

        } catch (Exception e) {
            System.err.println(">>> 치명적 오류: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Transactional
    protected int parseAndSaveResults(String region) {
        // 테이블 내 모든 데이터 행 추출
        List<WebElement> rows = driver.findElements(By.cssSelector("div.form01.text-center"));
        int savedCount = 0;

        for (WebElement row : rows) {
            try {
                // 업체명과 주소 추출
                String dealerName = row.findElement(By.cssSelector(".col-md-3")).getText().trim();
                String address = row.findElement(By.cssSelector(".col-md-6")).getText().trim();

                if (!dealerName.isEmpty() && !address.isEmpty()) {
                    // [중복 체크] 이름과 주소가 모두 같을 때만 스킵
                    if (!dealerRepository.existsByDealerNameAndAddress(dealerName, address)) {
                        dealerRepository.save(CertifiedDealer.builder()
                                .sido(region)
                                .dealerName(dealerName)
                                .address(address)
                                .build());
                        savedCount++;
                    }
                } else {
                    System.err.print("[누락: 데이터 빈칸] ");
                }
            } catch (NoSuchElementException e) {
                // 데이터 행이 아닌 헤더 등은 무시
            } catch (Exception e) {
                System.err.print("[오류: " + e.getMessage() + "] ");
            }
        }
        return savedCount;
    }

    private int getCurrentPageNumber() {
        try {
            return Integer.parseInt(driver.findElement(By.cssSelector("p.pageNum b")).getText().trim());
        } catch (Exception e) {
            return -1;
        }
    }

    private boolean goToNextPage(WebDriverWait wait, JavascriptExecutor js) {
        try {
            int currentPage = getCurrentPageNumber();
            int nextPage = currentPage + 1;

            List<WebElement> pageLinks = driver.findElements(By.cssSelector("p.pageNum a"));
            for (WebElement link : pageLinks) {
                if (link.getText().trim().equals(String.valueOf(nextPage))) {
                    js.executeScript("arguments[0].click();", link);
                    return true;
                }
            }

            try {
                WebElement nextBtnImg = driver.findElement(By.cssSelector("p.pageNum a img[alt='다음페이지']"));
                WebElement nextLink = nextBtnImg.findElement(By.xpath(".."));
                js.executeScript("arguments[0].click();", nextLink);
                Thread.sleep(2500); // 페이지 전환 대기시간 증가
                return true;
            } catch (NoSuchElementException e) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }
}