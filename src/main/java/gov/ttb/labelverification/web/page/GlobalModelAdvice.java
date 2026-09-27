package gov.ttb.labelverification.web.page;

import gov.ttb.labelverification.regulatory.RegulatoryConstants;
import gov.ttb.labelverification.security.AppUserPrincipal;
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

    @ModelAttribute("currentUser")
    AppUserPrincipal currentUser(@AuthenticationPrincipal AppUserPrincipal user) {
        return user;
    }
}
