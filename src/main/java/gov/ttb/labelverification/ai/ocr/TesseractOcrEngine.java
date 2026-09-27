package gov.ttb.labelverification.ai.ocr;

import gov.ttb.labelverification.ai.PipelineException;
import gov.ttb.labelverification.config.AppProperties;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Semaphore;
import javax.imageio.ImageIO;
import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import net.sourceforge.tess4j.Word;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Local OCR via Tess4J (JNA binding to the native libtesseract).
 * <p>
 * Label images have decorative fonts, embossing and small print that one pass
 * misses, so each image is read twice: sparse-text mode (scattered front-label
 * text) and single-block mode (dense back-label text such as the health
 * warning). Lines from both passes are merged and de-duplicated.
 * <p>
 * Tesseract instances are not thread-safe; one is created per pass.
 */
@Component
public class TesseractOcrEngine implements OcrEngine {

    private static final Logger log = LoggerFactory.getLogger(TesseractOcrEngine.class);
    private static final int TARGET_WIDTH = 2048;
    private static final int UPSCALE_BELOW = 1024;

    private static final List<String> LIBRARY_CANDIDATES = List.of(
            "/opt/homebrew/lib", "/usr/local/lib", "/usr/lib/x86_64-linux-gnu", "/usr/lib/aarch64-linux-gnu", "/usr/lib");
    private static final List<String> TESSDATA_CANDIDATES = List.of(
            "/opt/homebrew/share/tessdata", "/usr/local/share/tessdata",
            "/usr/share/tesseract-ocr/5/tessdata", "/usr/share/tesseract-ocr/4.00/tessdata", "/usr/share/tessdata");

    private final String tessdataPath;
    private final String language;
    private final boolean available;
    private final Semaphore permits;

    public TesseractOcrEngine(AppProperties properties) {
        AppProperties.Ocr ocr = properties.ocr();
        this.language = ocr.language() == null ? "eng" : ocr.language();
        this.permits = new Semaphore(Math.max(1, ocr.maxConcurrent()), true);

        String libraryPath = firstExisting(ocr.libraryPath(), LIBRARY_CANDIDATES, "libtesseract");
        if (libraryPath != null) {
            String existing = System.getProperty("jna.library.path");
            System.setProperty("jna.library.path",
                    existing == null || existing.isBlank() ? libraryPath : existing + ":" + libraryPath);
        }
        this.tessdataPath = firstExisting(ocr.tessdataPath(), TESSDATA_CANDIDATES, language + ".traineddata");
        this.available = tessdataPath != null;
        if (available) {
            log.info("Tesseract OCR configured: tessdata={}, library={}", tessdataPath, libraryPath);
        } else {
            log.warn("Tesseract tessdata not found — local pipeline disabled. Install tesseract "
                    + "(brew install tesseract / apt-get install tesseract-ocr) or set TESSDATA_PREFIX.");
        }
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public OcrResult recognize(byte[] imageBytes) {
        if (!available) {
            throw new PipelineException("Tesseract is not installed or tessdata was not found", null);
        }
        acquire();
        try {
            return recognizeNow(imageBytes);
        } finally {
            permits.release();
        }
    }

    private OcrResult recognizeNow(byte[] imageBytes) {
        BufferedImage original = decode(imageBytes);
        BufferedImage prepared = preprocess(original);

        List<String> lines = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int psm : new int[]{ITessAPI.TessPageSegMode.PSM_SPARSE_TEXT, ITessAPI.TessPageSegMode.PSM_SINGLE_BLOCK}) {
            String text = runPass(prepared, psm);
            for (String line : text.split("\n")) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && seen.add(trimmed.toLowerCase(Locale.ROOT))) {
                    lines.add(trimmed);
                }
            }
        }
        return new OcrResult(String.join("\n", lines), List.of(), original.getWidth(), original.getHeight());
    }

    /**
     * Text lines in reading order with their pixel height, from Tesseract's automatic
     * layout analysis. Line height lets the pre-fill extractor tell the brand (largest
     * type) from body text.
     */
    public List<OcrLine> recognizeLines(byte[] imageBytes) {
        if (!available) {
            throw new PipelineException("Tesseract is not installed or tessdata was not found", null);
        }
        acquire();
        try {
            BufferedImage prepared = preprocess(decode(imageBytes));
            Tesseract tesseract = newTesseract(ITessAPI.TessPageSegMode.PSM_AUTO);
            List<OcrLine> lines = new ArrayList<>();
            for (Word w : tesseract.getWords(prepared, ITessAPI.TessPageIteratorLevel.RIL_TEXTLINE)) {
                String text = w.getText() == null ? "" : w.getText().trim();
                if (!text.isEmpty()) {
                    lines.add(new OcrLine(text, w.getBoundingBox().height, w.getBoundingBox().y));
                }
            }
            return lines;
        } catch (UnsatisfiedLinkError e) {
            throw new PipelineException("Tesseract OCR failed: " + e.getMessage(), e);
        } finally {
            permits.release();
        }
    }

    /** Bounds concurrent OCR so a burst of uploads cannot exhaust a small container's memory. */
    private void acquire() {
        try {
            permits.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PipelineException("Interrupted while waiting for OCR capacity", e);
        }
    }

    private Tesseract newTesseract(int psm) {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(tessdataPath);
        tesseract.setLanguage(language);
        tesseract.setPageSegMode(psm);
        tesseract.setOcrEngineMode(ITessAPI.TessOcrEngineMode.OEM_LSTM_ONLY);
        return tesseract;
    }

    private String runPass(BufferedImage image, int psm) {
        try {
            return newTesseract(psm).doOCR(image);
        } catch (TesseractException | UnsatisfiedLinkError e) {
            throw new PipelineException("Tesseract OCR failed: " + e.getMessage(), e);
        }
    }

    private static BufferedImage decode(byte[] bytes) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            if (img == null) {
                throw new PipelineException("Unsupported image format for local OCR (use JPEG or PNG)", null);
            }
            return img;
        } catch (IOException e) {
            throw new PipelineException("Could not decode image", e);
        }
    }

    /** Grayscale, and upscale small images so small print is legible to Tesseract. */
    static BufferedImage preprocess(BufferedImage src) {
        int width = src.getWidth();
        int height = src.getHeight();
        if (width < UPSCALE_BELOW) {
            double scale = (double) TARGET_WIDTH / width;
            width = TARGET_WIDTH;
            height = (int) Math.round(height * scale);
        }
        BufferedImage gray = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = gray.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(src, 0, 0, width, height, null);
        g.dispose();
        return gray;
    }

    private static String firstExisting(String configured, List<String> candidates, String marker) {
        List<String> all = new ArrayList<>();
        if (configured != null && !configured.isBlank()) {
            all.add(configured);
        }
        all.addAll(candidates);
        for (String dir : all) {
            Path path = Path.of(dir);
            if (!Files.isDirectory(path)) {
                continue;
            }
            try (var files = Files.list(path)) {
                if (files.anyMatch(f -> f.getFileName().toString().startsWith(marker))) {
                    return dir;
                }
            } catch (IOException ignored) {
                // try next candidate
            }
        }
        return null;
    }
}
