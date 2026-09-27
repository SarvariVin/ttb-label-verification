package gov.ttb.labelverification.domain;

import gov.ttb.labelverification.regulatory.FieldName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/** Mirrors TTB Form 5100.31 — the values the applicant declared for this label. */
@Entity
@Table(name = "application_data")
public class ApplicationData extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "label_id", unique = true)
    private Label label;

    @Column(name = "serial_number")
    private String serialNumber;
    @Column(name = "brand_name")
    private String brandName;
    @Column(name = "fanciful_name")
    private String fancifulName;
    @Column(name = "class_type")
    private String classType;
    @Column(name = "class_type_code")
    private String classTypeCode;
    @Column(name = "alcohol_content")
    private String alcoholContent;
    @Column(name = "net_contents")
    private String netContents;
    @Column(name = "health_warning")
    private String healthWarning;
    @Column(name = "name_and_address")
    private String nameAndAddress;
    @Column(name = "qualifying_phrase")
    private String qualifyingPhrase;
    @Column(name = "country_of_origin")
    private String countryOfOrigin;
    @Column(name = "grape_varietal")
    private String grapeVarietal;
    @Column(name = "appellation_of_origin")
    private String appellationOfOrigin;
    @Column(name = "vintage_year")
    private String vintageYear;
    @Column(name = "sulfite_declaration")
    private Boolean sulfiteDeclaration;
    @Column(name = "age_statement")
    private String ageStatement;
    @Column(name = "state_of_distillation")
    private String stateOfDistillation;
    @Column(name = "fdc_yellow_5")
    private Boolean fdcYellow5;
    @Column(name = "cochineal_carmine")
    private Boolean cochinealCarmine;

    public ApplicationData() {
    }

    /** Declared text value for a comparable field, or null if not provided. */
    public String valueOf(FieldName field) {
        return switch (field) {
            case BRAND_NAME -> brandName;
            case FANCIFUL_NAME -> fancifulName;
            case CLASS_TYPE -> classType;
            case ALCOHOL_CONTENT -> alcoholContent;
            case NET_CONTENTS -> netContents;
            case HEALTH_WARNING -> healthWarning;
            case NAME_AND_ADDRESS -> nameAndAddress;
            case QUALIFYING_PHRASE -> qualifyingPhrase;
            case COUNTRY_OF_ORIGIN -> countryOfOrigin;
            case GRAPE_VARIETAL -> grapeVarietal;
            case APPELLATION_OF_ORIGIN -> appellationOfOrigin;
            case VINTAGE_YEAR -> vintageYear;
            case AGE_STATEMENT -> ageStatement;
            case STATE_OF_DISTILLATION -> stateOfDistillation;
            case SULFITE_DECLARATION -> Boolean.TRUE.equals(sulfiteDeclaration) ? "Contains Sulfites" : null;
            case STANDARDS_OF_FILL -> null;
        };
    }

    public Label getLabel() {
        return label;
    }

    void setLabel(Label label) {
        this.label = label;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getBrandName() {
        return brandName;
    }

    public void setBrandName(String brandName) {
        this.brandName = brandName;
    }

    public String getFancifulName() {
        return fancifulName;
    }

    public void setFancifulName(String fancifulName) {
        this.fancifulName = fancifulName;
    }

    public String getClassType() {
        return classType;
    }

    public void setClassType(String classType) {
        this.classType = classType;
    }

    public String getClassTypeCode() {
        return classTypeCode;
    }

    public void setClassTypeCode(String classTypeCode) {
        this.classTypeCode = classTypeCode;
    }

    public String getAlcoholContent() {
        return alcoholContent;
    }

    public void setAlcoholContent(String alcoholContent) {
        this.alcoholContent = alcoholContent;
    }

    public String getNetContents() {
        return netContents;
    }

    public void setNetContents(String netContents) {
        this.netContents = netContents;
    }

    public String getHealthWarning() {
        return healthWarning;
    }

    public void setHealthWarning(String healthWarning) {
        this.healthWarning = healthWarning;
    }

    public String getNameAndAddress() {
        return nameAndAddress;
    }

    public void setNameAndAddress(String nameAndAddress) {
        this.nameAndAddress = nameAndAddress;
    }

    public String getQualifyingPhrase() {
        return qualifyingPhrase;
    }

    public void setQualifyingPhrase(String qualifyingPhrase) {
        this.qualifyingPhrase = qualifyingPhrase;
    }

    public String getCountryOfOrigin() {
        return countryOfOrigin;
    }

    public void setCountryOfOrigin(String countryOfOrigin) {
        this.countryOfOrigin = countryOfOrigin;
    }

    public String getGrapeVarietal() {
        return grapeVarietal;
    }

    public void setGrapeVarietal(String grapeVarietal) {
        this.grapeVarietal = grapeVarietal;
    }

    public String getAppellationOfOrigin() {
        return appellationOfOrigin;
    }

    public void setAppellationOfOrigin(String appellationOfOrigin) {
        this.appellationOfOrigin = appellationOfOrigin;
    }

    public String getVintageYear() {
        return vintageYear;
    }

    public void setVintageYear(String vintageYear) {
        this.vintageYear = vintageYear;
    }

    public Boolean getSulfiteDeclaration() {
        return sulfiteDeclaration;
    }

    public void setSulfiteDeclaration(Boolean sulfiteDeclaration) {
        this.sulfiteDeclaration = sulfiteDeclaration;
    }

    public String getAgeStatement() {
        return ageStatement;
    }

    public void setAgeStatement(String ageStatement) {
        this.ageStatement = ageStatement;
    }

    public String getStateOfDistillation() {
        return stateOfDistillation;
    }

    public void setStateOfDistillation(String stateOfDistillation) {
        this.stateOfDistillation = stateOfDistillation;
    }

    public Boolean getFdcYellow5() {
        return fdcYellow5;
    }

    public void setFdcYellow5(Boolean fdcYellow5) {
        this.fdcYellow5 = fdcYellow5;
    }

    public Boolean getCochinealCarmine() {
        return cochinealCarmine;
    }

    public void setCochinealCarmine(Boolean cochinealCarmine) {
        this.cochinealCarmine = cochinealCarmine;
    }
}
