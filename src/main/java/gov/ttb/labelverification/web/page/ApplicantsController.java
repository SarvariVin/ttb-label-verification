package gov.ttb.labelverification.web.page;

import gov.ttb.labelverification.service.ApplicantService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/applicants")
public class ApplicantsController {

    private final ApplicantService applicants;

    public ApplicantsController(ApplicantService applicants) {
        this.applicants = applicants;
    }

    @GetMapping
    String list(Model model) {
        model.addAttribute("rows", applicants.list());
        return "applicants";
    }

    @PostMapping("/{id}/notes")
    String notes(@PathVariable String id, @RequestParam(required = false) String notes, RedirectAttributes redirect) {
        applicants.updateNotes(id, notes);
        redirect.addFlashAttribute("flash", "Notes saved.");
        return "redirect:/applicants";
    }
}
