
package fhirspark.settings;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;

/**
 * POJO representing the settings.yaml file.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "port",
    "fhirDbBase",
    "specimenSystem",
    "diagnosticReportSystem",
    "observationSystem",
    "responseSystem",
    "followUpSystem",
    "patientSystem",
    "studySystem",
    "serviceRequestSystem",
    "hgncPath",
    "portalUrl",
    "loginRequired",
    "oncokbPath",
    "regex"
})
public final class Settings {

    @JsonProperty("port")
    private Integer port;
    @JsonProperty("fhirDbBase")
    private String fhirDbBase;
    @JsonProperty("specimenSystem")
    private String specimenSystem;
    @JsonProperty("diagnosticReportSystem")
    private String diagnosticReportSystem;
    @JsonProperty("observationSystem")
    private String observationSystem;
    @JsonProperty("responseSystem")
    private String responseSystem;
    @JsonProperty("followUpSystem")
    private String followUpSystem;
    @JsonProperty("patientSystem")
    private String patientSystem;
    @JsonProperty("studySystem")
    private String studySystem;
    @JsonProperty("serviceRequestSystem")
    private String serviceRequestSystem;
    @JsonProperty("hgncPath")
    private String hgncPath;
    @JsonProperty("portalUrl")
    private String portalUrl;
    @JsonProperty("loginRequired")
    private Boolean loginRequired;
    @JsonProperty("oncokbPath")
    private String oncokbPath;
    @JsonProperty("regex")
    private List<Regex> regex;

    @JsonProperty("port")
    public Integer getPort() {
        return port;
    }

    @JsonProperty("port")
    public void setPort(Integer port) {
        this.port = port;
    }

    @JsonProperty("fhirDbBase")
    public String getFhirDbBase() {
        return fhirDbBase;
    }

    @JsonProperty("fhirDbBase")
    public void setFhirDbBase(String fhirDbBase) {
        this.fhirDbBase = fhirDbBase;
    }

    @JsonProperty("specimenSystem")
    public String getSpecimenSystem() {
        return specimenSystem;
    }

    @JsonProperty("specimenSystem")
    public void setSpecimenSystem(String specimenSystem) {
        this.specimenSystem = specimenSystem;
    }

    @JsonProperty("diagnosticReportSystem")
    public String getDiagnosticReportSystem() {
        return diagnosticReportSystem;
    }

    @JsonProperty("diagnosticReportSystem")
    public void setDiagnosticReportSystem(String diagnosticReportSystem) {
        this.diagnosticReportSystem = diagnosticReportSystem;
    }

    @JsonProperty("observationSystem")
    public String getObservationSystem() {
        return observationSystem;
    }

    @JsonProperty("observationSystem")
    public void setObservationSystem(String observationSystem) {
        this.observationSystem = observationSystem;
    }

    @JsonProperty("responseSystem")
    public String getResponseSystem() {
        return responseSystem;
    }

    @JsonProperty("responseSystem")
    public void setResponseSystem(String responseSystem) {
        this.responseSystem = responseSystem;
    }

    @JsonProperty("patientSystem")
    public String getPatientSystem() {
        return patientSystem;
    }

    @JsonProperty("patientSystem")
    public void setPatientSystem(String patientSystem) {
        this.patientSystem = patientSystem;
    }

    @JsonProperty("studySystem")
    public void setStudySystem(String studySystem) {
        this.studySystem = studySystem;
    }

    @JsonProperty("studySystem")
    public String getStudySystem() {
        return studySystem;
    }

    @JsonProperty("serviceRequestSystem")
    public String getServiceRequestSystem() {
        return serviceRequestSystem;
    }

    @JsonProperty("serviceRequestSystem")
    public void setServiceRequestSystem(String serviceRequestSystem) {
        this.serviceRequestSystem = serviceRequestSystem;
    }

    @JsonProperty("hgncPath")
    public String getHgncPath() {
        return hgncPath;
    }

    @JsonProperty("hgncPath")
    public void setHgncPath(String hgncPath) {
        this.hgncPath = hgncPath;
    }

    @JsonProperty("portalUrl")
    public String getPortalUrl() {
        return portalUrl;
    }

    @JsonProperty("portalUrl")
    public void setPortalUrl(String portalUrl) {
        this.portalUrl = portalUrl;
    }

    @JsonProperty("loginRequired")
    public Boolean getLoginRequired() {
        return loginRequired;
    }

    @JsonProperty("loginRequired")
    public void setLoginRequired(Boolean loginRequired) {
        this.loginRequired = loginRequired;
    }

    @JsonProperty("oncokbPath")
    public String getOncokbPath() {
        return oncokbPath;
    }

    @JsonProperty("oncokbPath")
    public void setOncokbPath(String oncokbPath) {
        this.oncokbPath = oncokbPath;
    }

    @JsonProperty("regex")
    public List<Regex> getRegex() {
        return regex;
    }

    @JsonProperty("regex")
    public void setRegex(List<Regex> newRegex) {
        regex = newRegex;
    }

    @JsonProperty("followUpSystem")
    public String getFollowUpSystem() {
        return followUpSystem;
    }

    @JsonProperty("followUpSystem")
    public void setFollowUpSystem(String followUpSystem) {
        this.followUpSystem = followUpSystem;
    }

}
