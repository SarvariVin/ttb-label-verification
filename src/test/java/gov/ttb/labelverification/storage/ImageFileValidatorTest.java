package gov.ttb.labelverification.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ImageFileValidatorTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0};

    @Test
    void detectsByMagicBytes() {
        assertThat(ImageFileValidator.validate(PNG, "image/png", "a.png")).isEqualTo("image/png");
        assertThat(ImageFileValidator.validate(JPEG, "image/jpeg", "a.jpg")).isEqualTo("image/jpeg");
    }

    @Test
    void rejectsSpoofedContent() {
        byte[] html = "<html><script>".getBytes();
        assertThatThrownBy(() -> ImageFileValidator.validate(html, "image/png", "evil.png"))
                .isInstanceOf(ImageFileValidator.InvalidImageException.class)
                .hasMessageContaining("not a JPEG, PNG or WebP");
    }

    @Test
    void rejectsDisallowedDeclaredType() {
        assertThatThrownBy(() -> ImageFileValidator.validate(PNG, "image/gif", "a.gif"))
                .isInstanceOf(ImageFileValidator.InvalidImageException.class);
    }

    @Test
    void rejectsEmpty() {
        assertThatThrownBy(() -> ImageFileValidator.validate(new byte[0], "image/png", "a.png"))
                .hasMessageContaining("empty");
    }
}
