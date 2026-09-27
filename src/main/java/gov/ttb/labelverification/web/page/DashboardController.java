package gov.ttb.labelverification.web.page;

import gov.ttb.labelverification.security.AppUserPrincipal;
import gov.ttb.labelverification.service.LabelQueryService;
import gov.ttb.labelverification.service.SettingsService;
import gov.ttb.labelverification.service.SlaMetricsService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Role-specific home page: specialist queues + SLA, or applicant submissions. */
@Controller
public class DashboardController {

    private final LabelQueryService queries;
    private final SlaMetricsService sla;
    private final SettingsService settings;

    public DashboardController(LabelQueryService queries, SlaMetricsService sla, SettingsService settings) {
        this.queries = queries;
        this.sla = sla;
        this.settings = settings;
    }

    @GetMapping("/")
    String dashboard(@AuthenticationPrincipal AppUserPrincipal user,
                     @RequestParam(defaultValue = "ready") String tab, Model model) {
        if (user.isSpecialist()) {
            model.addAttribute("queues", queries.specialistQueues());
            model.addAttribute("sla", sla.summary());
            model.addAttribute("threshold", settings.approvalThreshold());
            model.addAttribute("tab", tab);
            return "dashboard-specialist";
        }
        model.addAttribute("submissions", queries.applicantSubmissions(user));
        return "dashboard-applicant";
    }
}
