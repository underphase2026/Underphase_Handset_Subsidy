package underphase_info.ICT_Market_Cloring.Repository;

import underphase_info.ICT_Market_Cloring.Entity.CertifiedDealer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CertifiedDealerRepository extends JpaRepository<CertifiedDealer, Long> {
}