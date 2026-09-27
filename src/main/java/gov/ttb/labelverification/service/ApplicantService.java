package gov.ttb.labelverification.service;

import gov.ttb.labelverification.domain.Applicant;
import gov.ttb.labelverification.repository.ApplicantRepository;
import gov.ttb.labelverification.repository.LabelRepository;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Applicant directory for specialists. */
@Service
@PreAuthorize("hasRole('SPECIALIST')")
public class ApplicantService {

    public record ApplicantRow(Applicant applicant, long labelCount) {
    }

    private final ApplicantRepository applicants;
    private final LabelRepository labels;

    public ApplicantService(ApplicantRepository applicants, LabelRepository labels) {
        this.applicants = applicants;
        this.labels = labels;
    }

    @Transactional(readOnly = true)
    public List<ApplicantRow> list() {
        return applicants.findAllByOrderByCompanyNameAsc().stream()
                .map(a -> new ApplicantRow(a, labels.countByApplicantId(a.getId())))
                .toList();
    }

    @Transactional
    public void updateNotes(String applicantId, String notes) {
        Applicant a = applicants.findById(applicantId).orElseThrow(() -> new NotFoundException("Applicant not found"));
        a.setNotes(notes == null || notes.isBlank() ? null : notes.trim());
    }
}
