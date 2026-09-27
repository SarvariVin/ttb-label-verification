package gov.ttb.labelverification.repository;

import gov.ttb.labelverification.domain.ValidationResult;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ValidationResultRepository extends JpaRepository<ValidationResult, String> {

    Optional<ValidationResult> findFirstByLabelIdAndCurrentTrueOrderByCreatedAtDesc(String labelId);

    List<ValidationResult> findByLabelIdOrderByCreatedAtDesc(String labelId);
}
