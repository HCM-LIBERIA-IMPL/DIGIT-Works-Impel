package org.egov.works.mukta.adapter.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import org.egov.common.contract.response.ResponseInfo;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisbursementResponse {
    @JsonProperty("ResponseInfo")
    private ResponseInfo responseInfo;
    @JsonProperty("Disbursement")
    private Disbursement disbursement;
}
