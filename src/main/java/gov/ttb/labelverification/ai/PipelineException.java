package gov.ttb.labelverification.ai;

/** Raised when an extraction pipeline cannot produce a result. */
public class PipelineException extends RuntimeException {

    private final boolean timeout;

    public PipelineException(String message, Throwable cause) {
        this(message, cause, false);
    }

    public PipelineException(String message, Throwable cause, boolean timeout) {
        super(message, cause);
        this.timeout = timeout;
    }

    public boolean isTimeout() {
        return timeout;
    }
}
