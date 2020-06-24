
package fhirspark;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "port",
    "fhirDbBase",
    "hl7v2config"
})
public final class Settings {

    @JsonProperty("port")
    private Integer port;
    @JsonProperty("fhirDbBase")
    private String fhirDbBase;
    @JsonProperty("hl7v2config")
    private List<Hl7v2config> hl7v2config;

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

    @JsonProperty("hl7v2config")
    public List<Hl7v2config> getHl7v2config() {
        return hl7v2config;
    }

    @JsonProperty("hl7v2config")
    public void setHl7v2config(List<Hl7v2config> hl7v2config) {
        this.hl7v2config = hl7v2config;
    }

}
