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

        // 1. 기본 Headless 및 리눅스 서버 설정
        options.addArguments("--headless");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");

        // 2. [추가] 메모리 및 CPU 최적화 설정
        options.addArguments("--disable-gpu");            // GPU 가속 비활성화
        options.addArguments("--disable-extensions");     // 확장 프로그램 비활성화
        options.addArguments("--blink-settings=imagesEnabled=false"); // 이미지 로딩 방지 (메모리 절약)
        options.addArguments("--incognito");             // 시크릿 모드 실행 (캐시 누적 방지)

        // 3. 자동화 감지 회피
        options.setExperimentalOption("excludeSwitches", Collections.singletonList("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);

        return new ChromeDriver(options);
    }
}