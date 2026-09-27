package gov.ttb.labelverification.repository;

import gov.ttb.labelverification.domain.StatusOverride;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatusOverrideRepository extends JpaRepository<StatusOverride, String> {

    @EntityGraph(attributePaths = {"specialist"})
    List<StatusOverride> findByLabelIdOrderByCreatedAtDesc(String labelId);
}
