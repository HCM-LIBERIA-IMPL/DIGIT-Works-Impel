CREATE TABLE IF NOT EXISTS jit_payment_inst_details (
  id varchar(256) PRIMARY KEY,
  tenantId varchar(64) NOT NULL,
  piNumber varchar(256),
  parentPiNumber varchar(256),
  muktaReferenceId varchar(256),
  numBeneficiaries int,
  grossAmount numeric(12,2),
  netAmount numeric(12,2),
  piStatus varchar,
  piSuccessCode varchar,
  piSuccessDesc varchar,
  piApprovedId varchar,
  piApprovalDate varchar,
  piErrorResp varchar(256),
  additionalDetails jsonb,
  createdtime bigint,
  createdby varchar(64),
  lastmodifiedtime bigint,
  lastmodifiedby varchar(64)
);