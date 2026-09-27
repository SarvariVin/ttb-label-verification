package gov.ttb.labelverification.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Typed binding for the {@code app.*} block in application.yml. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @DefaultValue Storage storage,
        @DefaultValue Seed seed,
        @DefaultValue Pipeline pipeline,
        @DefaultValue Ocr ocr,
        @DefaultValue Cloud cloud) {

    /**
     * @param type "filesystem" (default, needs a persistent disk) or "database" (images stored
     *             in PostgreSQL — for platforms that allow only one volume, such as Railway's free plan)
     */
    public record Storage(@DefaultValue("filesystem") String type,
                          @DefaultValue("./data/uploads") String directory) {
    }

    /**
     * Bootstrap accounts for an empty database. {@code password} should come from a
     * secret; when blank a random one is generated and logged once.
     */
    public record Seed(@DefaultValue("true") boolean enabled,
                       String password,
                       @DefaultValue("false") boolean resetPassword,
                       @DefaultValue("specialist@example.gov") String specialistEmail,
                       @DefaultValue("applicant@example.com") String applicantEmail,
                       @DefaultValue("Sample Distilling Co.") String applicantCompany) {
    }

    public record Pipeline(@DefaultValue("60s") Duration timeout) {
    }

    /**
     * Blank paths are auto-detected from common Homebrew / Debian locations.
     *
     * @param maxConcurrent OCR jobs allowed at once; each holds a decoded image and Tesseract's
     *                      native buffers, so small containers should use 1
     */
    public record Ocr(String tessdataPath, String libraryPath, @DefaultValue("eng") String language,
                      @DefaultValue("2") int maxConcurrent) {
    }

    public record Cloud(String googleVisionApiKey, String openaiApiKey,
                        @DefaultValue("gpt-4.1-nano") String openaiModel,
                        @DefaultValue("https://api.openai.com/v1") String openaiBaseUrl) {

        public boolean hasGoogleVision() {
            return googleVisionApiKey != null && !googleVisionApiKey.isBlank();
        }

        public boolean hasOpenAi() {
            return openaiApiKey != null && !openaiApiKey.isBlank();
        }

        /** Cloud pipeline requires both OCR (Vision) and classification (OpenAI). */
        public boolean isConfigured() {
            return hasGoogleVision() && hasOpenAi();
        }
    }
}
