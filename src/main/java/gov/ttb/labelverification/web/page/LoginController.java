package gov.ttb.labelverification.web.page;

import gov.ttb.labelverification.security.AppUserPrincipal;
import gov.ttb.labelverification.security.DemoLoginService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LoginController {

    private final DemoLoginService demo;
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    public LoginController(DemoLoginService demo) {
        this.demo = demo;
    }

    @GetMapping("/login")
    String login(Model model) {
        model.addAttribute("demoAccounts", demo.accounts());
        return "login";
    }

    /**
     * Demo mode only (off by default): sign in as the selected account without a password.
     * CSRF-protected like every other form; the session id is renewed to prevent fixation.
     */
    @PostMapping("/login/demo")
    String demoLogin(@RequestParam(required = false) String email,
                     HttpServletRequest request, HttpServletResponse response) {
        Optional<AppUserPrincipal> principal = demo.principalFor(email);
        if (principal.isEmpty()) {
            return "redirect:/login?error";
        }
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        SecurityContext context = contextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal.get(), null, principal.get().getAuthorities()));
        contextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        return "redirect:/";
    }
}
