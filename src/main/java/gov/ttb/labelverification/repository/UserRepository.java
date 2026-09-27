package gov.ttb.labelverification.repository;

import gov.ttb.labelverification.domain.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByEmailIgnoreCase(String email);
}
