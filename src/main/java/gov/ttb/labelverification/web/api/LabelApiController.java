package gov.ttb.labelverification.web.api;

import gov.ttb.labelverification.domain.LabelStatus;
import gov.ttb.labelverification.security.AppUserPrincipal;
import gov.ttb.labelverification.service.LabelAnalysisService;
import gov.ttb.labelverification.service.LabelApplicationForm;
import gov.ttb.labelverification.service.LabelQueryService;
import gov.ttb.labelverification.service.PrefillService;
import gov.ttb.labelverification.service.ReviewService;
import gov.ttb.labelverification.service.SubmissionService;
import gov.ttb.labelverification.web.Uploads;
import gov.ttb.labelverification.web.api.dto.BatchApproveRequest;
import gov.ttb.labelverification.web.api.dto.LabelDetailView;
import gov.ttb.labelverification.web.api.dto.LabelSummaryView;
import gov.ttb.labelverification.web.api.dto.ReviewRequest;
import gov.ttb.labelverification.web.api.dto.StatusOverrideRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST API (HTTP Basic) exposing the same use cases as the web UI.
 * See docs/api.md for request/response examples.
 */
@RestController
@RequestMapping("/api/v1")
public class LabelApiController {

    private final LabelQueryService queries;
    private final SubmissionService submissions;
    private final ReviewService reviews;
    private final PrefillService prefill;

    public LabelApiController(LabelQueryService queries, SubmissionService submissions, ReviewService reviews,
                              PrefillService prefill) {
        this.queries = queries;
        this.submissions = submissions;
        this.reviews = reviews;
        this.prefill = prefill;
    }

    /** Reads label images and suggests Form 5100.31 values (applicant). Nothing is stored. */
    @PostMapping(value = "/labels/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PrefillService.PrefillResult extract(@RequestParam("images") List<MultipartFile> images) {
        return prefill.extract(Uploads.toImages(images));
    }

    /** Specialists: every label. Applicants: their own submissions. */
    @GetMapping("/labels")
    public List<LabelSummaryView> list(@AuthenticationPrincipal AppUserPrincipal user,
                                       @RequestParam(required = false) String queue) {
        List<LabelQueryService.LabelSummary> rows;
        if (user.isSpecialist()) {
            LabelQueryService.Queues q = queries.specialistQueues();
            rows = switch (queue == null ? "all" : queue) {
                case "ready" -> q.readyToApprove();
                case "review" -> q.needsReview();
                default -> q.all();
            };
        } else {
            rows = queries.applicantSubmissions(user);
        }
        return rows.stream().map(LabelSummaryView::from).toList();
    }

    @GetMapping("/labels/{id}")
    public LabelDetailView get(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable String id) {
        return LabelDetailView.from(queries.detail(user, id), user.isSpecialist());
    }

    /** Multipart: form fields (camelCase, as in {@link LabelApplicationForm}) + one or more "images" parts. */
    @PostMapping(value = "/labels", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SubmissionService.SubmissionResult> submit(
            @AuthenticationPrincipal AppUserPrincipal user,
            @Valid @ModelAttribute LabelApplicationForm form,
            @RequestParam("images") List<MultipartFile> images) {
        SubmissionService.SubmissionResult result = submissions.submit(user, form, Uploads.toImages(images));
        return ResponseEntity.status(result.success() ? HttpStatus.CREATED : HttpStatus.ACCEPTED).body(result);
    }

    @PostMapping("/labels/{id}/review")
    public Map<String, LabelStatus> review(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable String id,
                                           @Valid @RequestBody ReviewRequest request) {
        List<ReviewService.FieldOverride> overrides = request.overrides().stream()
                .map(o -> new ReviewService.FieldOverride(o.validationItemId(), o.resolvedStatus(), o.reviewerNotes()))
                .toList();
        return Map.of("status", reviews.submitReview(user, id, overrides));
    }

    @PostMapping("/labels/{id}/override")
    public ResponseEntity<Void> override(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable String id,
                                         @Valid @RequestBody StatusOverrideRequest request) {
        reviews.overrideStatus(user, id, request.newStatus(), request.justification(), request.reasonCode());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/labels/{id}/reanalyze")
    public LabelAnalysisService.Outcome reanalyze(@PathVariable String id) {
        return reviews.reanalyze(id);
    }

    @PostMapping("/labels/batch-approve")
    public ReviewService.BatchApproveResult batchApprove(@AuthenticationPrincipal AppUserPrincipal user,
                                                         @Valid @RequestBody BatchApproveRequest request) {
        return reviews.batchApprove(user, request.labelIds());
    }

    @GetMapping("/images/{id}")
    public ResponseEntity<byte[]> image(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable String id) {
        LabelQueryService.ImageContent img = queries.image(user, id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(img.contentType()))
                .cacheControl(CacheControl.noStore())
                .body(img.bytes());
    }
}
