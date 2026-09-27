package gov.ttb.labelverification.repository;

import gov.ttb.labelverification.domain.Label;
import gov.ttb.labelverification.domain.LabelStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LabelRepository extends JpaRepository<Label, String> {

    @EntityGraph(attributePaths = {"applicant", "applicationData"})
    @Query("select l from Label l where l.id = :id")
    Optional<Label> findDetailedById(String id);

    @EntityGraph(attributePaths = {"applicant", "applicationData"})
    List<Label> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"applicant", "applicationData"})
    List<Label> findByApplicantIdOrderByCreatedAtDesc(String applicantId);

    @EntityGraph(attributePaths = {"applicant", "applicationData"})
    List<Label> findByStatusInOrderByCreatedAtAsc(Collection<LabelStatus> statuses);

    long countByStatus(LabelStatus status);

    long countByApplicantId(String applicantId);
}
