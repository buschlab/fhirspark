package fhirspark.adapter;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.TokenClientParam;
import fhirspark.definitions.GenomicsReportingEnum;
import fhirspark.definitions.MolekulargenetischerBefundberichtEnum;
import fhirspark.definitions.ResponseEnum;
import fhirspark.definitions.SnomedEnum;
import fhirspark.restmodel.FollowUp;
import fhirspark.restmodel.ResponseCriteria;
import fhirspark.restmodel.TherapyRecommendation;
import fhirspark.settings.Regex;
import fhirspark.settings.Settings;
import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import org.hl7.fhir.r4.model.Annotation;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.Reference;

public final class FollowUpAdapter {

    private static IGenericClient client;
    private static String followUpUri;
    private static String responseUri;
    private static String therapyRecommendationUri;

    private FollowUpAdapter() {
    }

    public static void initialize(Settings settings, IGenericClient fhirClient) {
        FollowUpAdapter.client = fhirClient;
        FollowUpAdapter.followUpUri = settings.getFollowUpSystem();
        FollowUpAdapter.responseUri = settings.getResponseSystem();
        FollowUpAdapter.therapyRecommendationUri = settings.getObservationSystem();
    }

    public static FollowUp toJson(List<Regex> regex, MedicationStatement medicationStatement) {
        FollowUp followUp = new FollowUp();

        if (medicationStatement.hasInformationSource()) {
            Bundle b2 = (Bundle) client.search().forResource(Practitioner.class).where(new TokenClientParam("_id")
                    .exactly().code(medicationStatement.getInformationSource().getReference())).prettyPrint()
                    .execute();
            Practitioner author = (Practitioner) b2.getEntryFirstRep().getResource();
            followUp.setAuthor(author.getIdentifierFirstRep().getValue());
        }

        //followUp.setTherapyRecommendation(medicationStatement.getBasedOnFirstRep().getReference());

        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd");
        followUp.setDate(f.format(medicationStatement.getEffectiveDateTimeType().toCalendar().getTime()));

        followUp.setId(medicationStatement.getIdentifierFirstRep().getValue());

        if (medicationStatement.getNote().size() > 0) {
            followUp.setComment(medicationStatement.getNote().get(0).getText());
        }


        followUp.setTherapyRecommendationRealized(medicationStatement.getStatus()
            .compareTo(MedicationStatement.MedicationStatementStatus.fromCode("completed")) == 0);
        followUp.setSideEffect(medicationStatement.hasStatusReason());


        ResponseCriteria respCrit = new ResponseCriteria();
        TherapyRecommendation therapyRecommendation = new TherapyRecommendation();

        for (Reference reference : medicationStatement.getReasonReference()) {

            Bundle b1 = (Bundle) client.search().forResource(Observation.class)
                .where(new TokenClientParam("_id")
                .exactly().code(reference.getReference())).prettyPrint()
                .include(Observation.INCLUDE_DERIVED_FROM)
                .include(Observation.INCLUDE_SPECIMEN.asRecursive())
                .execute();

            Observation obs = (Observation) b1.getEntryFirstRep().getResource();

            if (obs.getIdentifierFirstRep().getValue().startsWith("response_")) {
                String tag = obs.getIdentifierFirstRep().getValue().split("_")[1];

                try {
                    ResponseCriteria.class
                        .getDeclaredMethod("set" + tag, Boolean.class)
                        .invoke(respCrit, true);
                } catch (NoSuchMethodException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                }

            } else if (obs.getIdentifierFirstRep().getSystem().equals(therapyRecommendationUri)) {

                therapyRecommendation = TherapyRecommendationAdapter.toJson(client,
                        regex, obs);

            }

        }

        followUp.setTherapyRecommendation(therapyRecommendation);
        followUp.setResponse(respCrit);

        return followUp;
    }

