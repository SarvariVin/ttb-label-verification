package gov.ttb.labelverification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** A company that submits labels for approval. */
@Entity
@Table(name = "applicants")
public class Applicant extends BaseEntity {

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "contact_name")
    private String contactName;

    private String notes;

    protected Applicant() {
    }

    public Applicant(String companyName, String contactEmail, String contactName, String notes) {
        this.companyName = companyName;
        this.contactEmail = contactEmail;
        this.contactName = contactName;
        this.notes = notes;
    }

    public String getCompanyName() {
        return companyName;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public String getContactName() {
        return contactName;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
