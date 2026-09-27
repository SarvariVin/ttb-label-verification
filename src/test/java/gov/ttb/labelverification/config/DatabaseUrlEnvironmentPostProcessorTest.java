package gov.ttb.labelverification.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class DatabaseUrlEnvironmentPostProcessorTest {

    @Test
    void convertsPlatformUrlToJdbcWithCredentials() {
        Map<String, Object> p = DatabaseUrlEnvironmentPostProcessor.convert(
                "postgresql://app_user:s%40fe%3Apw@postgres.railway.internal:5432/railway", null, null);
        assertThat(p)
                .containsEntry("spring.datasource.url", "jdbc:postgresql://postgres.railway.internal:5432/railway")
                .containsEntry("spring.datasource.username", "app_user")
                .containsEntry("spring.datasource.password", "s@fe:pw");
    }

    @Test
    void keepsQueryAndDefaultsPort() {
        Map<String, Object> p = DatabaseUrlEnvironmentPostProcessor.convert(
                "postgres://u:p@db.example.com/labels?sslmode=require", null, null);
        assertThat(p).containsEntry("spring.datasource.url", "jdbc:postgresql://db.example.com:5432/labels?sslmode=require");
    }

    @Test
    void explicitCredentialsWin() {
        Map<String, Object> p = DatabaseUrlEnvironmentPostProcessor.convert(
                "postgresql://u:p@h:5432/d", "explicit", "explicit-secret");
        assertThat(p).doesNotContainKeys("spring.datasource.username", "spring.datasource.password");
    }

    @Test
    void jdbcUrlsAreLeftAlone() {
        assertThat(DatabaseUrlEnvironmentPostProcessor.convert("jdbc:postgresql://h:5432/d", null, null)).isEmpty();
        assertThat(DatabaseUrlEnvironmentPostProcessor.convert(null, null, null)).isEmpty();
    }

    @Test
    void railwayWithoutDatabaseUrlFailsWithActionableMessage() {
        Map<String, String> env = Map.of("RAILWAY_ENVIRONMENT_NAME", "production");
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> DatabaseUrlEnvironmentPostProcessor.platformDefaults(env::get))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DATABASE_URL is not set on this Railway service")
                .hasMessageContaining("Variables");
    }

    @Test
    void railwayActivatesItsProfileUnlessOneIsChosen() {
        assertThat(DatabaseUrlEnvironmentPostProcessor.platformDefaults(
                Map.of("RAILWAY_PROJECT_ID", "p", "DATABASE_URL", "postgresql://u:p@h/d")::get))
                .containsEntry("spring.profiles.active", "railway");
        assertThat(DatabaseUrlEnvironmentPostProcessor.platformDefaults(
                Map.of("RAILWAY_PROJECT_ID", "p", "DATABASE_URL", "postgresql://u:p@h/d",
                        "SPRING_PROFILES_ACTIVE", "custom")::get))
                .isEmpty();
    }

    @Test
    void offRailwayNothingChanges() {
        assertThat(DatabaseUrlEnvironmentPostProcessor.platformDefaults(Map.<String, String>of()::get)).isEmpty();
    }

    @Test
    void diagnosticsNeverPrintCredentials() {
        String line = DatabaseUrlEnvironmentPostProcessor.diagnostics(Map.of(
                "RAILWAY_PROJECT_ID", "p",
                "DATABASE_URL", "postgresql://user:SuperSecret@postgres.railway.internal:5432/railway")::get);
        assertThat(line)
                .contains("railway=true")
                .contains("postgresql://postgres.railway.internal:5432/railway")
                .contains("railway (automatic)")
                .doesNotContain("SuperSecret").doesNotContain("user");
        assertThat(DatabaseUrlEnvironmentPostProcessor.diagnostics(Map.<String, String>of()::get))
                .isEqualTo("Hosting check: railway=false, DATABASE_URL=not set, profile=default, APP_USERS=not set");
        assertThat(DatabaseUrlEnvironmentPostProcessor.diagnostics(Map.of("APP_USERS", "[{\"role\":\"x\"}]")::get))
                .endsWith("APP_USERS=set (14 chars, JSON)");
    }
}
