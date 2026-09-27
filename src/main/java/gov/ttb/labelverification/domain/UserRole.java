package gov.ttb.labelverification.domain;

public enum UserRole {
    /** TTB labeling specialist — reviews, approves, overrides, configures. */
    SPECIALIST,
    /** Industry applicant — submits labels and sees only their own submissions. */
    APPLICANT
}
