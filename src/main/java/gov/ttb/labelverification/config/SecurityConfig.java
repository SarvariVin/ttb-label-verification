package gov.ttb.labelverification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Route-level security, security headers and authentication entry points.
 * <ul>
 *   <li><b>/api/**</b> — stateless HTTP Basic. Session cookies are ignored, so CSRF
 *       protection is unnecessary on this chain.</li>
 *   <li><b>everything else</b> — form login + session cookie + CSRF tokens.</li>
 * </ul>
 * Action-level role checks live in the services via {@code @PreAuthorize}.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String CSP = "default-src 'self'; img-src 'self' data: blob:; style-src 'self'; "
            + "script-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'";

    @Bean
    @Order(1)
    SecurityFilterChain apiChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/settings/**").hasRole("SPECIALIST")
                        .anyRequest().authenticated())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .csrf(AbstractHttpConfigurer::disable)
                .headers(SecurityConfig::securityHeaders);
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain webChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/login/demo", "/css/**", "/js/**", "/favicon.svg", "/robots.txt",
                                "/actuator/health", "/error").permitAll()
                        .requestMatchers("/settings/**", "/applicants/**").hasRole("SPECIALIST")
                        .requestMatchers("/submit/**").hasRole("APPLICANT")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .usernameParameter("email")
                        .defaultSuccessUrl("/", true)
                        .permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login?logout").permitAll())
                .headers(SecurityConfig::securityHeaders);
        return http.build();
    }

    private static void securityHeaders(HeadersConfigurer<HttpSecurity> headers) {
        headers
                .contentSecurityPolicy(csp -> csp.policyDirectives(CSP))
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                .referrerPolicy(ref -> ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
