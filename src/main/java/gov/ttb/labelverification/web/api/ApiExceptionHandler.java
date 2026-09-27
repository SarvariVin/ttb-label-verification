package gov.ttb.labelverification.web.api;

import gov.ttb.labelverification.ai.PipelineException;
import gov.ttb.labelverification.service.BusinessRuleException;
import gov.ttb.labelverification.service.NotFoundException;
import gov.ttb.labelverification.storage.ImageFileValidator;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** RFC 9457 problem responses for the REST API. Never leaks stack traces. */
@RestControllerAdvice(basePackages = "gov.ttb.labelverification.web.api")
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler({BusinessRuleException.class, ImageFileValidator.InvalidImageException.class,
            IllegalArgumentException.class})
    ProblemDetail unprocessable(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    @ExceptionHandler(PipelineException.class)
    ProblemDetail pipeline(PipelineException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "The label could not be read automatically: " + e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalid(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail forbidden(AccessDeniedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "You do not have permission to do that");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e) {
        log.error("Unhandled API error", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }
}
