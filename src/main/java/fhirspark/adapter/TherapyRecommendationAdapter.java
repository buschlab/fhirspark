package fhirspark.adapter;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.TokenClientParam;
import fhirspark.definitions.GenomicsReportingEnum;
import fhirspark.definitions.LoincEnum;
import fhirspark.definitions.UriEnum;
import fhirspark.resolver.PubmedPublication;
import fhirspark.restmodel.Reasoning;
import fhirspark.restmodel.TherapyRecommendation;
import fhirspark.restmodel.Treatment;
import fhirspark.settings.Regex;
import org.hl7.fhir.r4.model.Annotation;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Observation.ObservationComponentComponent;
import org.hl7.fhir.r4.model.Observation.ObservationStatus;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.RelatedArtifact;
import org.hl7.fhir.r4.model.RelatedArtifact.RelatedArtifactType;
import org.hl7.fhir.r4.model.Task;
import org.hl7.fhir.r4.model.Task.TaskIntent;
import org.hl7.fhir.r4.model.Task.TaskStatus;
import org.hl7.fhir.r4.model.codesystems.ObservationCategory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class TherapyRecommendationAdapter {

    private static PubmedPublication pubmedResolver = new PubmedPublication();
    private static String therapyRecommendationUri;
    private static String patientUri;

    private TherapyRecommendationAdapter() {
    }

    public static void initialize(String newTherapyRecommendationUri, String newPatientUri) {
        TherapyRecommendationAdapter.therapyRecommendationUri = newTherapyRecommendationUri;
        TherapyRecommendationAdapter.patientUri = newPatientUri;
    }

    public static Observation fromJson(Bundle bundle, List<Regex> regex, DiagnosticReport diagnosticReport,
            Reference fhirPatient, TherapyRecommendation therapyRecommendation) {
        Observation efficacyObservation = new Observation();
        efficacyObservation.setId(IdType.newRandomUuid());
        efficacyObservation.getMeta().addProfile(GenomicsReportingEnum.MEDICATION_EFFICACY.getSystem());
        efficacyObservation.setStatus(ObservationStatus.FINAL);
        efficacyObservation.addCategory().addCoding(new Coding(ObservationCategory.LABORATORY.getSystem(),
                ObservationCategory.LABORATORY.toCode(), ObservationCategory.LABORATORY.getDisplay()));
        efficacyObservation.getValueCodeableConcept()
                .addCoding(LoincEnum.PRESUMED_RESPONSIVE.toCoding());
        efficacyObservation.getCode()
                .addCoding(LoincEnum.GENETIC_VARIATIONS_EFFECT_ON_DRUG_EFFICACY.toCoding());
        ObservationComponentComponent evidenceComponent = efficacyObservation.addComponent();
        evidenceComponent.getCode().addCoding(LoincEnum.LEVEL_OF_EVIDENCE.toCoding());
        String m3Text = therapyRecommendation.getEvidenceLevelM3Text() != null
                ? " (" + therapyRecommendation.getEvidenceLevelM3Text() + ")"
                : "";
        evidenceComponent.getValueCodeableConcept().addCoding(new Coding("https://cbioportal.org/evidence/BW/",
                therapyRecommendation.getEvidenceLevel() + " "
                        + therapyRecommendation.getEvidenceLevelExtension() + m3Text,
                therapyRecommendation.getEvidenceLevel() + " "
                        + therapyRecommendation.getEvidenceLevelExtension() + m3Text));

        efficacyObservation.addIdentifier().setSystem(therapyRecommendationUri)
                .setValue(therapyRecommendation.getId());

        efficacyObservation.addPerformer(getOrCreatePractitioner(bundle, therapyRecommendation.getAuthor()));

        therapyRecommendation.getComment()
                .forEach(comment -> efficacyObservation.getNote().add(new Annotation().setText(comment)));

        if (therapyRecommendation.getReasoning() != null) {
            diagnosticReport.getResult().addAll(ReasoningAdapter.fromJson(bundle, efficacyObservation, regex,
                    fhirPatient, therapyRecommendation.getReasoning()));
        }

        if (therapyRecommendation.getReferences() != null) {
            therapyRecommendation.getReferences().forEach(reference -> {
                String title = reference.getName() != null ? reference.getName()
                        : pubmedResolver.resolvePublication(reference.getPmid());
                Extension ex = new Extension().setUrl(GenomicsReportingEnum.RELATEDARTIFACT.getSystem());
                RelatedArtifact relatedArtifact = new RelatedArtifact().setType(RelatedArtifactType.CITATION)
                        .setUrl(UriEnum.PUBMED_URI.getUri() + reference.getPmid()).setCitation(title);
                ex.setValue(relatedArtifact);
                efficacyObservation.addExtension(ex);
            });
        }

        if (therapyRecommendation.getTreatments() != null) {
            therapyRecommendation.getTreatments().forEach(treatment -> {
                Task medicationChange = new Task().setStatus(TaskStatus.REQUESTED)
                        .setIntent(TaskIntent.PROPOSAL).setFor(fhirPatient);
                medicationChange.setId(IdType.newRandomUuid());
                medicationChange.getMeta().addProfile(GenomicsReportingEnum.MEDICATIONCHANGE.getSystem());

                MedicationStatement ms = DrugAdapter.fromJson(fhirPatient, treatment);

                medicationChange.getCode()
                        .addCoding(LoincEnum.CONSIDER_ALTERNATIVE_MEDICATION.toCoding());
                medicationChange.setFocus(new Reference(ms));
                String ncit = ms.getMedicationCodeableConcept().getCodingFirstRep().getCode();
                if (ncit == null) {
                    ncit = treatment.getName();
                }
                medicationChange.addIdentifier(new Identifier().setSystem(UriEnum.NCIT_URI.getUri()).setValue(ncit));

                Extension ex = new Extension().setUrl(GenomicsReportingEnum.RECOMMENDEDACTION.getSystem());
                ex.setValue(new Reference(medicationChange));
                diagnosticReport.addExtension(ex);

                bundle.addEntry().setFullUrl(medicationChange.getIdElement().getValue())
                        .setResource(medicationChange).getRequest()
                        .setUrl("Task?identifier=" + UriEnum.NCIT_URI.getUri() + "|" + ncit + "&subject="
                                + fhirPatient.getResource().getIdElement())
                        .setIfNoneExist("identifier=" + UriEnum.NCIT_URI.getUri() + "|" + ncit + "&subject="
                                + fhirPatient.getResource().getIdElement())
                        .setMethod(Bundle.HTTPVerb.PUT);

                ObservationComponentComponent assessed = efficacyObservation.addComponent();
                assessed.getCode().addCoding(LoincEnum.MEDICATION_ASSESSED.toCoding());
                assessed.setValue(ms.getMedicationCodeableConcept());

            });
        }

        return efficacyObservation;

    }

    private static Reference getOrCreatePractitioner(Bundle b, String credentials) {

        Practitioner practitioner = new Practitioner();
        practitioner.setId(IdType.newRandomUuid());
        practitioner.addIdentifier(new Identifier().setSystem(patientUri).setValue(credentials));
        b.addEntry().setFullUrl(practitioner.getIdElement().getValue()).setResource(practitioner).getRequest()
                .setUrl("Practitioner?identifier=" + patientUri + "|" + credentials)
                .setIfNoneExist("identifier=" + patientUri + "|" + credentials).setMethod(Bundle.HTTPVerb.PUT);

        return new Reference(practitioner);

    }

    public static TherapyRecommendation toJson(IGenericClient client, List<Regex> regex, Observation ob) {
        TherapyRecommendation therapyRecommendation = new TherapyRecommendation()
                .withComment(new ArrayList<>()).withReasoning(new Reasoning());

        if (ob.hasPerformer()) {
            Bundle b2 = (Bundle) client
                    .search().forResource(Practitioner.class).where(new TokenClientParam("_id")
                            .exactly().code(ob.getPerformerFirstRep().getReference()))
                    .prettyPrint().execute();
            Practitioner author = (Practitioner) b2.getEntryFirstRep().getResource();
            therapyRecommendation.setAuthor(author.getIdentifierFirstRep().getValue());
        }

        therapyRecommendation.setId(ob.getIdentifierFirstRep().getValue());

        List<Treatment> treatments = new ArrayList<>();
        therapyRecommendation.setTreatments(treatments);

        List<fhirspark.restmodel.Reference> references = new ArrayList<>();
        ob.getExtensionsByUrl(GenomicsReportingEnum.RELATEDARTIFACT.getSystem()).forEach(relatedArtifact -> {
            if (((RelatedArtifact) relatedArtifact.getValue())
                    .getType() == RelatedArtifactType.CITATION) {
                references.add(new fhirspark.restmodel.Reference()
                        .withPmid(Integer.valueOf(((RelatedArtifact) relatedArtifact.getValue())
                                .getUrl().replaceFirst(UriEnum.PUBMED_URI.getUri(), "")))
                        .withName(((RelatedArtifact) relatedArtifact.getValue()).getCitation()));
            }
        });

        therapyRecommendation.setReferences(references);

        ob.getComponent().forEach(result -> {
            if (result.getCode().getCodingFirstRep().getCode().equals("93044-6")) {
                String[] evidence = result.getValueCodeableConcept().getCodingFirstRep().getCode()
                        .split(" ");
                therapyRecommendation.setEvidenceLevel(evidence[0]);
                if (evidence.length > 1) {
                    therapyRecommendation.setEvidenceLevelExtension(evidence[1]);
                }
                if (evidence.length > 2) {
                    therapyRecommendation.setEvidenceLevelM3Text(
                            String.join(" ", Arrays.asList(evidence).subList(2, evidence.length))
                                    .replace("(", "").replace(")", ""));
                }
            }
            if (result.getCode().getCodingFirstRep().getCode().equals("51963-7")) {
                therapyRecommendation.getTreatments().add(DrugAdapter.toJson(result));
            }
        });

        therapyRecommendation.setReasoning(ReasoningAdapter.toJson(regex, ob.getDerivedFrom(), ob.getHasMember()));

        ob.getNote().forEach(note -> therapyRecommendation.getComment().add(note.getText()));

        return therapyRecommendation;
    }

}