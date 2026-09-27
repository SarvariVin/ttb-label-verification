package gov.ttb.labelverification.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.apache.commons.logging.Log;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Accepts {@code DATABASE_URL} in the {@code postgresql://user:password@host:port/db} form
 * that hosting platforms (Railway, Heroku, Render…) inject, and converts it to the JDBC
 * URL plus username and password Spring expects. JDBC URLs pass through untouched.
 * Explicit {@code DATABASE_USERNAME} / {@code DATABASE_PASSWORD} always win.
 * <p>
 * On Railway (detected from the variables Railway injects into every service) it also
 * activates the {@code railway} profile when no profile was chosen, and stops at once
 * with an actionable message if {@code DATABASE_URL} is missing — instead of retrying
 * a non-existent {@code localhost:5432}.
 * <p>
 * Runs before config-data loading so the profile-specific file is picked up.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String SOURCE_NAME = "platformDatabaseUrl";
    static final List<String> RAILWAY_MARKERS =
            List.of("RAILWAY_ENVIRONMENT_NAME", "RAILWAY_ENVIRONMENT", "RAILWAY_PROJECT_ID", "RAILWAY_SERVICE_ID");

    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER - 1;
    }

    private final Log log;

    public DatabaseUrlEnvironmentPostProcessor(DeferredLogFactory logFactory) {
        this.log = logFactory.getLog(DatabaseUrlEnvironmentPostProcessor.class);
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication application) {
        log.info(diagnostics(env::getProperty));
        Map<String, Object> props = new HashMap<>(platformDefaults(env::getProperty));
        props.putAll(convert(env.getProperty("DATABASE_URL"),
                env.getProperty("DATABASE_USERNAME"), env.getProperty("DATABASE_PASSWORD")));
        if (!props.isEmpty()) {
            env.getPropertySources().addFirst(new MapPropertySource(SOURCE_NAME, props));
        }
    }

    /**
     * Railway-specific defaults.
     *
     * @throws IllegalStateException on Railway when {@code DATABASE_URL} is not set
     */
    static Map<String, Object> platformDefaults(Function<String, String> env) {
        boolean onRailway = RAILWAY_MARKERS.stream().anyMatch(k -> !isBlank(env.apply(k)));
        if (!onRailway) {
            return Map.of();
        }
        if (isBlank(env.apply("DATABASE_URL"))) {
            throw new IllegalStateException("""
                    DATABASE_URL is not set on this Railway service, so there is no database to connect to.
                    Fix: open this service → Variables → New Variable → name DATABASE_URL, value: a reference \
                    to your PostgreSQL service's DATABASE_URL (e.g. ${{Postgres.DATABASE_URL}}), then Deploy \
                    the staged change. See docs/deploy-railway.md.
                    """ + diagnostics(env));
        }
        boolean profileChosen = !isBlank(env.apply("spring.profiles.active"))
                || !isBlank(env.apply("SPRING_PROFILES_ACTIVE"));
        return profileChosen ? Map.of() : Map.of("spring.profiles.active", "railway");
    }

    /**
     * One startup line for diagnosing hosting configuration. Never prints credentials:
     * only the scheme, host, port and database of {@code DATABASE_URL}.
     */
    static String diagnostics(Function<String, String> env) {
        boolean onRailway = RAILWAY_MARKERS.stream().anyMatch(k -> !isBlank(env.apply(k)));
        String url = env.apply("DATABASE_URL");
        String db;
        if (isBlank(url)) {
            db = "not set";
        } else {
            try {
                URI uri = URI.create(url.startsWith("jdbc:") ? url.substring(5) : url);
                db = "set (" + uri.getScheme() + "://" + (uri.getHost() == null ? "?" : uri.getHost())
                        + (uri.getPort() > 0 ? ":" + uri.getPort() : "") + (uri.getPath() == null ? "" : uri.getPath()) + ")";
            } catch (IllegalArgumentException e) {
                db = "set (unparseable)";
            }
        }
        String profile = !isBlank(env.apply("SPRING_PROFILES_ACTIVE")) ? env.apply("SPRING_PROFILES_ACTIVE")
                : !isBlank(env.apply("spring.profiles.active")) ? env.apply("spring.profiles.active")
                : onRailway ? "railway (automatic)" : "default";
        String users = env.apply("APP_USERS");
        String usersState = isBlank(users) ? "not set" : "set (" + users.trim().length() + " chars, "
                + (users.trim().startsWith("[") ? "JSON" : "Base64") + ")";
        return "Hosting check: railway=" + onRailway + ", DATABASE_URL=" + db + ", profile=" + profile
                + ", APP_USERS=" + usersState;
    }

    static Map<String, Object> convert(String url, String explicitUser, String explicitPassword) {
        Map<String, Object> props = new HashMap<>();
        if (url == null || !(url.startsWith("postgres://") || url.startsWith("postgresql://"))) {
            return props;
        }
        URI uri = URI.create(url);
        int port = uri.getPort() > 0 ? uri.getPort() : 5432;
        String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
        props.put("spring.datasource.url", "jdbc:postgresql://" + uri.getHost() + ":" + port + uri.getRawPath() + query);
        String userInfo = uri.getRawUserInfo();
        if (userInfo != null) {
            int colon = userInfo.indexOf(':');
            String user = decode(colon >= 0 ? userInfo.substring(0, colon) : userInfo);
            String password = colon >= 0 ? decode(userInfo.substring(colon + 1)) : null;
            if (isBlank(explicitUser)) {
                props.put("spring.datasource.username", user);
            }
            if (isBlank(explicitPassword) && password != null) {
                props.put("spring.datasource.password", password);
            }
        }
        return props;
    }

    private static String decode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