    public static void fromJson(Bundle bundle, List<Regex> regex, Reference fhirPatient,
        String patientId, FollowUp followUp) {

        MedicationStatement medicationStatement = new MedicationStatement();
        medicationStatement.getMeta().addProfile(GenomicsReportingEnum.GENOMICS_REPORT.getSystem());
        medicationStatement.getMeta().addProfile(MolekulargenetischerBefundberichtEnum.GENOMICS_REPORT.getSystem());
        medicationStatement.addIdentifier().setSystem(followUpUri).setValue(followUp.getId());
        medicationStatement.setId(IdType.newRandomUuid());
        medicationStatement.setSubject(fhirPatient);

        medicationStatement.setInformationSource(MtbAdapter.getOrCreatePractitioner(bundle, followUp.getAuthor()));

        medicationStatement.addReasonReference(
            getTherapyRecommendationReference(followUp.getAuthor(), followUp.getTherapyRecommendation().getId())
        );

        medicationStatement.getEffectiveDateTimeType().fromStringValue(followUp.getDate());

        medicationStatement.addIdentifier().setValue(followUp.getId());

        if (followUp.getComment() != null) {
            ArrayList<Annotation> annots = new ArrayList<Annotation>();
            Annotation comment = new Annotation();
            comment.setText(followUp.getComment());
            annots.add(comment);
            medicationStatement.setNote(annots);
        }

        if (followUp.getTherapyRecommendationRealized()) {
            medicationStatement.setStatus(MedicationStatement.MedicationStatementStatus.fromCode("completed"));
        } else {
            medicationStatement.setStatus(MedicationStatement.MedicationStatementStatus.fromCode("not-taken"));
        }

        if (followUp.getSideEffect()) {
            CodeableConcept cc = new CodeableConcept();
            cc.addCoding(SnomedEnum.SIDE_EFFECT.toCoding());
            medicationStatement.addStatusReason(cc);
        }

        if (!followUp.getId().startsWith("followUp_" + patientId + "_")) {
            throw new IllegalArgumentException("Invalid followUp id!");
        }

        if (followUp.getResponse() != null) {
            ResponseCriteria response = followUp.getResponse();
            ArrayList<String> responseTags = new ArrayList<String>();
            ArrayList<Boolean> responseValues = new ArrayList<Boolean>();
            final int respCount = 12;
            final Double numOfResp = 4.0;

            responseTags.add("Pd3");
            responseValues.add(response.getPd3());
            responseTags.add("Pr3");
            responseValues.add(response.getPr3());
            responseTags.add("Cr3");
            responseValues.add(response.getCr3());
            responseTags.add("Sd3");
            responseValues.add(response.getSd3());
            responseTags.add("Pd6");
            responseValues.add(response.getPd6());
            responseTags.add("Pr6");
            responseValues.add(response.getPr6());
            responseTags.add("Cr6");
            responseValues.add(response.getCr6());
            responseTags.add("Sd6");
            responseValues.add(response.getSd6());
            responseTags.add("Pd12");
            responseValues.add(response.getPd12());
            responseTags.add("Pr12");
            responseValues.add(response.getPr12());
            responseTags.add("Cr12");
            responseValues.add(response.getCr12());
            responseTags.add("Sd12");
            responseValues.add(response.getSd12());

            for (int i = 0; i < respCount; i++) {

                if (!responseValues.get(i)) {
                    continue;
                }

                String[] months = {"3", "6", "12"};

                Observation responseObs = new Observation();

                responseObs.getCode()
                    .addCoding(GenomicsReportingEnum.THERAPEUTIC_IMPLICATION_CODING.toCoding());

                CodeableConcept codeConc = new CodeableConcept();
                codeConc.addCoding(ResponseEnum.valueOf(responseTags.get(i).substring(0, 2).toUpperCase()).toCoding());
                responseObs.setValue(codeConc);

                DateTimeType dTime = new DateTimeType(followUp.getDate());
                DateTimeType respTime = getMTBDate(followUp.getAuthor(), followUp.getTherapyRecommendation().getId());
                respTime.add(Calendar.MONTH, Integer.valueOf(months[(int) Math.floor(i / numOfResp)]));

                responseObs.setEffective(respTime);
                responseObs.setIssued(dTime.getValue());

                dTime.add(Calendar.MONTH, Integer.valueOf(months[(int) Math.floor(i / numOfResp)]));

                responseObs.setId(IdType.newRandomUuid());
                responseObs.setStatus(Observation.ObservationStatus.FINAL);
                responseObs.addIdentifier().setSystem(responseUri)
                    .setValue("response_" + responseTags.get(i) + "_" + followUp.getId());

                bundle.addEntry().setFullUrl(responseObs.getIdElement().getValue()).setResource(responseObs)
                        .getRequest().setUrl(
                            "Observation?identifier=" + "response_" + responseTags.get(i) + "_" + followUp.getId()
                        )
                        .setIfNoneExist("identifier=" + "response_" + responseTags.get(i) + "_" + followUp.getId())
                        .setMethod(Bundle.HTTPVerb.PUT);
                medicationStatement.addReasonReference(new Reference(responseObs)
                    .setDisplay("TherapyResponse_" + months[(int) Math.floor(i / numOfResp)] + "_ Months"));
            }
        }

        bundle.addEntry().setFullUrl(medicationStatement.getIdElement().getValue()).setResource(medicationStatement)
                .getRequest().setUrl("MedicationStatement?identifier=" + followUpUri + "|" + followUp.getId())
                .setIfNoneExist("identifier=" + followUpUri + "|" + followUp.getId()).setMethod(Bundle.HTTPVerb.PUT);

    }

    private static Reference getTherapyRecommendationReference(String credentials, String trIdentifier) {

        Bundle b1 = (Bundle) client.search().forResource(Observation.class)
                .where(new TokenClientParam("identifier")
                .exactly().code(trIdentifier)).prettyPrint()
                .execute();

        Observation obs = (Observation) b1.getEntryFirstRep().getResource();
        String id = obs.getIdElement().getIdPart();

        return new Reference("Observation/" + id).setDisplay("BaseTherapyRecommendation");

    }

    private static DateTimeType getMTBDate(String credentials, String trIdentifier) {

        Bundle b1 = (Bundle) client.search().forResource(DiagnosticReport.class)
                .where(new TokenClientParam("result")
                .exactly().code(getTherapyRecommendationReference(credentials, trIdentifier).getId()))
                .prettyPrint()
                .execute();

        DiagnosticReport dr = (DiagnosticReport) b1.getEntryFirstRep().getResource();

        return dr.getEffectiveDateTimeType();

    }

}
