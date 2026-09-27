package gov.ttb.labelverification.repository;

import gov.ttb.labelverification.domain.Setting;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettingRepository extends JpaRepository<Setting, String> {

    Optional<Setting> findByKey(String key);
}
