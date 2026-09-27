package gov.ttb.labelverification.repository;

import gov.ttb.labelverification.domain.ValidationItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ValidationItemRepository extends JpaRepository<ValidationItem, String> {

    List<ValidationItem> findByValidationResultIdOrderByFieldNameAsc(String validationResultId);

    /** Items of the label's current validation result. */
    @Query("""
            select i from ValidationItem i
            where i.validationResult.label.id = :labelId and i.validationResult.current = true
            order by i.fieldName""")
    List<ValidationItem> findCurrentForLabel(String labelId);
}
