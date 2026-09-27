package gov.ttb.labelverification.web;

import gov.ttb.labelverification.service.UploadedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/** Converts multipart parts to service-layer images, skipping empty file inputs. */
public final class Uploads {

    private Uploads() {
    }

    public static List<UploadedImage> toImages(List<MultipartFile> files) {
        if (files == null) {
            return List.of();
        }
        return files.stream().filter(f -> f != null && !f.isEmpty()).map(Uploads::toImage).toList();
    }

    public static UploadedImage toImage(MultipartFile f) {
        try {
            return new UploadedImage(f.getOriginalFilename(), f.getContentType(), f.getBytes());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read upload", e);
        }
    }
}
