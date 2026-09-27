package gov.ttb.labelverification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.ttb.labelverification.domain.Setting;
import gov.ttb.labelverification.repository.SettingRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Typed access to the key/value {@code settings} table (values are JSON). */
@Service
public class SettingsService {

    public static final String PIPELINE_MODEL = "submission_pipeline_model";
    public static final String APPROVAL_THRESHOLD = "approval_threshold";
    public static final String FIELD_STRICTNESS = "field_strictness";
    public static final String SLA_TARGETS = "sla_targets";

    public static final int DEFAULT_APPROVAL_THRESHOLD = 90;

    public record SlaTargets(int reviewResponseHours, int totalTurnaroundHours, int maxQueueDepth) {
        public static final SlaTargets DEFAULT = new SlaTargets(48, 72, 50);
    }

    private final SettingRepository repository;
    private final ObjectMapper objectMapper;

    public SettingsService(SettingRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /** "local" (default) or "cloud". */
    @Transactional(readOnly = true)
    public String pipelineModel() {
        return read(PIPELINE_MODEL, String.class).orElse("local");
    }

    /** Minimum overall confidence for the Ready-to-Approve queue and batch approval. */
    @Transactional(readOnly = true)
    public int approvalThreshold() {
        return read(APPROVAL_THRESHOLD, Integer.class).orElse(DEFAULT_APPROVAL_THRESHOLD);
    }

    @Transactional(readOnly = true)
    public SlaTargets slaTargets() {
        return read(SLA_TARGETS, SlaTargets.class).orElse(SlaTargets.DEFAULT);
    }

    @Transactional(readOnly = true)
    public Map<String, String> fieldStrictness() {
        return readGeneric(FIELD_STRICTNESS, new TypeReference<Map<String, String>>() {
        }).orElseGet(LinkedHashMap::new);
    }

    @PreAuthorize("hasRole('SPECIALIST')")
    @Transactional
    public void update(String pipelineModel, int approvalThreshold, SlaTargets slaTargets) {
        if (!"local".equals(pipelineModel) && !"cloud".equals(pipelineModel)) {
            throw new IllegalArgumentException("Pipeline model must be 'local' or 'cloud'");
        }
        if (approvalThreshold < 50 || approvalThreshold > 100) {
            throw new IllegalArgumentException("Approval threshold must be between 50 and 100");
        }
        write(PIPELINE_MODEL, pipelineModel);
        write(APPROVAL_THRESHOLD, approvalThreshold);
        write(SLA_TARGETS, slaTargets);
    }

    @Transactional
    public void write(String key, Object value) {
        String json = toJson(value);
        repository.findByKey(key).ifPresentOrElse(
                s -> s.setValue(json),
                () -> repository.save(new Setting(key, json)));
    }

    private <T> Optional<T> read(String key, Class<T> type) {
        return repository.findByKey(key).map(s -> {
            try {
                return objectMapper.readValue(s.getValue(), type);
            } catch (JsonProcessingException e) {
                return null;
            }
        });
    }

    private <T> Optional<T> readGeneric(String key, TypeReference<T> type) {
        return repository.findByKey(key).map(s -> {
            try {
                return objectMapper.readValue(s.getValue(), type);
            } catch (JsonProcessingException e) {
                return null;
            }
        });
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Setting is not serializable", e);
        }
    }
}
