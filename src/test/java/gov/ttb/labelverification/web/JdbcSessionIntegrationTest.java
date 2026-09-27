package gov.ttb.labelverification.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Sign-in state is stored in the database and is usable from the session cookie alone. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:sessions;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JdbcSessionIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    FindByIndexNameSessionRepository<?> sessions;

    @Test
    void signInIsPersistedAndRestoredFromTheDatabase() throws Exception {
        MvcResult login = mvc.perform(post("/login").with(csrf())
                        .param("email", "specialist@example.gov")
                        .param("password", "test-only-seed-password"))
                .andExpect(redirectedUrl("/"))
                .andReturn();

        // The session lives in the database, indexed by principal name.
        assertThat(sessions.findByPrincipalName("specialist@example.gov")).isNotEmpty();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM spring_session WHERE principal_name = 'specialist@example.gov'", Integer.class))
                .isPositive();

        // Only the cookie is sent back (no in-memory MockHttpSession): the app restores the sign-in
        // from the database, which is what happens after a restart.
        Cookie cookie = login.getResponse().getCookie("SESSION");
        assertThat(cookie).as("Spring Session cookie").isNotNull();
        mvc.perform(get("/").cookie(cookie)).andExpect(status().isOk());
        mvc.perform(get("/settings").cookie(cookie)).andExpect(status().isOk());
    }
}
