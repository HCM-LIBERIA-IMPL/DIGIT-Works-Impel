package digit.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;

/**
 * ContractRequest
 */
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2023-02-01T15:45:33.268+05:30")

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ContractRequest {
    @JsonProperty("requestInfo")
    @Valid
    private RequestInfo requestInfo = null;

    @JsonProperty("contract")
    @Valid
    private Contract contract = null;

    @JsonProperty("workflow")
    @Valid
    private Workflow workflow = null;


}

