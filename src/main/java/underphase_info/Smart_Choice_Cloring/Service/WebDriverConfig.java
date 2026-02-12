package underphase_info.Smart_Choice_Cloring.Service;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebDriverConfig {

    @Bean
    public ChromeOptions chromeOptions() {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();

        // 1. Headless 모드 (서버 배포 시 필수)
        options.addArguments("--headless=new");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");

        // 2. 메모리 최적화 및 팝업 방지
        options.addArguments("--disable-gpu");
        options.addArguments("--blink-settings=imagesEnabled=false");
        options.addArguments("--disable-popup-blocking");
        options.addArguments("--memory-pressure-off");

        // 3. User-Agent 설정
        options.addArguments(
                "--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        return options;
    }

    // WebDriver Bean 제거함 - 각 서비스에서 필요할 때만 생성하고 종료하는 방식으로 변경
    // 이렇게 하면 앱 시작 시 브라우저가 뜨지 않음
}