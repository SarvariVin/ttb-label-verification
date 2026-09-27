package gov.ttb.labelverification.service;

/** Resource missing — or not visible to the caller (never leak existence across applicants). */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
