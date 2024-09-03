package fhirspark.adapter;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import fhirspark.adapter.clinicaldata.GenericAdapter;
import fhirspark.definitions.UriEnum;
import fhirspark.restmodel.ClinicalDatum;
import fhirspark.restmodel.GeneticAlteration;
import fhirspark.restmodel.Reasoning;
import fhirspark.settings.Regex;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.Specimen;

public final class ReasoningAdapter {

    private ReasoningAdapter() {
    }

    public static List<Reference> fromJson(Bundle bundle, Observation efficacyObservation, List<Regex> regex,
            Reference fhirPatient, Reasoning reasoning, Map<String, Observation> unique) {
        if (reasoning.getClinicalData() != null) {
            reasoning.getClinicalData().forEach(clinical -> {
                if (clinical == null) {
                    return;
                }
                Specimen s = null;
                if (clinical.getSampleId() != null && clinical.getSampleId().length() > 0) {
                    String sampleId = RegexAdapter.applyRegexFromCbioportal(regex, clinical.getSampleId());
                    s = SpecimenAdapter.fromJson(fhirPatient, sampleId);
                    bundle.addEntry().setFullUrl(s.getIdElement().getValue()).setResource(s)
                            .getRequest().setUrl("Specimen?identifier=" + SpecimenAdapter.getSpecimenSystem() + "|"
                                    + sampleId)
                            .setIfNoneExist("identifier=" + SpecimenAdapter.getSpecimenSystem() + "|"
                                    + sampleId)
                            .setMethod(Bundle.HTTPVerb.PUT);
                }
                try {
                    Method m = Class.forName("fhirspark.adapter.clinicaldata." + clinical.getAttributeId())
                            .getMethod("process", ClinicalDatum.class);
                    efficacyObservation.addHasMember(new Reference((Resource) m.invoke(null, clinical)));
                } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException
                        | InvocationTargetException e) {
                    GenericAdapter genericAdapter = new GenericAdapter();
                    efficacyObservation
                            .addHasMember(new Reference(genericAdapter.fromJson(clinical, new Reference(s))));
                }
            });
        }

        if (reasoning.getGeneticAlterations() != null) {
            reasoning.getGeneticAlterations().forEach(geneticAlteration -> {
                String uniqueString = "component-value-concept=" + UriEnum.NCBI_GENE.getUri() + "|"
                        + geneticAlteration.getEntrezGeneId() + "&subject="
                        + fhirPatient.getResource().getIdElement();
                Observation geneticVariant = GeneticAlterationsAdapter.fromJson(geneticAlteration);
                geneticVariant.setSubject(fhirPatient);
                if (!unique.containsKey(uniqueString)) {
                    unique.put(uniqueString, geneticVariant);
                    geneticVariant.setId(IdType.newRandomUuid());
                    bundle.addEntry().setFullUrl(geneticVariant.getIdElement().getValue())
                    .setResource(geneticVariant).getRequest()
                    .setUrl("Observation?" + uniqueString)
                    .setIfNoneExist(uniqueString)
                    .setMethod(Bundle.HTTPVerb.PUT);
                } else {
                    geneticVariant = unique.get(uniqueString);
                }
                efficacyObservation.addDerivedFrom(new Reference(geneticVariant));

            });
        }

        return efficacyObservation.getDerivedFrom();

    }

    public static Reasoning toJson(List<Regex> regex,
        List<Reference> genetic, List<Reference> clinical, IGenericClient client) {
        List<ClinicalDatum> clinicalData = new ArrayList<>();
        List<GeneticAlteration> geneticAlterations = new ArrayList<>();

        genetic.forEach(reference -> geneticAlterations
                .add(GeneticAlterationsAdapter.toJson((Observation) reference.getResource())));

        clinical.forEach(member -> {
            GenericAdapter genericAdapter = new GenericAdapter();
            ClinicalDatum cd = genericAdapter.toJson(regex, (Observation) member.getResource(), client);
            clinicalData.add(cd);
        });

        return new Reasoning().withClinicalData(clinicalData).withGeneticAlterations(geneticAlterations);
    }

}
