package org.egov.works.mukta.adapter.service;


import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONArray;
import org.egov.common.models.individual.Individual;
import org.egov.tracer.model.CustomException;
import org.egov.works.mukta.adapter.config.Constants;
import org.egov.works.mukta.adapter.config.MuktaAdaptorConfig;
import org.egov.works.mukta.adapter.constants.Error;
import org.egov.works.mukta.adapter.enrichment.PaymentInstructionEnrichment;
import org.egov.works.mukta.adapter.kafka.MuktaAdaptorProducer;
import org.egov.works.mukta.adapter.repository.DisbursementRepository;
import org.egov.works.mukta.adapter.util.*;
import org.egov.works.mukta.adapter.web.models.Disbursement;
import org.egov.works.mukta.adapter.web.models.DisbursementRequest;
import org.egov.works.mukta.adapter.web.models.DisbursementSearchCriteria;
import org.egov.works.mukta.adapter.web.models.DisbursementSearchRequest;
import org.egov.works.mukta.adapter.web.models.bankaccount.BankAccount;
import org.egov.works.mukta.adapter.web.models.bill.*;
import org.egov.works.mukta.adapter.web.models.enums.PaymentStatus;
import org.egov.works.mukta.adapter.web.models.enums.Status;
import org.egov.works.mukta.adapter.web.models.enums.StatusCode;
import org.egov.works.mukta.adapter.web.models.jit.Beneficiary;
import org.egov.works.mukta.adapter.web.models.organisation.Organisation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PaymentInstructionService {
    private final BillUtils billUtils;
    private final PaymentInstructionEnrichment piEnrichment;
    private final BankAccountUtils bankAccountUtils;
    private final OrganisationUtils organisationUtils;
    private final IndividualUtils individualUtils;
    private final MdmsUtil mdmsUtil;
    private final DisbursementRepository disbursementRepository;
    private final ProgramServiceUtil programServiceUtil;
    private final MuktaAdaptorProducer muktaAdaptorProducer;
    private final MuktaAdaptorConfig muktaAdaptorConfig;

    @Autowired
    public PaymentInstructionService(BillUtils billUtils, PaymentInstructionEnrichment piEnrichment, BankAccountUtils bankAccountUtils, OrganisationUtils organisationUtils, IndividualUtils individualUtils, MdmsUtil mdmsUtil, DisbursementRepository disbursementRepository, ProgramServiceUtil programServiceUtil, MuktaAdaptorProducer muktaAdaptorProducer, MuktaAdaptorConfig muktaAdaptorConfig) {
        this.billUtils = billUtils;
        this.piEnrichment = piEnrichment;
        this.bankAccountUtils = bankAccountUtils;
        this.organisationUtils = organisationUtils;
        this.individualUtils = individualUtils;
        this.mdmsUtil = mdmsUtil;
        this.disbursementRepository = disbursementRepository;
        this.programServiceUtil = programServiceUtil;
        this.muktaAdaptorProducer = muktaAdaptorProducer;
        this.muktaAdaptorConfig = muktaAdaptorConfig;
    }

    public Disbursement processDisbursementCreate(PaymentRequest paymentRequest) {
        log.info("Processing payment instruction on failure");
        DisbursementSearchRequest disbursementSearchRequest = DisbursementSearchRequest.builder().criteria(DisbursementSearchCriteria.builder().paymentNumber(paymentRequest.getReferenceId()).build()).build();
        List<Disbursement> disbursements = disbursementRepository.searchDisbursement(disbursementSearchRequest);
        if(disbursements != null && !disbursements.isEmpty() && (disbursements.get(0).getStatus().getStatusCode().equals(StatusCode.INITIATED) || disbursements.get(0).getStatus().getStatusCode().equals(StatusCode.SUCCESSFUL))){
            throw new CustomException(Error.PAYMENT_ALREADY_PROCESSED, Error.PAYMENT_ALREADY_PROCESSED_MESSAGE);
        }
        log.info("No payment found for the payment id : " + paymentRequest.getReferenceId());
        log.info("Creating new payment for the payment id : " + paymentRequest.getReferenceId());
        Disbursement disbursement = processPaymentInstruction(paymentRequest);
        DisbursementRequest disbursementRequest = DisbursementRequest.builder().disbursement(disbursement).requestInfo(paymentRequest.getRequestInfo()).build();
//        programServiceUtil.callProgramServiceDisbursement(disbursementRequest);
        muktaAdaptorProducer.push(muktaAdaptorConfig.getDisburseCreateTopic(), disbursementRequest);
        return disbursement;
    }

    public Disbursement processPaymentInstruction(PaymentRequest paymentRequest) {
        log.info("Processing payment instruction");
        if(paymentRequest.getPayment() == null && paymentRequest.getReferenceId() != null && paymentRequest.getTenantId() != null) {
            log.info("Fetching payment details by using reference id and tenant id");
            List<Payment> payments = billUtils.fetchPaymentDetails(paymentRequest.getRequestInfo(), paymentRequest.getReferenceId(), paymentRequest.getTenantId());
            if (payments == null || payments.isEmpty()) {
                throw new CustomException(Error.PAYMENT_NOT_FOUND, Error.PAYMENT_NOT_FOUND_MESSAGE);
            }
            log.info("Payments fetched for the disbursement request : " + payments);
            paymentRequest.setPayment(payments.get(0));
        }
        Map<String, Map<String, JSONArray>> mdmsData = mdmsUtil.fetchMdmsData(paymentRequest.getRequestInfo(), paymentRequest.getPayment().getTenantId());
        Disbursement disbursementRequest = getBeneficiariesFromPayment(paymentRequest, mdmsData);
        log.info("Disbursement request is " + disbursementRequest);
        return disbursementRequest;
    }

    private Disbursement getBeneficiariesFromPayment(PaymentRequest paymentRequest, Map<String, Map<String, JSONArray>> mdmsData) {
        log.info("Started executing getBeneficiariesFromPayment");
        // Get the list of bills based on payment request
        List<Bill> billList = billUtils.fetchBillsFromPayment(paymentRequest);
        if (billList == null || billList.isEmpty())
            throw new CustomException(Error.BILLS_NOT_FOUND , Error.BILLS_NOT_FOUND_MESSAGE);

        billList = filterBillsPayableLineItemByPayments(paymentRequest.getPayment(), billList);
        log.info("Bills are filtered based on line item status, and sending back."+ billList);
        List<Beneficiary> beneficiaryList = piEnrichment.getBeneficiariesFromBills(billList, paymentRequest, mdmsData);

        if (beneficiaryList == null || beneficiaryList.isEmpty())
            throw new CustomException(Error.BENEFICIARIES_NOT_FOUND, Error.BENEFICIARIES_NOT_FOUND_MESSAGE);

        // Get all beneficiary ids from pi request
        List<String> individualIds = new ArrayList<>();
        List<String> orgIds = new ArrayList<>();
        for (Bill bill : billList) {
            for (BillDetail billDetail : bill.getBillDetails()) {
                Party payee = billDetail.getPayee();
                if (payee != null && payee.getType().equals(Constants.PAYEE_TYPE_INDIVIDUAL)) {
                    individualIds.add(billDetail.getPayee().getIdentifier());
                } else if (payee != null) {
                    orgIds.add(billDetail.getPayee().getIdentifier());
                }
            }
        }
        return getBeneficiariesEnrichedData(paymentRequest, beneficiaryList, orgIds, individualIds);
    }

    private Disbursement getBeneficiariesEnrichedData(PaymentRequest paymentRequest, List<Beneficiary> beneficiaryList, List<String> orgIds, List<String> individualIds) {
        log.info("Started executing getBeneficiariesEnrichedData");
        List<String> beneficiaryIds = new ArrayList<>();
        for (Beneficiary beneficiary : beneficiaryList) {
            beneficiaryIds.add(beneficiary.getBeneficiaryId());
        }

        List<Organisation> organizations = new ArrayList<>();
        List<Individual> individuals = new ArrayList<>();
        // Get bank account details by beneficiary ids
        List<BankAccount> bankAccounts = bankAccountUtils.getBankAccountsByIdentifier(paymentRequest.getRequestInfo(), beneficiaryIds, paymentRequest.getPayment().getTenantId());
        log.info("Bank accounts fetched for the beneficiary ids : " + bankAccounts);
        // Get organizations details
        if (orgIds != null && !orgIds.isEmpty()) {
            organizations = organisationUtils.getOrganisationsById(paymentRequest.getRequestInfo(), orgIds, paymentRequest.getPayment().getTenantId());
            log.info("Organizations fetched for the org ids : " + organizations);
        }
        // Get bank account details by beneficiary ids
        if (individualIds != null && !individualIds.isEmpty()) {
            individuals = individualUtils.getIndividualById(paymentRequest.getRequestInfo(), individualIds, paymentRequest.getPayment().getTenantId());
            log.info("Individuals fetched for the individual ids : " + individuals);
        }
        // Enrich PI request with beneficiary bankaccount details
        Disbursement disbursementRequest = piEnrichment.enrichBankaccountOnBeneficiary(beneficiaryList, bankAccounts, individuals, organizations, paymentRequest);
        log.info("Beneficiaries are enriched, sending back beneficiaryList");
        return disbursementRequest;
    }


    public List<Bill> filterBillsPayableLineItemByPayments(Payment payment, List<Bill> billList) {
        log.info("Started executing filterBillsPayableLineItemByPayments");

        Map<String, BillDetail> billDetailMap = billList.stream()
                .map(Bill::getBillDetails)
                .flatMap(Collection::stream)
                .collect(Collectors.toMap(BillDetail::getId, Function.identity()));
        Map<String, LineItem> billPayableLineItemMap = billList.stream()
                .map(Bill::getBillDetails)
                .flatMap(Collection::stream)
                .map(BillDetail::getPayableLineItems)
                .flatMap(Collection::stream)
                .collect(Collectors.toMap(LineItem::getId, Function.identity()));
        for (PaymentBill paymentBill : payment.getBills()) {
            for (PaymentBillDetail paymentBillDetail : paymentBill.getBillDetails()) {
                List<LineItem> lineItems = new ArrayList<>();
                for (PaymentLineItem payableLineItem : paymentBillDetail.getPayableLineItems()) {
                    LineItem lineItem = billPayableLineItemMap.get(payableLineItem.getLineItemId());
                    if (lineItem != null && lineItem.getStatus().equals(Status.ACTIVE) && (payableLineItem.getStatus().equals(PaymentStatus.INITIATED) || payableLineItem.getStatus().equals(PaymentStatus.FAILED)))
                        lineItems.add(lineItem);
                }
                billDetailMap.get(paymentBillDetail.getBillDetailId()).setPayableLineItems(lineItems);
            }
        }
        log.info("Bills are filtered based on line item status, and sending back.");
        return billList;
    }

    public List<Disbursement> processDisbursementSearch(DisbursementSearchRequest disbursementSearchRequest) {
        log.info("Searching for disbursements based on the criteria: "+ disbursementSearchRequest.getCriteria());
        return disbursementRepository.searchDisbursement(disbursementSearchRequest);
    }
}