package underphase_info.Smart_Choice_Cloring.Service;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;

@Configuration
public class WebDriverConfig {
    @Bean
    public WebDriver webDriver() {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();

        // 자동화 제어 메시지 숨기기 및 감지 회피
        options.setExperimentalOption("excludeSwitches", Collections.singletonList("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);

        // 현재 Mac 환경이므로 headless는 주석 처리하여 눈으로 확인합니다.
        // options.addArguments("--headless");

        return new ChromeDriver(options);
    }
}