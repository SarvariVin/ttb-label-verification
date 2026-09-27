package gov.ttb.labelverification.web.api;

import gov.ttb.labelverification.service.ExtractionService;
import gov.ttb.labelverification.service.SettingsService;
import gov.ttb.labelverification.web.api.dto.SettingsView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Specialist-only runtime settings (route-guarded in SecurityConfig, method-guarded in SettingsService). */
@RestController
@RequestMapping("/api/v1/settings")
public class SettingsApiController {

    private final SettingsService settings;
    private final ExtractionService extraction;

    public SettingsApiController(SettingsService settings, ExtractionService extraction) {
        this.settings = settings;
        this.extraction = extraction;
    }

    @GetMapping
    public SettingsView get() {
        SettingsService.SlaTargets sla = settings.slaTargets();
        return new SettingsView(settings.pipelineModel(), settings.approvalThreshold(), sla.reviewResponseHours(),
                sla.totalTurnaroundHours(), sla.maxQueueDepth(), extraction.isCloudAvailable(),
                extraction.isLocalAvailable());
    }

    @PutMapping
    public SettingsView update(@Valid @RequestBody SettingsView body) {
        settings.update(body.pipelineModel(), body.approvalThreshold(),
                new SettingsService.SlaTargets(body.reviewResponseHours(), body.totalTurnaroundHours(),
                        body.maxQueueDepth()));
        return get();
    }
}
