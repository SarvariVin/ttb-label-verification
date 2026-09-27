package gov.ttb.labelverification.service;

import gov.ttb.labelverification.regulatory.BeverageType;
import gov.ttb.labelverification.regulatory.RegulatoryConstants;
import gov.ttb.labelverification.security.AppUserPrincipal;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * CSV batch submission (up to 50 rows). Each row references one or more image
 * filenames (semicolon-separated) from the uploaded image set. Rows are
 * independent: a bad row is reported and the rest still go through.
 * <p>
 * Columns: beverage_type, container_size_ml, brand_name, images, and optionally
 * serial_number, fanciful_name, class_type, class_type_code, alcohol_content,
 * net_contents, name_and_address, qualifying_phrase, country_of_origin,
 * grape_varietal, appellation_of_origin, vintage_year, sulfite_declaration,
 * age_statement, state_of_distillation.
 */
@Service
public class BatchSubmissionService {

    public record RowResult(int rowNumber, String brandName, String labelId, String status, String error) {
    }

    private static final Set<String> REQUIRED_COLUMNS =
            Set.of("beverage_type", "container_size_ml", "brand_name", "images");

    private final SubmissionService submissions;
    private final Validator validator;

    public BatchSubmissionService(SubmissionService submissions, Validator validator) {
        this.submissions = submissions;
        this.validator = validator;
    }

    @PreAuthorize("hasRole('APPLICANT')")
    public List<RowResult> submit(AppUserPrincipal user, String csv, Map<String, UploadedImage> imagesByName) {
        List<CSVRecord> rows = parse(csv);
        if (rows.isEmpty()) {
            throw new BusinessRuleException("CSV has no data rows");
        }
        if (rows.size() > RegulatoryConstants.MAX_BATCH_ROWS) {
            throw new BusinessRuleException("Maximum " + RegulatoryConstants.MAX_BATCH_ROWS + " rows per batch");
        }

        List<RowResult> out = new ArrayList<>();
        for (CSVRecord row : rows) {
            int rowNumber = (int) row.getRecordNumber() + 1; // +1 for header line
            String brand = get(row, "brand_name");
            try {
                LabelApplicationForm form = toForm(row);
                Set<ConstraintViolation<LabelApplicationForm>> violations = validator.validate(form);
                if (!violations.isEmpty()) {
                    throw new BusinessRuleException(violations.stream()
                            .map(ConstraintViolation::getMessage).sorted().collect(Collectors.joining("; ")));
                }
                List<UploadedImage> images = new ArrayList<>();
                for (String name : splitImages(get(row, "images"))) {
                    UploadedImage img = imagesByName.get(name);
                    if (img == null) {
                        throw new BusinessRuleException("Image \"" + name + "\" was not uploaded");
                    }
                    images.add(img);
                }
                SubmissionService.SubmissionResult r = submissions.submit(user, form, images);
                out.add(new RowResult(rowNumber, brand, r.labelId(),
                        r.aiProposedStatus() == null ? r.status().name() : r.aiProposedStatus().name(), r.error()));
            } catch (RuntimeException e) {
                out.add(new RowResult(rowNumber, brand, null, "FAILED", e.getMessage()));
            }
        }
        return out;
    }

    private static List<CSVRecord> parse(String csv) {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader().setSkipHeaderRecord(true).setTrim(true).setIgnoreEmptyLines(true).build();
        try (CSVParser parser = CSVParser.parse(new StringReader(csv == null ? "" : csv), format)) {
            Set<String> headers = parser.getHeaderMap().keySet().stream()
                    .map(h -> h.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
            List<String> missing = REQUIRED_COLUMNS.stream().filter(c -> !headers.contains(c)).sorted().toList();
            if (!missing.isEmpty()) {
                throw new BusinessRuleException("CSV is missing required columns: " + String.join(", ", missing));
            }
            return parser.getRecords();
        } catch (IOException | IllegalArgumentException e) {
            throw new BusinessRuleException("Could not read CSV: " + e.getMessage());
        }
    }

    static LabelApplicationForm toForm(CSVRecord row) {
        LabelApplicationForm f = new LabelApplicationForm();
        String type = get(row, "beverage_type");
        if (type != null) {
            try {
                f.setBeverageType(BeverageType.valueOf(type.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                throw new BusinessRuleException("beverage_type must be distilled_spirits, wine, or malt_beverage");
            }
        }
        String size = get(row, "container_size_ml");
        if (size != null) {
            try {
                f.setContainerSizeMl(Integer.parseInt(size));
            } catch (NumberFormatException e) {
                throw new BusinessRuleException("container_size_ml must be a whole number");
            }
        }
        f.setBrandName(get(row, "brand_name"));
        f.setSerialNumber(get(row, "serial_number"));
        f.setFancifulName(get(row, "fanciful_name"));
        f.setClassType(get(row, "class_type"));
        f.setClassTypeCode(get(row, "class_type_code"));
        f.setAlcoholContent(get(row, "alcohol_content"));
        f.setNetContents(get(row, "net_contents"));
        f.setNameAndAddress(get(row, "name_and_address"));
        f.setQualifyingPhrase(get(row, "qualifying_phrase"));
        f.setCountryOfOrigin(get(row, "country_of_origin"));
        f.setGrapeVarietal(get(row, "grape_varietal"));
        f.setAppellationOfOrigin(get(row, "appellation_of_origin"));
        f.setVintageYear(get(row, "vintage_year"));
        f.setAgeStatement(get(row, "age_statement"));
        f.setStateOfDistillation(get(row, "state_of_distillation"));
        String sulfites = get(row, "sulfite_declaration");
        if (sulfites != null) {
            f.setSulfiteDeclaration(sulfites.equalsIgnoreCase("true") ? Boolean.TRUE
                    : sulfites.equalsIgnoreCase("false") ? Boolean.FALSE : null);
        }
        return f;
    }

    private static List<String> splitImages(String value) {
        if (value == null) {
            return List.of();
        }
        return Arrays.stream(value.split(";")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static String get(CSVRecord row, String column) {
        if (!row.isMapped(column) || !row.isSet(column)) {
            return null;
        }
        String v = row.get(column);
        return v == null || v.isBlank() ? null : v.trim();
    }
}
