package fhirspark.adapter;

import fhirspark.definitions.GenomicsReportingEnum;
import fhirspark.definitions.UriEnum;
import fhirspark.restmodel.ClinicalTrial;
import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Observation.ObservationComponentComponent;

public final class ClinicalTrialAdapter {

    private ClinicalTrialAdapter() {
    }

    public static List<ObservationComponentComponent> fromJson(List<ClinicalTrial> trials) {
        // ObservationComponentComponent eligibility = new
        // ObservationComponentComponent();
        // eligibility.getCode().addCoding(GenomicsReportingEnum.PREDICTED_THERAPEUTIC_IMPLICATION.toCoding());
        // eligibility.getValueCodeableConcept().addCoding(SnomedEnum.PATIENT_ELIGIBLE_FOR_CLINICAL_TRIAL.toCoding());

        if (trials.isEmpty()) {
            return null;
        }
        ObservationComponentComponent fhirTrial = new ObservationComponentComponent();
        fhirTrial.getCode().addCoding(GenomicsReportingEnum.ASSOCIATED_THERAPY.toCoding());
        for (ClinicalTrial trial : trials) {
            fhirTrial.getValueCodeableConcept()
                    .addCoding(new Coding(UriEnum.CLINICALTRIALS.getUri(), trial.getId(), trial.getName()));
        }

        return List.of(fhirTrial);
        // return List.of(eligibility, fhirTrial);
    }

    public static List<ClinicalTrial> toJson(ObservationComponentComponent fhirTrials) {
        List<ClinicalTrial> trials = new ArrayList<>();
        for (Coding c : fhirTrials.getValueCodeableConcept().getCoding()) {
            ClinicalTrial t = new ClinicalTrial();
            t.setId(c.getCode());
            t.setName(c.getDisplay());
            trials.add(t);
        }

        return trials;
    }

}
