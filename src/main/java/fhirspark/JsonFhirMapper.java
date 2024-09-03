package fhirspark;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.ReferenceClientParam;
import ca.uhn.fhir.rest.gclient.TokenClientParam;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fhirspark.adapter.FollowUpAdapter;
import fhirspark.adapter.MtbAdapter;
import fhirspark.adapter.TherapyRecommendationAdapter;
import fhirspark.definitions.GenomicsReportingEnum;
import fhirspark.definitions.Hl7TerminologyEnum;
import fhirspark.definitions.UriEnum;
import fhirspark.restmodel.CbioportalRest;
import fhirspark.restmodel.Deletions;
import fhirspark.restmodel.FollowUp;
import fhirspark.restmodel.GeneticAlteration;
import fhirspark.restmodel.Mtb;
import fhirspark.restmodel.TherapyRecommendation;
import fhirspark.settings.Settings;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.hl7.fhir.instance.model.api.IAnyResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Identifier.IdentifierUse;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.RelatedArtifact;
import org.hl7.fhir.r4.model.RelatedArtifact.RelatedArtifactType;

/**
 * Fulfils the persistence in HL7 FHIR resources.
 */
public class JsonFhirMapper {

    public static final int TIMEOUT = 60000;

    private static String patientUri;
    private static String therapyRecommendationUri;
    private static String followUpUri;
    private static String responseUri;
    private static String mtbUri;
    private static Settings settings;

    private FhirContext ctx = FhirContext.forR4();
    private IGenericClient client;
    private ObjectMapper objectMapper = new ObjectMapper(new JsonFactory());

    /**
     *
     * Constructs a new FHIR mapper and stores the required configuration.
     *
     * @param settings Settings object with containing configuration
     */
    public JsonFhirMapper(Settings settings) {
        JsonFhirMapper.settings = settings;
        ctx.getRestfulClientFactory().setConnectTimeout(TIMEOUT);
        ctx.getRestfulClientFactory().setSocketTimeout(TIMEOUT);
        this.client = ctx.newRestfulGenericClient(settings.getFhirDbBase());
        MtbAdapter.initialize(settings, client);
        FollowUpAdapter.initialize(settings, client);
        JsonFhirMapper.patientUri = settings.getPatientSystem();
        JsonFhirMapper.therapyRecommendationUri = settings.getObservationSystem();
        JsonFhirMapper.followUpUri = settings.getFollowUpSystem();
        JsonFhirMapper.mtbUri = settings.getDiagnosticReportSystem();
        JsonFhirMapper.responseUri = settings.getResponseSystem();

    }

    /**
     * Retrieves MTB data from FHIR server and transforms it into JSON format for
     * cBioPortal.
     *
     * @param patientId id of the patient.
     * @return JSON representation of the MTB data.
     * @throws JsonProcessingException if the JSON representation could not be created.
     */
    public String mtbToJson(String patientId) throws JsonProcessingException {
        List<Mtb> mtbs = new ArrayList<Mtb>();
        Bundle bPatient = (Bundle) client.search().forResource(Patient.class)
                .where(new TokenClientParam("identifier").exactly().systemAndCode(patientUri, patientId)).prettyPrint()
                .execute();
        Patient fhirPatient = (Patient) bPatient.getEntryFirstRep().getResource();

        if (fhirPatient == null) {
            return this.objectMapper.writeValueAsString(new CbioportalRest().withId(patientId).withMtbs(mtbs));
        }

        Bundle bDiagnosticReports = (Bundle) client.search().forResource(DiagnosticReport.class)
                .where(new ReferenceClientParam("subject").hasId(harmonizeId(fhirPatient))).prettyPrint()
                .include(DiagnosticReport.INCLUDE_BASED_ON)
                .include(DiagnosticReport.INCLUDE_RESULT.asRecursive())
                .include(DiagnosticReport.INCLUDE_SPECIMEN.asRecursive()).execute();

        List<BundleEntryComponent> diagnosticReports = bDiagnosticReports.getEntry();

        for (int i = 0; i < diagnosticReports.size(); i++) {
            if (!(diagnosticReports.get(i).getResource() instanceof DiagnosticReport)) {
                continue;
            }
            DiagnosticReport diagnosticReport = (DiagnosticReport) diagnosticReports.get(i).getResource();
            mtbs.add(MtbAdapter.toJson(settings.getRegex(), patientId, diagnosticReport));

        }

        mtbs.sort(Comparator.comparing(Mtb::getId).reversed());

        return this.objectMapper.writeValueAsString(new CbioportalRest().withId(patientId).withMtbs(mtbs));

    }

