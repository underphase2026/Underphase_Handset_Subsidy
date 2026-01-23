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
     * ICT 마켓 인증판매점 크롤링 메인 로직
     */
    @Transactional
    public void crawlCertifiedDealers() {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
        JavascriptExecutor js = (JavascriptExecutor) driver;

        // 수집 대상 지역 정의
        String[] targetRegions = {"경상남도", "울산광역시", "부산광역시"};

        try {
            // 1. 기존 데이터 초기화 (새로운 수집 시작 전)
            dealerRepository.deleteAll();

            for (String region : targetRegions) {
                // [핵심 수정] 지역 이동 시 브라우저 상태를 초기화하기 위해 페이지를 새로 불러옵니다.
                driver.get("https://ictmarket.or.kr:8443/find/find_01.do");

                System.out.println("\n>>> [" + region + "] 지역 수집 시작...");

                // 2. 광역시/도 선택 (SIDO_CD)
                WebElement sidoSelect = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("SIDO_CD")));
                new Select(sidoSelect).selectByValue(region);
                Thread.sleep(1000); // 선택 후 드롭다운 반영 대기

                // 3. 검색 버튼 클릭 (getAgentListNM 함수 호출)
                WebElement searchBtn = wait.until(ExpectedConditions.elementToBeClickable(
                        By.xpath("//button[contains(@onclick, 'getAgentListNM()')]")
                ));
                js.executeScript("arguments[0].click();", searchBtn);

                // 4. 페이지네이션 루프 실행
                boolean hasNext = true;
                int pageCount = 1;

                while (hasNext) {
                    System.out.print(">>> " + region + " [" + pageCount + "] 페이지 수집 중... ");

                    // 결과 행(form01)이 나타날 때까지 대기
                    wait.until(ExpectedConditions.presenceOfElementLocated(By.className("form01")));
                    Thread.sleep(1000); // 동적 데이터 렌더링을 위한 여유 시간

                    // 현재 페이지 데이터 추출 및 DB 저장
                    parseAndSaveResults(region);
                    System.out.println("완료");

                    // 다음 페이지로 이동 시도
                    hasNext = goToNextPage(wait, js);
                    pageCount++;
                }

                // 한 지역 수집 완료 후 잠시 대기
                Thread.sleep(2000);
            }
            System.out.println("\n>>> [수집 성공] 모든 지역의 데이터를 MariaDB에 저장했습니다!");

        } catch (Exception e) {
            System.err.println(">>> 크롤링 중 치명적 오류 발생: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 현재 페이지의 데이터(유통점명, 주소)를 파싱하여 저장
     */
    private void parseAndSaveResults(String region) {
        List<WebElement> rows = driver.findElements(By.cssSelector("div.form01.text-center"));

        for (WebElement row : rows) {
            try {
                // 유통점명 추출 (col-md-3)
                String dealerName = row.findElement(By.cssSelector(".col-md-3")).getText().trim();
                // 주소 추출 (col-md-6)
                String address = row.findElement(By.cssSelector(".col-md-6")).getText().trim();

                if (!dealerName.isEmpty() && !address.isEmpty()) {
                    dealerRepository.save(CertifiedDealer.builder()
                            .sido(region)
                            .dealerName(dealerName)
                            .address(address)
                            .build());
                }
            } catch (NoSuchElementException ignored) {
                // 헤더나 빈 행은 무시합니다.
            }
        }
    }

    /**
     * 다음 페이지 번호를 찾아 클릭하거나 '다음페이지' 버튼을 클릭
     */
    private boolean goToNextPage(WebDriverWait wait, JavascriptExecutor js) {
        try {
            // 현재 활성화된 페이지 번호(<b>태그) 확인
            WebElement currentPageElem = driver.findElement(By.cssSelector("p.pageNum b"));
            int currentPage = Integer.parseInt(currentPageElem.getText().trim());
            int nextPage = currentPage + 1;

            // 1) 다음 숫자 버튼이 있는지 확인 (예: 2, 3, 4...)
            List<WebElement> pageLinks = driver.findElements(By.cssSelector("p.pageNum a"));
            for (WebElement link : pageLinks) {
                if (link.getText().trim().equals(String.valueOf(nextPage))) {
                    js.executeScript("arguments[0].click();", link);
                    Thread.sleep(1500);
                    return true;
                }
            }

            // 2) 숫자가 없다면 '다음페이지' 화살표 이미지 버튼 확인
            try {
                WebElement nextBtnImg = driver.findElement(By.cssSelector("p.pageNum a img[alt='다음페이지']"));
                WebElement nextLink = nextBtnImg.findElement(By.xpath("..")); // 부모 <a> 태그
                js.executeScript("arguments[0].click();", nextLink);
                Thread.sleep(1500);
                return true;
            } catch (NoSuchElementException e) {
                // 더 이상 다음 페이지가 없음
            }

        } catch (Exception e) {
            // 페이지네이션 요소를 찾을 수 없는 경우 루프 종료
        }
        return false;
    }
}