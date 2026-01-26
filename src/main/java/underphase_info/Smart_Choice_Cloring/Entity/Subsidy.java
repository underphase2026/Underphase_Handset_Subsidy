package underphase_info.Smart_Choice_Cloring.Entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Subsidy {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String maker;         // 제조사
    private String deviceName;    // 기기명
    private String telecom;       // 통신사
    private String planName;      // [수정] 요금제 이름 (예: 컴팩트, 슬림)
    private String planRange;     // 요금제 구간 (예: 10만원 이상)
    private String supportType;   // 지원 유형 (번호이동 / 기기변경)
    private String supportAmount; // 지원금

    @Builder.Default
    private LocalDateTime crawledAt = LocalDateTime.now();
}