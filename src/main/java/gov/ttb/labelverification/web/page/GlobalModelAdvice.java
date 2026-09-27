package gov.ttb.labelverification.web.page;

import gov.ttb.labelverification.regulatory.RegulatoryConstants;
import gov.ttb.labelverification.security.AppUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Attributes every page template can use. */
@ControllerAdvice(basePackages = "gov.ttb.labelverification.web.page")
public class GlobalModelAdvice {

    @ModelAttribute("appName")
    String appName() {
        return RegulatoryConstants.APP_NAME;
    }

    @ModelAttribute("appTagline")
    String appTagline() {
        return RegulatoryConstants.APP_TAGLINE;
    }

    /** Request path without the context path, for highlighting the active navigation link. */
    @ModelAttribute("currentPath")
    String currentPath(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    @ModelAttribute("currentUser")
    AppUserPrincipal currentUser(@AuthenticationPrincipal AppUserPrincipal user) {
        return user;
    }
}
