package underphase_info.ICT_Market_Cloring.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import underphase_info.ICT_Market_Cloring.Entity.CertifiedDealer;

public interface CertifiedDealerRepository extends JpaRepository<CertifiedDealer, Long> {
    boolean existsByDealerNameAndAddress(String dealerName, String address);
}