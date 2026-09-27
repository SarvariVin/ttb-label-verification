package gov.ttb.labelverification.repository;

import gov.ttb.labelverification.domain.LabelImage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabelImageRepository extends JpaRepository<LabelImage, String> {

    List<LabelImage> findByLabelIdOrderBySortOrderAsc(String labelId);
}
