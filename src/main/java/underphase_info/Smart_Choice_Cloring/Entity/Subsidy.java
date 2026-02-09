package underphase_info.Smart_Choice_Cloring.Entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Subsidy {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String maker;
    private String deviceName;
    private String telecom;
    private String planName;
    private String planRange;
    private String supportType;
    private String supportAmount;

    @Builder.Default
    private LocalDateTime crawledAt = LocalDateTime.now();

    public void updateAmount(String newAmount) {
        this.supportAmount = newAmount;
        this.crawledAt = LocalDateTime.now();
    }
}