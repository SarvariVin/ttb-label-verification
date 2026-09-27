package gov.ttb.labelverification.domain;

import gov.ttb.labelverification.regulatory.FieldName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Specialist-maintained synonym whitelist, checked before fuzzy matching. */
@Entity
@Table(name = "accepted_variants")
public class AcceptedVariant extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "field_name", nullable = false)
    private FieldName fieldName;

    @Column(name = "canonical_value", nullable = false)
    private String canonicalValue;

    @Column(name = "variant_value", nullable = false)
    private String variantValue;

    protected AcceptedVariant() {
    }

    public AcceptedVariant(FieldName fieldName, String canonicalValue, String variantValue) {
        this.fieldName = fieldName;
        this.canonicalValue = canonicalValue;
        this.variantValue = variantValue;
    }

    public FieldName getFieldName() {
        return fieldName;
    }

    public String getCanonicalValue() {
        return canonicalValue;
    }

    public String getVariantValue() {
        return variantValue;
    }
}
