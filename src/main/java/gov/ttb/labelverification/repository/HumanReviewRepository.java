package gov.ttb.labelverification.repository;

import gov.ttb.labelverification.domain.HumanReview;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HumanReviewRepository extends JpaRepository<HumanReview, String> {

    @EntityGraph(attributePaths = {"specialist", "validationItem"})
    List<HumanReview> findByLabelIdOrderByReviewedAtDesc(String labelId);
}
