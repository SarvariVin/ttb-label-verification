package gov.ttb.labelverification.service;

/** A request that is well-formed but not allowed in the current state (HTTP 409/422). */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
