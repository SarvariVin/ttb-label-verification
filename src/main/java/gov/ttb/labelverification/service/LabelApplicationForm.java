package gov.ttb.labelverification.service;

import gov.ttb.labelverification.regulatory.BeverageType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Form 5100.31 application data as submitted by an applicant (web form, API
 * multipart, or one CSV row). Validated server-side regardless of client checks.
 */
public class LabelApplicationForm {

    @NotNull(message = "Select a beverage type")
    private BeverageType beverageType;

    @NotNull(message = "Enter bottle capacity")
    @Positive(message = "Capacity must be greater than 0")
    @Max(value = 100_000, message = "Capacity looks wrong")
    private Integer containerSizeMl;

    @Size(max = 10)
    private String classTypeCode;
    @Size(max = 100)
    private String serialNumber;
    @NotBlank(message = "Brand Name is required")
    @Size(max = 200)
    private String brandName;
    @Size(max = 200)
    private String fancifulName;
    @Size(max = 200)
    private String classType;
    @Size(max = 100)
    private String alcoholContent;
    @Size(max = 100)
    private String netContents;
    @Size(max = 1000)
    private String healthWarning;
    @Size(max = 500)
    private String nameAndAddress;
    @Size(max = 100)
    private String qualifyingPhrase;
    @Size(max = 100)
    private String countryOfOrigin;
    @Size(max = 200)
    private String grapeVarietal;
    @Size(max = 200)
    private String appellationOfOrigin;
    @Size(max = 10)
    private String vintageYear;
    private Boolean sulfiteDeclaration;
    @Size(max = 100)
    private String ageStatement;
    @Size(max = 100)
    private String stateOfDistillation;
    /** When correcting a previous submission. */
    @Size(max = 21)
    private String priorLabelId;

    public BeverageType getBeverageType() {
        return beverageType;
    }

    public void setBeverageType(BeverageType beverageType) {
        this.beverageType = beverageType;
    }

    public Integer getContainerSizeMl() {
        return containerSizeMl;
    }

    public void setContainerSizeMl(Integer containerSizeMl) {
        this.containerSizeMl = containerSizeMl;
    }

    public String getClassTypeCode() {
        return classTypeCode;
    }

    public void setClassTypeCode(String classTypeCode) {
        this.classTypeCode = classTypeCode;
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

    public String getPriorLabelId() {
        return priorLabelId;
    }

    public void setPriorLabelId(String priorLabelId) {
        this.priorLabelId = priorLabelId;
    }
}
