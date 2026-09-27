package gov.ttb.labelverification.web.page;

import gov.ttb.labelverification.ai.PipelineException;
import gov.ttb.labelverification.regulatory.BeverageType;
import gov.ttb.labelverification.regulatory.HealthWarning;
import gov.ttb.labelverification.regulatory.QualifyingPhrases;
import gov.ttb.labelverification.security.AppUserPrincipal;
import gov.ttb.labelverification.service.BatchSubmissionService;
import gov.ttb.labelverification.service.BusinessRuleException;
import gov.ttb.labelverification.service.LabelApplicationForm;
import gov.ttb.labelverification.service.PrefillService;
import gov.ttb.labelverification.service.SubmissionService;
import gov.ttb.labelverification.service.UploadedImage;
import gov.ttb.labelverification.storage.ImageFileValidator;
import gov.ttb.labelverification.web.Uploads;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Applicant submission pages: single label and CSV batch. */
@Controller
@RequestMapping("/submit")
public class SubmitController {

    private final SubmissionService submissions;
    private final BatchSubmissionService batch;
    private final PrefillService prefill;

    public SubmitController(SubmissionService submissions, BatchSubmissionService batch, PrefillService prefill) {
        this.submissions = submissions;
        this.batch = batch;
        this.prefill = prefill;
    }

    /**
     * Reads the selected images and returns suggested form values as JSON.
     * Called by app.js as soon as images are chosen; nothing is stored.
     */
    @PostMapping(value = "/extract", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    ResponseEntity<?> extract(@RequestParam(name = "images", required = false) List<MultipartFile> images) {
        try {
            return ResponseEntity.ok(prefill.extract(Uploads.toImages(images)));
        } catch (BusinessRuleException | ImageFileValidator.InvalidImageException e) {
            return ResponseEntity.unprocessableEntity().body(Map.of("error", e.getMessage()));
        } catch (PipelineException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "The label could not be read automatically: " + e.getMessage()));
        }
    }

    @ModelAttribute
    void referenceData(Model model) {
        model.addAttribute("beverageTypes", BeverageType.values());
        model.addAttribute("qualifyingPhrases", QualifyingPhrases.ALL);
        model.addAttribute("healthWarning", HealthWarning.FULL_TEXT);
    }

    @GetMapping
    String form(@RequestParam(required = false) String priorLabelId, Model model) {
        LabelApplicationForm form = new LabelApplicationForm();
        form.setPriorLabelId(priorLabelId);
        model.addAttribute("form", form);
        return "submit";
    }

    @PostMapping
    String submit(@AuthenticationPrincipal AppUserPrincipal user,
                  @Valid @ModelAttribute("form") LabelApplicationForm form, BindingResult binding,
                  @RequestParam(name = "images", required = false) List<MultipartFile> images,
                  Model model, RedirectAttributes redirect) {
        List<UploadedImage> uploads = Uploads.toImages(images);
        if (uploads.isEmpty()) {
            model.addAttribute("uploadError", "Upload at least one label image (JPEG or PNG).");
        }
        if (binding.hasErrors() || uploads.isEmpty()) {
            return "submit";
        }
        try {
            SubmissionService.SubmissionResult result = submissions.submit(user, form, uploads);
            redirect.addFlashAttribute("flash", result.success()
                    ? "Submitted. Your label was analyzed and is now awaiting specialist review."
                    : result.error());
            return "redirect:/labels/" + result.labelId();
        } catch (BusinessRuleException | ImageFileValidator.InvalidImageException e) {
            model.addAttribute("uploadError", e.getMessage());
            return "submit";
        }
    }

    @GetMapping("/batch")
    String batchForm() {
        return "submit-batch";
    }

    @PostMapping("/batch")
    String batchSubmit(@AuthenticationPrincipal AppUserPrincipal user,
                       @RequestParam("csv") MultipartFile csv,
                       @RequestParam(name = "images", required = false) List<MultipartFile> images,
                       Model model) throws IOException {
        Map<String, UploadedImage> byName = new LinkedHashMap<>();
        for (UploadedImage img : Uploads.toImages(images)) {
            byName.put(img.filename(), img);
        }
        try {
            model.addAttribute("results",
                    batch.submit(user, new String(csv.getBytes(), StandardCharsets.UTF_8), byName));
        } catch (BusinessRuleException e) {
            model.addAttribute("error", e.getMessage());
        }
        return "submit-batch";
    }
}
