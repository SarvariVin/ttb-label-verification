package gov.ttb.labelverification.security;

import gov.ttb.labelverification.domain.User;
import gov.ttb.labelverification.domain.UserRole;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** Authenticated user held in the session. Role maps to ROLE_SPECIALIST / ROLE_APPLICANT. */
public record AppUserPrincipal(String id, String email, String name, String passwordHash, UserRole role,
                               String applicantId) implements UserDetails {

    public static AppUserPrincipal from(User user) {
        return new AppUserPrincipal(user.getId(), user.getEmail(), user.getName(), user.getPasswordHash(),
                user.getRole(), user.getApplicant() == null ? null : user.getApplicant().getId());
    }

    public boolean isSpecialist() {
        return role == UserRole.SPECIALIST;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }
}
