package gov.ttb.labelverification.service;

import gov.ttb.labelverification.ai.ExtractionPipeline;
import gov.ttb.labelverification.ai.ExtractionResult;
import gov.ttb.labelverification.ai.LabelImageData;
import gov.ttb.labelverification.ai.PipelineException;
import gov.ttb.labelverification.ai.cloud.CloudExtractionPipeline;
import gov.ttb.labelverification.ai.local.LocalExtractionPipeline;
import gov.ttb.labelverification.config.AppProperties;
import gov.ttb.labelverification.regulatory.BeverageType;
import gov.ttb.labelverification.regulatory.FieldName;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Chooses the extraction pipeline from settings and applies fallback + timeout:
 * <ul>
 *   <li>setting "local" → local; on failure, cloud if configured</li>
 *   <li>setting "cloud" → cloud; on failure, local</li>
 * </ul>
 * {@code modelUsed} on the result shows which pipeline actually ran.
 */
@Service
public class ExtractionService {

    private static final Logger log = LoggerFactory.getLogger(ExtractionService.class);

    private final LocalExtractionPipeline local;
    private final CloudExtractionPipeline cloud;
    private final SettingsService settings;
    private final Duration timeout;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public ExtractionService(LocalExtractionPipeline local, CloudExtractionPipeline cloud,
                             SettingsService settings, AppProperties properties) {
        this.local = local;
        this.cloud = cloud;
        this.settings = settings;
        this.timeout = properties.pipeline().timeout();
    }

    public boolean isCloudAvailable() {
        return cloud.isAvailable();
    }

    public boolean isLocalAvailable() {
        return local.isAvailable();
    }

    public ExtractionResult extract(List<LabelImageData> images, BeverageType type, Map<FieldName, String> expected) {
        boolean preferCloud = CloudExtractionPipeline.ID.equals(settings.pipelineModel());
        ExtractionPipeline primary = preferCloud ? cloud : local;
        ExtractionPipeline fallback = preferCloud ? local : cloud;

        try {
            return runWithTimeout(primary, images, type, expected);
        } catch (PipelineException e) {
            if (e.isTimeout() || !fallback.isAvailable()) {
                throw e;
            }
            log.warn("{} pipeline failed, falling back to {}: {}", primary.id(), fallback.id(), e.getMessage());
            return runWithTimeout(fallback, images, type, expected);
        }
    }

    private ExtractionResult runWithTimeout(ExtractionPipeline pipeline, List<LabelImageData> images,
                                            BeverageType type, Map<FieldName, String> expected) {
        if (!pipeline.isAvailable()) {
            throw new PipelineException(pipeline.id() + " pipeline is not available", null);
        }
        CompletableFuture<ExtractionResult> future =
                CompletableFuture.supplyAsync(() -> pipeline.extract(images, type, expected), executor);
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new PipelineException("AI pipeline timed out after " + timeout.toSeconds() + " seconds", e, true);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof PipelineException pe) {
                throw pe;
            }
            throw new PipelineException(pipeline.id() + " pipeline failed: " + e.getCause().getMessage(), e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PipelineException("Interrupted", e);
        }
    }
}
