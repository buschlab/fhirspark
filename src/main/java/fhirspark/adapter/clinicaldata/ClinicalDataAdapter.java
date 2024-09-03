package fhirspark.adapter.clinicaldata;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import fhirspark.restmodel.ClinicalDatum;
import fhirspark.settings.Regex;
import java.util.List;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

/**
 * General interface for all adapters of clinical data.
 */
public interface ClinicalDataAdapter {

    Resource fromJson(ClinicalDatum clinicalData);

    Resource fromJson(ClinicalDatum clinicalData, Reference specimen);

    ClinicalDatum toJson(List<Regex> regex, Observation fhirResource, IGenericClient client);

}
