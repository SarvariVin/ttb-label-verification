package gov.ttb.labelverification.repository;

import gov.ttb.labelverification.domain.Applicant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicantRepository extends JpaRepository<Applicant, String> {

    List<Applicant> findAllByOrderByCompanyNameAsc();

    Optional<Applicant> findFirstByCompanyNameIgnoreCase(String companyName);
}
