package gov.ttb.labelverification.repository;

import gov.ttb.labelverification.domain.AcceptedVariant;
import gov.ttb.labelverification.regulatory.FieldName;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcceptedVariantRepository extends JpaRepository<AcceptedVariant, String> {

    List<AcceptedVariant> findByFieldName(FieldName fieldName);
}
