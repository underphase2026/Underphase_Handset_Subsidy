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

    /**
     * 전체 지역 크롤링 실행
     */
    public void crawlCertifiedDealers() {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
        JavascriptExecutor js = (JavascriptExecutor) driver;

        // 수집 순서: 경남 -> 울산 -> 부산
        String[] targetRegions = {"경상남도", "울산광역시", "부산광역시"};

        try {
            dealerRepository.deleteAll(); // 시작 시 초기화

            for (String region : targetRegions) {
                System.out.println("\n========================================");
                System.out.println(">>> [" + region + "] 수집 시작...");
                System.out.println("========================================");

                // 1. 매 지역 시작 시 페이지 새로고침하여 상태 초기화
                driver.get("https://ictmarket.or.kr:8443/find/find_01.do");
                Thread.sleep(2000);

                // 2. 지역 선택 및 검색
                WebElement sidoSelect = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("SIDO_CD")));
                new Select(sidoSelect).selectByValue(region);
                Thread.sleep(1000);

                WebElement searchBtn = wait.until(ExpectedConditions.elementToBeClickable(
                        By.xpath("//button[contains(@onclick, 'getAgentListNM()')]")
                ));
                js.executeScript("arguments[0].click();", searchBtn);

                // 3. 페이지네이션 루프
                boolean hasNext = true;
                int pageCount = 1;
                int lastPageNum = 0; // 무한 루프 방지용 변수

                while (hasNext) {
                    System.out.print(">>> " + region + " [" + pageCount + "] 페이지 수집 중... ");

                    // 데이터 로딩 대기
                    wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.className("form01")));
                    Thread.sleep(1500);

                    // 현재 페이지 데이터 저장
                    int savedCount = parseAndSaveResults(region);

                    // 현재 페이지 번호 가져오기
                    int currentPageNum = getCurrentPageNumber();
                    System.out.println("성공 (" + savedCount + "건 저장 / 현재 페이지: " + currentPageNum + ")");

                    // [핵심] 페이지 번호가 전과 같다면 마지막 페이지임
                    if (pageCount > 1 && currentPageNum == lastPageNum) {
                        System.out.println(">>> [" + region + "] 모든 페이지 수집 완료. 다음 지역으로 이동합니다.");
                        break;
                    }
                    lastPageNum = currentPageNum;

                    // 다음 페이지로 이동 시도
                    hasNext = goToNextPage(wait, js);
                    pageCount++;

                    // 안전을 위한 강제 종료 조건 (경남 115페이지 등 비정상 상황 대비)
                    if (pageCount > 500) break;
                }
                System.out.println(">>> [" + region + "] 구역 종료.");
                Thread.sleep(3000); // 지역 전환 시 세션 안정화를 위해 길게 대기
            }
            System.out.println("\n>>> [전체 완료] 모든 지역 수집이 끝났습니다!");

        } catch (Exception e) {
            System.err.println(">>> 크롤링 중 오류: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Transactional
    protected int parseAndSaveResults(String region) {
        List<WebElement> rows = driver.findElements(By.cssSelector("div.form01.text-center"));
        int count = 0;
        for (WebElement row : rows) {
            try {
                String dealerName = row.findElement(By.cssSelector(".col-md-3")).getText().trim();
                String address = row.findElement(By.cssSelector(".col-md-6")).getText().trim();

                if (!dealerName.isEmpty() && !address.isEmpty()) {
                    if (!dealerRepository.existsByDealerNameAndAddress(dealerName, address)) {
                        dealerRepository.save(CertifiedDealer.builder()
                                .sido(region)
                                .dealerName(dealerName)
                                .address(address)
                                .build());
                        count++;
                    }
                }
            } catch (Exception ignored) {}
        }
        return count;
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

            // 1. 다음 숫자 버튼 찾기
            List<WebElement> pageLinks = driver.findElements(By.cssSelector("p.pageNum a"));
            for (WebElement link : pageLinks) {
                if (link.getText().trim().equals(String.valueOf(nextPage))) {
                    js.executeScript("arguments[0].click();", link);
                    return true;
                }
            }

            // 2. 숫자가 없으면 '다음페이지(>)' 화살표 버튼 찾기
            try {
                WebElement nextBtnImg = driver.findElement(By.cssSelector("p.pageNum a img[alt='다음페이지']"));
                WebElement nextLink = nextBtnImg.findElement(By.xpath(".."));
                js.executeScript("arguments[0].click();", nextLink);
                Thread.sleep(2000); // 화살표 클릭 후 로딩 시간 충분히 부여
                return true;
            } catch (NoSuchElementException e) {
                return false; // 진짜 마지막 페이지
            }
        } catch (Exception e) {
            return false;
        }
    }
}