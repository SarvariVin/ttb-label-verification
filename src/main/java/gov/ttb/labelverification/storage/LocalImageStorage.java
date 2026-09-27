package gov.ttb.labelverification.storage;

import gov.ttb.labelverification.config.AppProperties;
import gov.ttb.labelverification.domain.Ids;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Stores images under {@code app.storage.directory}/yyyy/MM/<id>.<ext>. */
@Component
@ConditionalOnProperty(prefix = "app.storage", name = "type", havingValue = "filesystem", matchIfMissing = true)
public class LocalImageStorage implements ImageStorage {

    private final Path root;

    public LocalImageStorage(AppProperties properties) {
        this.root = Path.of(properties.storage().directory()).toAbsolutePath().normalize();
    }

    @Override
    public String store(byte[] bytes, String originalFilename, String contentType) {
        LocalDate today = LocalDate.now();
        String key = "%d/%02d/%s.%s".formatted(today.getYear(), today.getMonthValue(), Ids.newId(), extension(contentType));
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store image", e);
        }
        return key;
    }

    @Override
    public byte[] load(String storageKey) {
        try {
            return Files.readAllBytes(resolve(storageKey));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read image " + storageKey, e);
        }
    }

    /** Resolves a key and refuses anything that escapes the storage root (path traversal). */
    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return path;
    }

    private static String extension(String contentType) {
        return switch (contentType == null ? "" : contentType.toLowerCase(Locale.ROOT)) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
    }
}
