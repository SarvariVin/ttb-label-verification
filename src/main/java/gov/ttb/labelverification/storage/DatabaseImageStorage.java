package gov.ttb.labelverification.storage;

import gov.ttb.labelverification.domain.Ids;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Stores label images in the {@code image_blobs} table.
 * <p>
 * For hosts that offer a single persistent volume (which PostgreSQL needs), such as
 * Railway's free plan. Enable with {@code APP_STORAGE_TYPE=database}. Images count
 * toward database size; object storage is preferable at scale.
 */
@Component
@ConditionalOnProperty(prefix = "app.storage", name = "type", havingValue = "database")
public class DatabaseImageStorage implements ImageStorage {

    private final JdbcTemplate jdbc;

    public DatabaseImageStorage(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String store(byte[] bytes, String originalFilename, String contentType) {
        String key = "db/" + Ids.newId();
        jdbc.update("INSERT INTO image_blobs (storage_key, content_type, size_bytes, content, created_at) "
                        + "VALUES (?, ?, ?, ?, ?)",
                key, contentType, bytes.length, bytes, Timestamp.from(Instant.now()));
        return key;
    }

    @Override
    public byte[] load(String storageKey) {
        List<byte[]> rows = jdbc.query("SELECT content FROM image_blobs WHERE storage_key = ?",
                (rs, i) -> rs.getBytes(1), storageKey);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Image not found: " + storageKey);
        }
        return rows.get(0);
    }
}
