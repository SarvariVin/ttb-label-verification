package gov.ttb.labelverification.web.page;

import gov.ttb.labelverification.domain.ItemStatus;
import gov.ttb.labelverification.domain.LabelStatus;
import gov.ttb.labelverification.domain.ValidationItem;
import gov.ttb.labelverification.security.AppUserPrincipal;
import gov.ttb.labelverification.service.BusinessRuleException;
import gov.ttb.labelverification.service.LabelQueryService;
import gov.ttb.labelverification.service.ReviewService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Label detail, specialist review/override/re-analyze, batch approve, and image serving. */
@Controller
public class LabelPageController {

    private final LabelQueryService queries;
    private final ReviewService reviews;

    public LabelPageController(LabelQueryService queries, ReviewService reviews) {
        this.queries = queries;
        this.reviews = reviews;
    }

    @GetMapping("/labels/{id}")
    String detail(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable String id, Model model) {
        model.addAttribute("d", queries.detail(user, id));
        model.addAttribute("decisions", List.of(LabelStatus.APPROVED, LabelStatus.CONDITIONALLY_APPROVED,
                LabelStatus.NEEDS_CORRECTION, LabelStatus.REJECTED));
        model.addAttribute("resolutions", List.of(ItemStatus.MATCH, ItemStatus.MISMATCH, ItemStatus.NOT_FOUND));
        return "label-detail";
    }

    /** Form fields: resolved_{itemId}=MATCH|MISMATCH|NOT_FOUND and notes_{itemId}. */
    @PostMapping("/labels/{id}/review")
    String review(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable String id,
                  @RequestParam Map<String, String> params, RedirectAttributes redirect) {
        List<ReviewService.FieldOverride> overrides = new ArrayList<>();
        for (ValidationItem item : queries.detail(user, id).items()) {
            String resolved = params.get("resolved_" + item.getId());
            if (resolved == null || resolved.isBlank() || resolved.equals(item.getStatus().name())) {
                continue;
            }
            overrides.add(new ReviewService.FieldOverride(item.getId(), ItemStatus.valueOf(resolved),
                    params.get("notes_" + item.getId())));
        }
        return run(redirect, id, () -> {
            LabelStatus status = reviews.submitReview(user, id, overrides);
            return "Review saved. Label is now " + status.displayName().toLowerCase() + ".";
        });
    }

    @PostMapping("/labels/{id}/override")
    String override(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable String id,
                    @RequestParam LabelStatus newStatus, @RequestParam String justification,
                    @RequestParam(required = false) String reasonCode, RedirectAttributes redirect) {
        return run(redirect, id, () -> {
            reviews.overrideStatus(user, id, newStatus, justification, reasonCode);
            return "Status changed to " + newStatus.displayName().toLowerCase() + ".";
        });
    }

    @PostMapping("/labels/{id}/reanalyze")
    String reanalyze(@PathVariable String id, RedirectAttributes redirect) {
        return run(redirect, id, () -> {
            var outcome = reviews.reanalyze(id);
            return "Re-analyzed with " + outcome.modelUsed() + ". AI proposes: "
                    + outcome.proposedStatus().displayName().toLowerCase() + ".";
        });
    }

    @PostMapping("/labels/batch-approve")
    String batchApprove(@AuthenticationPrincipal AppUserPrincipal user,
                        @RequestParam(name = "labelIds", required = false) List<String> labelIds,
                        RedirectAttributes redirect) {
        try {
            ReviewService.BatchApproveResult r = reviews.batchApprove(user, labelIds);
            redirect.addFlashAttribute("flash", "Approved " + r.approvedCount() + " label(s)"
                    + (r.failedIds().isEmpty() ? "." : "; " + r.failedIds().size() + " no longer eligible."));
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/?tab=ready";
    }

    @GetMapping("/images/{id}")
    ResponseEntity<byte[]> image(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable String id) {
        LabelQueryService.ImageContent img = queries.image(user, id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(img.contentType()))
                .cacheControl(CacheControl.noStore())
                .body(img.bytes());
    }

    private static String run(RedirectAttributes redirect, String id, java.util.function.Supplier<String> action) {
        try {
            redirect.addFlashAttribute("flash", action.get());
        } catch (BusinessRuleException | IllegalArgumentException e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/labels/" + id;
    }
}
