package gov.ttb.labelverification.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import gov.ttb.labelverification.domain.User;
import gov.ttb.labelverification.domain.UserRole;
import gov.ttb.labelverification.repository.ApplicantRepository;
import gov.ttb.labelverification.repository.UserRepository;
import gov.ttb.labelverification.service.SettingsService;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

class DataSeederResetTest {

    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    private static AppProperties props(String password, boolean reset) {
        return new AppProperties(
                new AppProperties.Storage("filesystem", "./target/x"),
                new AppProperties.Seed(true, password, reset, "specialist@example.gov", "applicant@example.com", "Co"),
                new AppProperties.Pipeline(Duration.ofSeconds(60)),
                new AppProperties.Ocr(null, null, "eng", 1),
                new AppProperties.Cloud(null, null, "m", "u"));
    }

    private User existing(String email) {
        return new User("x", email, encoder.encode("old-password"), UserRole.SPECIALIST, null);
    }

    private DataSeeder seeder(UserRepository users, AppProperties p) {
        return new DataSeeder(users, mock(ApplicantRepository.class), mock(SettingsService.class), encoder, p);
    }

    @Test
    void resetsBothBootstrapAccountsWhenRequested() {
        UserRepository users = mock(UserRepository.class);
        User specialist = existing("specialist@example.gov");
        User applicant = existing("applicant@example.com");
        when(users.count()).thenReturn(2L);
        when(users.findByEmailIgnoreCase("specialist@example.gov")).thenReturn(Optional.of(specialist));
        when(users.findByEmailIgnoreCase("applicant@example.com")).thenReturn(Optional.of(applicant));

        seeder(users, props("New-Strong-Passw0rd!", true)).run(null);

        assertThat(encoder.matches("New-Strong-Passw0rd!", specialist.getPasswordHash())).isTrue();
        assertThat(encoder.matches("New-Strong-Passw0rd!", applicant.getPasswordHash())).isTrue();
    }

    @Test
    void doesNothingWithoutTheFlag() {
        UserRepository users = mock(UserRepository.class);
        User specialist = existing("specialist@example.gov");
        when(users.count()).thenReturn(2L);
        when(users.findByEmailIgnoreCase("specialist@example.gov")).thenReturn(Optional.of(specialist));

        seeder(users, props("New-Strong-Passw0rd!", false)).run(null);

        assertThat(encoder.matches("old-password", specialist.getPasswordHash())).isTrue();
    }

    @Test
    void refusesToResetToAnEmptyPassword() {
        UserRepository users = mock(UserRepository.class);
        User specialist = existing("specialist@example.gov");
        when(users.count()).thenReturn(2L);
        when(users.findByEmailIgnoreCase("specialist@example.gov")).thenReturn(Optional.of(specialist));

        seeder(users, props("", true)).run(null);

        assertThat(encoder.matches("old-password", specialist.getPasswordHash())).isTrue();
    }
}
