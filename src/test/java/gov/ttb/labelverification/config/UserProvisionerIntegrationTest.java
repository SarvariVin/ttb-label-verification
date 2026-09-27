package gov.ttb.labelverification.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gov.ttb.labelverification.domain.User;
import gov.ttb.labelverification.domain.UserRole;
import gov.ttb.labelverification.repository.ApplicantRepository;
import gov.ttb.labelverification.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/** Test-only passwords; they exist only in this in-memory database. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:provisioner;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "app.users=[" +
                "{\"role\":\"SPECIALIST\",\"email\":\"reviewer.two@example.gov\",\"name\":\"Reviewer Two\",\"password\":\"test-only-pass-01\"}," +
                "{\"role\":\"specialist\",\"email\":\"reviewer.three@example.gov\",\"passwordHash\":" +
                "\"{bcrypt}$2a$10$.1k0RdyRjZFIEypXKCYbyOBxO9iexW7gq.X6cRv/SgB7jhej4OOxC\"}," +
                "{\"role\":\"APPLICANT\",\"email\":\"owner@winery.example.com\",\"name\":\"Owner\",\"company\":\"Test Winery\",\"password\":\"test-only-pass-02\"}," +
                "{\"role\":\"APPLICANT\",\"email\":\"second@winery.example.com\",\"company\":\"test winery\",\"password\":\"test-only-pass-03\"}," +
                "{\"role\":\"APPLICANT\",\"email\":\"weak@example.com\",\"password\":\"short\"}," +
                "{\"role\":\"ADMIN\",\"email\":\"bad-role@example.com\",\"password\":\"test-only-pass-04\"}," +
                "{\"role\":\"APPLICANT\",\"email\":\"not-an-email\",\"password\":\"test-only-pass-05\"}" +
                "]"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserProvisionerIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    UserRepository users;
    @Autowired
    ApplicantRepository applicants;
    @Autowired
    UserProvisioner provisioner;
    @Autowired
    PasswordEncoder encoder;
    @Autowired
    TransactionTemplate tx;

    @Test
    void createsDeclaredAccountsAndSkipsInvalidOnes() throws Exception {
        assertThat(users.findByEmailIgnoreCase("reviewer.two@example.gov")).get()
                .extracting(User::getRole).isEqualTo(UserRole.SPECIALIST);
        assertThat(users.findByEmailIgnoreCase("reviewer.three@example.gov")).isPresent();
        mvc.perform(get("/api/v1/settings").with(httpBasic("reviewer.three@example.gov", "test-only-pass-hash")))
                .andExpect(status().isOk());
        assertThat(users.findByEmailIgnoreCase("weak@example.com")).isEmpty();
        assertThat(users.findByEmailIgnoreCase("bad-role@example.com")).isEmpty();
        assertThat(users.findByEmailIgnoreCase("not-an-email")).isEmpty();

        // Both applicants share one company (matched case-insensitively).
        assertThat(applicants.findAll().stream().filter(a -> a.getCompanyName().equalsIgnoreCase("Test Winery")))
                .hasSize(1);

        // They can sign in, with the right role.
        mvc.perform(get("/api/v1/settings").with(httpBasic("reviewer.two@example.gov", "test-only-pass-01")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/labels").with(httpBasic("owner@winery.example.com", "test-only-pass-02")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/settings").with(httpBasic("owner@winery.example.com", "test-only-pass-02")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/labels").with(httpBasic("owner@winery.example.com", "wrong-password!")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rerunIsIdempotentAndPasswordChangesApply() {
        String before = users.findByEmailIgnoreCase("reviewer.two@example.gov").orElseThrow().getPasswordHash();
        long count = users.count();
        tx.executeWithoutResult(s -> provisioner.run(null));
        assertThat(users.count()).isEqualTo(count);
        assertThat(users.findByEmailIgnoreCase("reviewer.two@example.gov").orElseThrow().getPasswordHash())
                .as("unchanged password is not re-hashed").isEqualTo(before);

        var changed = new UserProvisioner.Entry("SPECIALIST", "reviewer.two@example.gov", null, null,
                "test-only-pass-99", null);
        UserProvisioner.Outcome outcome = tx.execute(s -> provisioner.apply(changed, 1));
        assertThat(outcome).isEqualTo(UserProvisioner.Outcome.UPDATED);
        assertThat(encoder.matches("test-only-pass-99",
                users.findByEmailIgnoreCase("reviewer.two@example.gov").orElseThrow().getPasswordHash())).isTrue();
    }
}
