package underphase_info.Smart_Choice_Cloring.Repository;
import underphase_info.Smart_Choice_Cloring.Entity.Subsidy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubsidyRepository extends JpaRepository<Subsidy, Long> {
    // 모든 필드가 일치하는 데이터가 있는지 확인
    boolean existsByMakerAndDeviceNameAndPlanNameAndPlanRangeAndSupportTypeAndTelecom(
            String maker, String deviceName, String planName, String planRange, String supportType, String telecom
    );
}