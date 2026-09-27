package gov.ttb.labelverification.web.page;

import gov.ttb.labelverification.service.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

/** Friendly error pages; stack traces are logged, never shown. */
@ControllerAdvice(basePackages = "gov.ttb.labelverification.web.page")
public class PageExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(PageExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    ModelAndView notFound(NotFoundException e) {
        return error(HttpStatus.NOT_FOUND, "Not found", e.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ModelAndView forbidden() {
        return error(HttpStatus.FORBIDDEN, "Access denied", "You do not have permission to view this page.");
    }

    @ExceptionHandler(Exception.class)
    ModelAndView unexpected(Exception e) {
        log.error("Unhandled page error", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong",
                "An unexpected error occurred. Please try again.");
    }

    private static ModelAndView error(HttpStatus status, String title, String message) {
        ModelAndView mav = new ModelAndView("error");
        mav.setStatus(status);
        mav.addObject("status", status.value());
        mav.addObject("title", title);
        mav.addObject("message", message);
        return mav;
    }
}
