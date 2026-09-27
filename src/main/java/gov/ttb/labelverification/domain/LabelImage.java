package gov.ttb.labelverification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "label_images")
public class LabelImage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "label_id")
    private Label label;

    /** Opaque key understood by {@link gov.ttb.labelverification.storage.ImageStorage}. */
    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "image_filename", nullable = false)
    private String imageFilename;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "image_type", nullable = false)
    private ImageType imageType;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected LabelImage() {
    }

    public LabelImage(String storageKey, String imageFilename, String contentType, ImageType imageType, int sortOrder) {
        this.storageKey = storageKey;
        this.imageFilename = imageFilename;
        this.contentType = contentType;
        this.imageType = imageType;
        this.sortOrder = sortOrder;
    }

    public Label getLabel() {
        return label;
    }

    void setLabel(Label label) {
        this.label = label;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getImageFilename() {
        return imageFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public ImageType getImageType() {
        return imageType;
    }

    public void setImageType(ImageType imageType) {
        this.imageType = imageType;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
