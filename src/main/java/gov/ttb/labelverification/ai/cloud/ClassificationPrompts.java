package gov.ttb.labelverification.ai.cloud;

import gov.ttb.labelverification.regulatory.BeverageType;
import gov.ttb.labelverification.regulatory.FieldName;
import gov.ttb.labelverification.regulatory.HealthWarning;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Beverage-type-aware prompts for the Stage 2 field classifier. */
public final class ClassificationPrompts {

    private ClassificationPrompts() {
    }

    public static String system() {
        return """
                You are a TTB (Alcohol and Tobacco Tax and Trade Bureau) label compliance assistant.
                You receive OCR output from alcohol beverage label images as an indexed word list.
                Classify the words into TTB regulatory fields from Form 5100.31.

                Rules:
                - Copy text exactly as it appears in the OCR words; never correct spelling or casing.
                - For each field, list the indices of every OCR word that forms the value (in reading order).
                - If a field is not present on the label, return value null and an empty wordIndices list.
                - The health warning begins with "%s" and must be returned in full.
                - Confidence is 0-100: how sure you are the text is that field.
                - Classify each image as front, back, neck, strip or other.
                """.formatted(HealthWarning.PREFIX);
    }

    public static String user(BeverageType beverageType, List<IndexedWord> words,
                              Map<FieldName, String> applicationData, int imageCount) {
        StringBuilder sb = new StringBuilder();
        if (beverageType != null) {
            sb.append("Beverage type: ").append(beverageType.label())
                    .append(" (27 CFR Part ").append(beverageType.cfrPart()).append(")\n");
            sb.append("Mandatory fields: ").append(keys(beverageType.mandatoryFields())).append('\n');
            sb.append("Optional fields: ").append(keys(beverageType.optionalFields())).append("\n\n");
        } else {
            sb.append("Beverage type: unknown — detect it (DISTILLED_SPIRITS, WINE or MALT_BEVERAGE).\n\n");
        }
        if (applicationData != null && !applicationData.isEmpty()) {
            sb.append("The applicant declared these values. Use them only to disambiguate which text is which ")
                    .append("field; report what the label actually says:\n");
            applicationData.forEach((k, v) -> sb.append("- ").append(k.key()).append(": ").append(v).append('\n'));
            sb.append('\n');
        }
        sb.append("Image count: ").append(imageCount).append("\n");
        sb.append("OCR words (index|image|text):\n");
        for (IndexedWord w : words) {
            sb.append(w.index()).append('|').append(w.imageIndex()).append('|').append(w.text()).append('\n');
        }
        return sb.toString();
    }

    private static String keys(List<FieldName> fields) {
        return fields.stream().map(FieldName::key).collect(Collectors.joining(", "));
    }

    /** A word with a global index across all images. */
    public record IndexedWord(int index, int imageIndex, String text) {
    }
}
