package gov.ttb.labelverification.web.page;

import gov.ttb.labelverification.service.ExtractionService;
import gov.ttb.labelverification.service.SettingsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Specialist-only settings: pipeline, approval threshold, SLA targets. */
@Controller
@RequestMapping("/settings")
public class SettingsController {

    private final SettingsService settings;
    private final ExtractionService extraction;

    public SettingsController(SettingsService settings, ExtractionService extraction) {
        this.settings = settings;
        this.extraction = extraction;
    }

    @GetMapping
    String view(Model model) {
        model.addAttribute("pipelineModel", settings.pipelineModel());
        model.addAttribute("threshold", settings.approvalThreshold());
        model.addAttribute("sla", settings.slaTargets());
        model.addAttribute("strictness", settings.fieldStrictness());
        model.addAttribute("cloudAvailable", extraction.isCloudAvailable());
        model.addAttribute("localAvailable", extraction.isLocalAvailable());
        return "settings";
    }

    @PostMapping
    String save(@RequestParam String pipelineModel, @RequestParam int approvalThreshold,
                @RequestParam int reviewResponseHours, @RequestParam int totalTurnaroundHours,
                @RequestParam int maxQueueDepth, RedirectAttributes redirect) {
        try {
            settings.update(pipelineModel, approvalThreshold,
                    new SettingsService.SlaTargets(reviewResponseHours, totalTurnaroundHours, maxQueueDepth));
            redirect.addFlashAttribute("flash", "Settings saved.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/settings";
    }
}
