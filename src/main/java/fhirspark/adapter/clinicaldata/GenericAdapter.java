package fhirspark.adapter.clinicaldata;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.TokenClientParam;
import fhirspark.adapter.SpecimenAdapter;
import fhirspark.definitions.LoincEnum;
import fhirspark.restmodel.ClinicalDatum;
import fhirspark.settings.Regex;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Observation.ObservationStatus;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.Specimen;

/**
 * Generic adapter for clinical data. Also fallback if other adapter was not
 * found.
 */
public class GenericAdapter implements ClinicalDataAdapter {

    @Override
    public Resource fromJson(ClinicalDatum clinicalData) {
        Observation obs = new Observation();
        obs.setStatus(ObservationStatus.UNKNOWN);
        obs.setCode(new CodeableConcept().addCoding(LoincEnum.CLINICAL_FINDING.toCoding()));

        obs.getValueStringType().setValue(clinicalData.getAttributeName()
                + (clinicalData.getValue() == null || clinicalData.getValue().equals("null") ? ""
                        : ": " + clinicalData.getValue()));

        return obs;
    }

    @Override
    public Resource fromJson(ClinicalDatum clinicalData, Reference specimen) {
        Observation obs = (Observation) fromJson(clinicalData);
        obs.setSpecimen(specimen);

        return obs;
    }

    @Override
    public ClinicalDatum toJson(List<Regex> regex, Observation obs, IGenericClient client) {
        if (obs.getValueStringType().asStringValue() == null) {
            return null;
        }
        String[] attr = obs.getValueStringType().asStringValue().split(": ");
        ClinicalDatum cd = new ClinicalDatum().withAttributeName(attr[0]);
        if (attr.length == 2) {
            cd.setValue(attr[1]);
        }
        if (obs.getSpecimen().getReference() != null && obs.getSpecimen().getResource() == null) {

            Bundle b1 = (Bundle) client.search().forResource(Specimen.class)
                    .where(new TokenClientParam("_id")
                            .exactly().code(obs.getSpecimen().getReference()))
                    .prettyPrint()
                    .execute();

            cd.setSampleId(((Specimen) b1.getEntryFirstRep().getResource())
                    .getIdentifierFirstRep().getValue());

        }
        if (obs.getSpecimen().getResource() != null) {
            System.out.println(((Specimen) obs.getSpecimen().getResource())
                    .getIdentifierFirstRep().getValue());
            cd.setSampleId(SpecimenAdapter.toJson(regex, obs.getSpecimen()));
        }
        return cd;

    }

}
