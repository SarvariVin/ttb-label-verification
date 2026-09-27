package gov.ttb.labelverification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Key/value runtime setting; value is JSON. */
@Entity
@Table(name = "settings")
public class Setting extends BaseEntity {

    @Column(name = "setting_key", nullable = false, unique = true)
    private String key;

    @Column(name = "setting_value", nullable = false, columnDefinition = "TEXT")
    private String value;

    protected Setting() {
    }

    public Setting(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
