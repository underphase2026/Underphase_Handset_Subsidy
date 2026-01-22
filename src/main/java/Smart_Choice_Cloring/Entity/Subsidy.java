package Smart_Choice_Cloring.Entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.logging.Logger;

@Entity
@Getter
@NoArgsConstructor
public class Subsidy {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String telecom;      // 통신사 (SKT, KT, LG U+)
    private String deviceName;   // 단말기 명
    private String planName;     // 요금제 명
    private String supportAmount; // 공시지원금

    private LocalDateTime updatedAt; // 데이터 수집 시간

    @Builder
    public Subsidy(String telecom, String deviceName, String planName, String supportAmount) {
        this.telecom = telecom;
        this.deviceName = deviceName;
        this.planName = planName;
        this.supportAmount = supportAmount;
        this.updatedAt = LocalDateTime.now();
    }
}
