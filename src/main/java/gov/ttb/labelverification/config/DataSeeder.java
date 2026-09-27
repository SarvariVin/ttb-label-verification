package gov.ttb.labelverification.config;

import gov.ttb.labelverification.domain.Applicant;
import gov.ttb.labelverification.domain.User;
import gov.ttb.labelverification.domain.UserRole;
import gov.ttb.labelverification.repository.ApplicantRepository;
import gov.ttb.labelverification.repository.UserRepository;
import gov.ttb.labelverification.service.SettingsService;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bootstraps an empty database with one specialist, one applicant company and
 * its user, and default settings. No labels are seeded — every label goes
 * through the real pipeline.
 * <p>
 * Credentials are never hard-coded: the password comes from
 * {@code APP_SEED_PASSWORD}; if absent, a random password is generated and
 * logged once. Disable in production with {@code APP_SEED=false}.
 */
@Component
@Order(10) // before UserProvisioner (@Order(100)): an empty database must get the bootstrap accounts first
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository users;
    private final ApplicantRepository applicants;
    private final SettingsService settings;
    private final PasswordEncoder encoder;
    private final AppProperties.Seed seed;

    public DataSeeder(UserRepository users, ApplicantRepository applicants, SettingsService settings,
                      PasswordEncoder encoder, AppProperties properties) {
        this.users = users;
        this.applicants = applicants;
        this.settings = settings;
        this.encoder = encoder;
        this.seed = properties.seed();
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            resetBootstrapPasswordsIfRequested();
            return;
        }
        String password = seed.password();
        boolean generated = password == null || password.isBlank();
        if (generated) {
            byte[] bytes = new byte[18];
            new SecureRandom().nextBytes(bytes);
            password = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        }
        String hash = encoder.encode(password);

        users.save(new User("Labeling Specialist", seed.specialistEmail(), hash, UserRole.SPECIALIST, null));
        Applicant company = applicants.save(new Applicant(seed.applicantCompany(), seed.applicantEmail(),
                "Applicant Contact", null));
        users.save(new User("Applicant Contact", seed.applicantEmail(), hash, UserRole.APPLICANT, company));

        if (generated) {
            log.warn("Bootstrap accounts created: {} (specialist), {} (applicant). Generated password: {} "
                    + "— set APP_SEED_PASSWORD to choose one, and APP_SEED=false in production.",
                    seed.specialistEmail(), seed.applicantEmail(), password);
        } else {
            log.info("Bootstrap accounts created: {} (specialist), {} (applicant).",
                    seed.specialistEmail(), seed.applicantEmail());
        }

        Map<String, String> strictness = new LinkedHashMap<>();
        strictness.put("health_warning", "strict");
        strictness.put("brand_name", "moderate");
        strictness.put("fanciful_name", "moderate");
        strictness.put("class_type", "moderate");
        strictness.put("alcohol_content", "strict");
        strictness.put("net_contents", "strict");
        strictness.put("name_and_address", "lenient");
        strictness.put("qualifying_phrase", "lenient");
        strictness.put("country_of_origin", "moderate");
        strictness.put("vintage_year", "strict");
        strictness.put("sulfite_declaration", "strict");
        settings.write(SettingsService.FIELD_STRICTNESS, strictness);
        settings.write(SettingsService.PIPELINE_MODEL, "local");
        settings.write(SettingsService.APPROVAL_THRESHOLD, SettingsService.DEFAULT_APPROVAL_THRESHOLD);
        settings.write(SettingsService.SLA_TARGETS, SettingsService.SlaTargets.DEFAULT);
    }

    /**
     * Recovery for lost bootstrap credentials: with {@code APP_SEED_RESET_PASSWORD=true} and a
     * non-blank {@code APP_SEED_PASSWORD}, the bootstrap specialist and applicant get that password.
     * Remove the flag after signing in; it re-applies on every restart while set.
     */
    void resetBootstrapPasswordsIfRequested() {
        if (!seed.resetPassword()) {
            return;
        }
        if (seed.password() == null || seed.password().isBlank()) {
            log.warn("APP_SEED_RESET_PASSWORD is set but APP_SEED_PASSWORD is empty — nothing was reset.");
            return;
        }
        String hash = encoder.encode(seed.password());
        int count = 0;
        for (String email : new String[]{seed.specialistEmail(), seed.applicantEmail()}) {
            var user = users.findByEmailIgnoreCase(email);
            if (user.isPresent()) {
                user.get().changePasswordHash(hash);
                count++;
            } else {
                log.warn("Bootstrap account {} not found — not reset.", email);
            }
        }
        log.warn("Reset the password of {} bootstrap account(s) to APP_SEED_PASSWORD. "
                + "Remove APP_SEED_RESET_PASSWORD now.", count);
    }
}
