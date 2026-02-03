package underphase_info.Smart_Choice_Cloring.Service;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebDriverConfig {

    @Bean
    public ChromeOptions chromeOptions() {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();

        // 1. 최신 Headless 모드 강제 적용 (창 띄움 방지 강화)
        options.addArguments("--headless=new");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");

        // 2. 메모리 최적화 및 팝업 방지
        options.addArguments("--disable-gpu");
        options.addArguments("--blink-settings=imagesEnabled=false");
        options.addArguments("--disable-popup-blocking"); // 팝업 차단으로 리소스 보호
        options.addArguments("--memory-pressure-off"); // 메모리 압박 시 강제 종료 방지

        // 3. User-Agent 설정 (서버가 자동화 도구를 더 잘 받아들이도록 함)
        options.addArguments(
                "--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        return options;
    }
}