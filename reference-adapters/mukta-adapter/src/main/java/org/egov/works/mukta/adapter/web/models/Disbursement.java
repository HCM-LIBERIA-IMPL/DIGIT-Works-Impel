package org.egov.works.mukta.adapter.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Disbursement extends ExchangeCode {
    @JsonProperty("id")
    private String id;
    @NotNull
    @JsonProperty("target_id")
    private String targetId;
    @JsonProperty("location_code")
    private String locationCode;
    @JsonProperty("parent_id")
    private String parentId;
    @JsonProperty("disbursement_date")
    private ZonedDateTime disbursementDate;
    @JsonProperty("allocation_ids")
    private List<String> allocationIds;
    @JsonProperty("disbursements")
    private List<Disbursement> disbursements;
    @NotNull
    @JsonProperty("account_code")
    private String accountCode;
    @NotNull
    @JsonProperty("net_amount")
    private BigDecimal netAmount;
    @NotNull
    @JsonProperty("gross_amount")
    private BigDecimal grossAmount;
    @NotNull
    @JsonProperty("individual")
    private Individual individual;
    @JsonProperty("program_code")
    private String programCode;
    @JsonProperty("additional_details")
    private Object additionalDetails;
    @JsonProperty("audit_details")
    private AuditDetails auditDetails;
}