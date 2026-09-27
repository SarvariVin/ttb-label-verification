package gov.ttb.labelverification.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class UserProvisionerDecodeTest {

    private static final String JSON = "[{\"role\":\"SPECIALIST\",\"email\":\"a@b.gov\","
            + "\"passwordHash\":\"{bcrypt}$2a$10$abc/def+ghi\"}]";

    @Test
    void jsonPassesThrough() {
        assertThat(UserProvisioner.decode(JSON)).isEqualTo(JSON);
    }

    @Test
    void base64StandardAndUrlSafeAreDecoded() {
        byte[] bytes = JSON.getBytes(StandardCharsets.UTF_8);
        assertThat(UserProvisioner.decode(Base64.getEncoder().encodeToString(bytes))).isEqualTo(JSON);
        assertThat(UserProvisioner.decode(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes))).isEqualTo(JSON);
    }

    @Test
    void base64WithLineBreaksIsAccepted() {
        String b64 = Base64.getMimeEncoder().encodeToString(JSON.getBytes(StandardCharsets.UTF_8));
        assertThat(b64).contains("\r\n");
        assertThat(UserProvisioner.decode(b64)).isEqualTo(JSON);
    }

    @Test
    void garbageIsRejected() {
        assertThat(UserProvisioner.decode("not json, not base64 !!!")).isNull();
        assertThat(UserProvisioner.decode(Base64.getEncoder().encodeToString("hello".getBytes()))).isNull();
    }
}
