package gov.ttb.labelverification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    /** Set for applicant users: the company they submit on behalf of. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicant_id")
    private Applicant applicant;

    protected User() {
    }

    public User(String name, String email, String passwordHash, UserRole role, Applicant applicant) {
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.applicant = applicant;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserRole getRole() {
        return role;
    }

    public Applicant getApplicant() {
        return applicant;
    }
}