    /**
     * Retrieves MTB data from cBioPortal and persists it in FHIR resources.
     */
    public void mtbFromJson(String patientId, List<Mtb> mtbs) throws DataFormatException, IOException {

        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.TRANSACTION);

        Reference fhirPatient = getOrCreatePatient(bundle, patientId);

        for (Mtb mtb : mtbs) {
            MtbAdapter.fromJson(bundle, settings.getRegex(), fhirPatient, patientId, mtb);
        }

        try {
            System.out.println(bundle);
            System.out.println(ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle));

            Bundle resp = client.transaction().withBundle(bundle).execute();

            // Log the response
            System.out.println(ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(resp));
        } catch (UnprocessableEntityException entityException) {
            try (FileWriter f = new FileWriter("error.json")) {
                f.write(entityException.getResponseBody());
            }
        }

    }

    /**
     * Retrieves MTB data from FHIR server and transforms it into JSON format for
     * cBioPortal.
     * @param patientId id of the patient.
     * @return JSON representation of the MTB data.
     * @throws JsonProcessingException if the JSON representation could not be created.
     */
    public String followUpToJson(String patientId) throws JsonProcessingException {
        List<FollowUp> followUps = new ArrayList<FollowUp>();
        Bundle bPatient = (Bundle) client.search().forResource(Patient.class)
                .where(new TokenClientParam("identifier").exactly().systemAndCode(patientUri, patientId)).prettyPrint()
                .execute();
        Patient fhirPatient = (Patient) bPatient.getEntryFirstRep().getResource();

        if (fhirPatient == null) {
            return this.objectMapper.writeValueAsString(
                new CbioportalRest()
                .withId(patientId)
                .withFollowUps(followUps)
            );
        }

        Bundle bMedicationStatements = (Bundle) client.search().forResource(MedicationStatement.class)
                .where(new ReferenceClientParam("subject").hasId(harmonizeId(fhirPatient))).prettyPrint()
                .include(MedicationStatement.INCLUDE_PART_OF)
                .include(MedicationStatement.INCLUDE_CONTEXT.asRecursive())
                .execute();

        List<BundleEntryComponent> medicationStatements = bMedicationStatements.getEntry();

        for (int i = 0; i < medicationStatements.size(); i++) {
            if (!(medicationStatements.get(i).getResource() instanceof MedicationStatement)) {
                continue;
            }
            MedicationStatement medicationStatement = (MedicationStatement) medicationStatements.get(i).getResource();
            followUps.add(FollowUpAdapter.toJson(settings.getRegex(), medicationStatement));

        }

        return this.objectMapper.writeValueAsString(new CbioportalRest().withId(patientId).withFollowUps(followUps));

    }

    /**
     * Retrieves FollowUp data from cBioPortal and persists it in FHIR resources.
     */
    public void followUpFromJson(String patientId, List<FollowUp> followUps) throws DataFormatException, IOException {

        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.TRANSACTION);

        Reference fhirPatient = getOrCreatePatient(bundle, patientId);

        for (FollowUp followUp : followUps) {
            FollowUpAdapter.fromJson(bundle, settings.getRegex(), fhirPatient, patientId, followUp);
        }

        try {
            System.out.println(bundle);
            System.out.println(ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle));

            Bundle resp = client.transaction().withBundle(bundle).execute();

            // Log the response
            System.out.println(ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(resp));
        } catch (UnprocessableEntityException entityException) {
            try (FileWriter f = new FileWriter("error.json")) {
                f.write(entityException.getResponseBody());
            }
        }

    }

    /**
     *
     * @param patientId id of the patient.
     * @param deletions entries that should be deleted. Either MTB or therapy
     *                  recommendation.
     */
    public void deleteEntries(String patientId, Deletions deletions) {
        // deletions.getTherapyRecommendation()
        // .forEach(recommendation -> deleteTherapyRecommendation(patientId,
        // recommendation));
        deletions.getMtb().forEach(mtb -> deleteMtb(patientId, mtb));
        deletions.getFollowUp().forEach(followUp -> deleteFollowUps(patientId, followUp));
        deletions.getTherapyRecommendation()
                .forEach(therapyRecommendationId -> deleteTherapyRecommendation(patientId, therapyRecommendationId));
    }

    private void deleteTherapyRecommendation(String patientId, String therapyRecommendationId) {
        if (!therapyRecommendationId.startsWith(patientId)) {
            throw new IllegalArgumentException("Invalid patientId!");
        }
        client.delete().resourceConditionalByUrl(
                "Observation?identifier=" + therapyRecommendationUri + "|" + therapyRecommendationId).execute();

    }

    private void deleteMtb(String patientId, String mtbId) {
        if (!mtbId.startsWith("mtb_" + patientId + "_")) {
            throw new IllegalArgumentException("Invalid patientId!");
        }
        client.delete().resourceConditionalByUrl("DiagnosticReport?identifier=" + mtbUri + "|" + mtbId).execute();
    }

    private void deleteFollowUps(String patientId, String followUpId) {
        if (!followUpId.startsWith("followUp_" + patientId + "_")) {
            throw new IllegalArgumentException("Invalid patientId!");
        }
        client.delete().resourceConditionalByUrl("MedicationStatement?identifier="
            + followUpUri + "|" + followUpId).execute();

        // Delete RECIST-response observations
        Bundle b = (Bundle) client.search().forResource(Observation.class)
            .where(new TokenClientParam("identifier")
            .hasSystemWithAnyCode(responseUri))
            .prettyPrint().execute();

        for (BundleEntryComponent bec : b.getEntry()) {
            String responseId = ((Observation) bec.getResource()).getIdentifierFirstRep().getValue();
            client.delete().resourceById("Observation", responseId).execute();
        }
    }

    private Reference getOrCreatePatient(Bundle b, String patientId) {

        Patient patient = new Patient();
        patient.setId(IdType.newRandomUuid());
        patient.getIdentifierFirstRep().setSystem(patientUri).setValue(patientId);
        patient.getIdentifierFirstRep().setUse(IdentifierUse.USUAL);
        patient.getIdentifierFirstRep().getType().addCoding(Hl7TerminologyEnum.MR.toCoding());
        b.addEntry().setFullUrl(patient.getIdElement().getValue()).setResource(patient).getRequest()
                .setUrl("Patient?identifier=" + patientUri + "|" + patientId)
                .setIfNoneExist("identifier=" + patientUri + "|" + patientId).setMethod(Bundle.HTTPVerb.PUT);

        return new Reference(patient);
    }

    private String harmonizeId(IAnyResource resource) {
        if (resource.getIdElement().getValue().startsWith("urn:uuid:")) {
            return resource.getIdElement().getValue();
        } else {
            return resource.getIdElement().getResourceType() + "/" + resource.getIdElement().getIdPart();
        }
    }

    /**
     * Fetched Pubmed IDs that have been previously associated with the same
     * alteration.
     *
     * @param alterations List of alterations to consider
     * @return List of matching references
     */
    public Collection<fhirspark.restmodel.Reference> getPmidsByAlteration(List<GeneticAlteration> alterations) {

        Set<String> entrez = new HashSet<>();
        for (GeneticAlteration a : alterations) {
            entrez.add(String.valueOf(a.getEntrezGeneId()));
        }

        Bundle bStuff = (Bundle) client.search().forResource(Observation.class)
                .where(new TokenClientParam("component-value-concept").exactly()
                        .systemAndValues(UriEnum.NCBI_GENE.getUri(), new ArrayList<>(entrez)))
                .prettyPrint().revInclude(Observation.INCLUDE_DERIVED_FROM).execute();

        Map<Integer, fhirspark.restmodel.Reference> refMap = new HashMap<>();

        for (BundleEntryComponent bec : bStuff.getEntry()) {
            Observation o = (Observation) bec.getResource();
            if (!o.getMeta().hasProfile(GenomicsReportingEnum.THERAPEUTIC_IMPLICATION.getSystem())
                && !o.getMeta().hasProfile(GenomicsReportingEnum.MEDICATION_EFFICACY.getSystem())) {
                continue;
            }
            o.getExtensionsByUrl(GenomicsReportingEnum.RELATEDARTIFACT.getSystem()).forEach(relatedArtifact -> {
                if (((RelatedArtifact) relatedArtifact.getValue()).getType() == RelatedArtifactType.CITATION) {
                    Integer pmid = Integer.valueOf(
                            ((RelatedArtifact) relatedArtifact.getValue()).getUrl().replaceFirst(
                                    UriEnum.PUBMED_URI.getUri(),
                                    ""));
                    refMap.put(pmid, new fhirspark.restmodel.Reference().withPmid(pmid)
                            .withName(((RelatedArtifact) relatedArtifact.getValue()).getCitation()));
                }
            });
        }

        return refMap.values();

    }

    /**
     * Fetches therapy recommendations that have been previously associated with the
     * same alteration.
     *
     * @param alterations List of alterations to consider
     * @return List of matching therapies
     */
    public Collection<TherapyRecommendation> getTherapyRecommendationsByAlteration(
            List<GeneticAlteration> alterations) {

        Set<String> entrez = new HashSet<>();
        for (GeneticAlteration a : alterations) {
            entrez.add(String.valueOf(a.getEntrezGeneId()));
        }

        Bundle bStuff = (Bundle) client.search().forResource(Observation.class)
                .where(new TokenClientParam("component-value-concept").exactly()
                        .systemAndValues(UriEnum.NCBI_GENE.getUri(), new ArrayList<>(entrez)))
                .prettyPrint().revInclude(Observation.INCLUDE_DERIVED_FROM).execute();

        Map<String, TherapyRecommendation> tcMap = new HashMap<>();

        for (BundleEntryComponent bec : bStuff.getEntry()) {
            Observation ob = (Observation) bec.getResource();
            if (!ob.getMeta().hasProfile(GenomicsReportingEnum.THERAPEUTIC_IMPLICATION.getSystem())
                && !ob.getMeta().hasProfile(GenomicsReportingEnum.MEDICATION_EFFICACY.getSystem())) {
                continue;
            }

            TherapyRecommendation therapyRecommendation =
                TherapyRecommendationAdapter.toJson(client, settings.getRegex(), ob);

            tcMap.put(ob.getIdentifierFirstRep().getValue(), therapyRecommendation);

        }

        return tcMap.values();

    }

    public Collection<FollowUp> getFollowUpsByAlteration(List<GeneticAlteration> alterations) {

        Set<String> entrez = new HashSet<>();
        for (GeneticAlteration a : alterations) {
            entrez.add(String.valueOf(a.getEntrezGeneId()));
        }

        Bundle bStuff = (Bundle) client.search().forResource(Observation.class)
                .where(new TokenClientParam("component-value-concept").exactly()
                        .systemAndValues(UriEnum.NCBI_GENE.getUri(), new ArrayList<>(entrez)))
                .prettyPrint().revInclude(Observation.INCLUDE_DERIVED_FROM).execute();

        Map<String, FollowUp> tcMap = new HashMap<>();

        ArrayList<Identifier> idents = new ArrayList<>();

        for (BundleEntryComponent bec : bStuff.getEntry()) {
            Observation ob = (Observation) bec.getResource();
            if (!ob.getMeta().hasProfile(GenomicsReportingEnum.THERAPEUTIC_IMPLICATION.getSystem())) {
                continue;
            }
            idents.addAll(ob.getIdentifier());
        }

        Bundle bFollowUps = (Bundle) client.search().forResource(MedicationStatement.class)
            .execute();

        for (BundleEntryComponent bec : bFollowUps.getEntry()) {
            MedicationStatement ms = (MedicationStatement) bec.getResource();
            if (!ms.hasReasonReference()) {
                continue;
            }
            FollowUp followUp = FollowUpAdapter.toJson(settings.getRegex(), ms);

            tcMap.put(ms.getIdentifierFirstRep().getValue(), followUp);

        }
        return tcMap.values();
    }
}
