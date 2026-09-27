package gov.ttb.labelverification.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import jakarta.servlet.http.Cookie;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

class DemoLoginIntegrationTest {

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    class DisabledByDefault {
        @Autowired
        MockMvc mvc;

        @Test
        void noPickerAndNoPasswordlessSignIn() throws Exception {
            mvc.perform(get("/login")).andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("demo-listbox"))));
            mvc.perform(post("/login/demo").param("email", "specialist@example.gov").with(csrf()))
                    .andExpect(redirectedUrl("/login?error"));
        }
    }

    @Nested
    @SpringBootTest(properties = {
            "app.demo-login.enabled=true",
            "app.demo-login.accounts=specialist@example.gov",
            "spring.datasource.url=jdbc:h2:mem:demologin;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1"})
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    class Enabled {
        @Autowired
        MockMvc mvc;

        @Test
        void pickerListsOnlyAllowedAccountsAndNeverPasswords() throws Exception {
            mvc.perform(get("/login")).andExpect(status().isOk())
                    .andExpect(content().string(containsString("id=\"demo-listbox\"")))
                    .andExpect(content().string(containsString("data-demo-action=\"/login/demo\"")))
                    .andExpect(content().string(containsString("specialist@example.gov")))
                    .andExpect(content().string(not(containsString("applicant@example.com"))))
                    .andExpect(content().string(not(containsString("test-only-seed-password"))));
        }

        @Test
        void signsInAsTheSelectedAccount() throws Exception {
            // Sessions are stored in the database; the browser only holds the SESSION cookie.
            Cookie session = mvc.perform(post("/login/demo")
                            .param("email", "specialist@example.gov").with(csrf()))
                    .andExpect(redirectedUrl("/"))
                    .andReturn().getResponse().getCookie("SESSION");
            mvc.perform(get("/").cookie(session)).andExpect(status().isOk())
                    .andExpect(content().string(containsString("Review dashboard")));
            mvc.perform(get("/settings").cookie(session)).andExpect(status().isOk());
        }

        @Test
        void rejectsAccountsOutsideTheListAndMissingCsrf() throws Exception {
            mvc.perform(post("/login/demo").param("email", "applicant@example.com").with(csrf()))
                    .andExpect(redirectedUrl("/login?error"));
            mvc.perform(post("/login/demo").param("email", "specialist@example.gov"))
                    .andExpect(status().isForbidden());
        }
    }
}
