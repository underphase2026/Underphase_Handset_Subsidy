package underphase_info.Smart_Choice_Cloring.Repository;
import underphase_info.Smart_Choice_Cloring.Entity.Subsidy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubsidyRepository extends JpaRepository<Subsidy, Long> {
    boolean existsByDeviceName(String deviceName);

    boolean existsByMakerAndDeviceNameAndPlanNameAndPlanRangeAndSupportTypeAndTelecom(
            String maker, String deviceName, String planName, String planRange, String supportType, String telecom
    );
}