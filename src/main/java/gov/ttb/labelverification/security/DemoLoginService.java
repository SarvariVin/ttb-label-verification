package gov.ttb.labelverification.security;

import gov.ttb.labelverification.domain.User;
import gov.ttb.labelverification.repository.UserRepository;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demo mode: lets visitors pick an account on the login page and sign in without a password.
 * <p>
 * <b>Off by default.</b> Enable with {@code APP_DEMO_LOGIN=true} only for demonstrations:
 * while it is on, anyone who can reach the site can act as any listed account.
 * {@code APP_DEMO_LOGIN_ACCOUNTS} (comma-separated emails) limits the list; blank means all accounts.
 * Passwords are never exposed — the app only holds hashes.
 */
@Service
public class DemoLoginService {

    private static final Logger log = LoggerFactory.getLogger(DemoLoginService.class);

    /** What the login page shows for one account. */
    public record DemoAccount(String email, String name, String role) {
    }

    private final UserRepository users;
    private final boolean enabled;
    private final Set<String> allowed;

    public DemoLoginService(UserRepository users,
                            @Value("${app.demo-login.enabled:false}") boolean enabled,
                            @Value("${app.demo-login.accounts:}") String accounts) {
        this.users = users;
        this.enabled = enabled;
        this.allowed = Arrays.stream(accounts.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    @EventListener(ApplicationReadyEvent.class)
    void warnIfEnabled() {
        if (enabled) {
            log.warn("DEMO LOGIN IS ENABLED (APP_DEMO_LOGIN=true): anyone who can reach this site can sign in as {}. "
                    + "Do not use with real data.", allowed.isEmpty() ? "ANY account" : allowed);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Accounts offered on the login page: specialists first, then applicants, by email. */
    @Transactional(readOnly = true)
    public List<DemoAccount> accounts() {
        if (!enabled) {
            return List.of();
        }
        return users.findAll().stream()
                .filter(this::isAllowed)
                .sorted(Comparator.comparing((User u) -> u.getRole().ordinal()).thenComparing(User::getEmail))
                .map(u -> new DemoAccount(u.getEmail(), u.getName(), u.getRole().name()))
                .toList();
    }

    /** The principal to sign in as, if demo login is on and the account is allowed. */
    @Transactional(readOnly = true)
    public Optional<AppUserPrincipal> principalFor(String email) {
        if (!enabled || email == null || email.isBlank()) {
            return Optional.empty();
        }
        return users.findByEmailIgnoreCase(email.trim())
                .filter(this::isAllowed)
                .map(AppUserPrincipal::from);
    }

    private boolean isAllowed(User u) {
        return allowed.isEmpty() || allowed.contains(u.getEmail().toLowerCase(Locale.ROOT));
    }
}
