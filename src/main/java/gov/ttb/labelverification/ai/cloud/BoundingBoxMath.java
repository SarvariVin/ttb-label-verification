package gov.ttb.labelverification.ai.cloud;

import gov.ttb.labelverification.ai.BoundingBox;
import gov.ttb.labelverification.ai.ocr.OcrWord;
import java.util.List;

/** Geometry helpers for turning OCR word boxes into normalized field boxes. */
public final class BoundingBoxMath {

    private BoundingBoxMath() {
    }

    /**
     * Union of the given word boxes, normalized to 0–1 of the image size.
     * Returns null if there are no words or the image size is unknown.
     */
    public static BoundingBox unionNormalized(List<OcrWord> words, int imageWidth, int imageHeight) {
        if (words == null || words.isEmpty() || imageWidth <= 0 || imageHeight <= 0) {
            return null;
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (OcrWord w : words) {
            minX = Math.min(minX, w.x());
            minY = Math.min(minY, w.y());
            maxX = Math.max(maxX, w.x() + w.width());
            maxY = Math.max(maxY, w.y() + w.height());
        }
        return new BoundingBox(
                clamp((double) minX / imageWidth),
                clamp((double) minY / imageHeight),
                clamp((double) (maxX - minX) / imageWidth),
                clamp((double) (maxY - minY) / imageHeight),
                dominantAngle(words));
    }

    /** Vertical text (tall, narrow words) reads at 90°; everything else at 0°. */
    static double dominantAngle(List<OcrWord> words) {
        long vertical = words.stream()
                .filter(w -> w.text().length() > 2 && w.height() > w.width() * 1.5)
                .count();
        return vertical * 2 > words.size() ? 90 : 0;
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }
}
