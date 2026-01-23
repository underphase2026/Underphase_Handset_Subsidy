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

    @Transactional
    public void crawlCertifiedDealers() {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
        JavascriptExecutor js = (JavascriptExecutor) driver;

        String[] targetRegions = {"경상남도", "울산광역시", "부산광역시"};

        try {
            dealerRepository.deleteAll(); // 기존 데이터 초기화
            driver.get("https://ictmarket.or.kr:8443/find/find_01.do");

            for (String region : targetRegions) {
                System.out.println("\n>>> [" + region + "] 지역 수집 시작...");

                // 1. 지역 선택 및 검색 실행
                WebElement sidoSelect = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("SIDO_CD")));
                new Select(sidoSelect).selectByValue(region);
                Thread.sleep(1000);

                WebElement searchBtn = wait.until(ExpectedConditions.elementToBeClickable(
                        By.xpath("//button[contains(@onclick, 'getAgentListNM()')]")
                ));
                js.executeScript("arguments[0].click();", searchBtn);

                // 2. 페이지네이션 루프 시작
                boolean hasNext = true;
                int pageCount = 1;

                while (hasNext) {
                    System.out.print(">>> " + region + " [" + pageCount + "] 페이지 수집 중... ");

                    // 데이터 로딩 대기
                    wait.until(ExpectedConditions.presenceOfElementLocated(By.className("form01")));
                    Thread.sleep(1000);

                    // 현재 페이지 데이터 파싱 및 저장
                    parseAndSaveResults(region);
                    System.out.println("완료");

                    // 3. 다음 페이지로 이동 시도
                    hasNext = goToNextPage(wait, js);
                    pageCount++;
                }
            }
            System.out.println("\n>>> [전수 수집 성공] 모든 지역의 데이터를 저장했습니다!");

        } catch (Exception e) {
            System.err.println(">>> 크롤링 중 오류: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean goToNextPage(WebDriverWait wait, JavascriptExecutor js) {
        try {
            // 현재 페이지 번호 찾기 (<b>1</b> 형태)
            WebElement currentPageElem = driver.findElement(By.cssSelector("p.pageNum b"));
            int currentPage = Integer.parseInt(currentPageElem.getText().trim());
            int nextPage = currentPage + 1;

            // 1) 다음 숫자가 있는지 확인 (예: 1페이지 수집 후 2번 링크 클릭)
            List<WebElement> pageLinks = driver.findElements(By.cssSelector("p.pageNum a"));
            for (WebElement link : pageLinks) {
                if (link.getText().trim().equals(String.valueOf(nextPage))) {
                    js.executeScript("arguments[0].click();", link);
                    Thread.sleep(1500);
                    return true;
                }
            }

            // 2) 숫자가 없다면 '다음페이지' 화살표(>) 버튼이 있는지 확인
            try {
                WebElement nextBtnImg = driver.findElement(By.cssSelector("p.pageNum a img[alt='다음페이지']"));
                WebElement nextLink = nextBtnImg.findElement(By.xpath("..")); // 부모 <a> 태그 선택
                js.executeScript("arguments[0].click();", nextLink);
                Thread.sleep(1500);
                return true;
            } catch (NoSuchElementException e) {
                // 다음 버튼도 없으면 끝
            }

        } catch (Exception e) {
            // 페이지네이션 요소를 찾을 수 없는 경우
        }
        return false;
    }

    private void parseAndSaveResults(String region) {
        List<WebElement> rows = driver.findElements(By.cssSelector("div.form01.text-center"));
        for (WebElement row : rows) {
            try {
                // 유통점명 (col-md-3), 주소 (col-md-6) 추출
                String dealerName = row.findElement(By.cssSelector(".col-md-3")).getText().trim();
                String address = row.findElement(By.cssSelector(".col-md-6")).getText().trim();

                if (!dealerName.isEmpty() && !address.isEmpty()) {
                    dealerRepository.save(CertifiedDealer.builder()
                            .sido(region)
                            .dealerName(dealerName)
                            .address(address)
                            .build());
                }
            } catch (Exception ignored) {}
        }
    }
}