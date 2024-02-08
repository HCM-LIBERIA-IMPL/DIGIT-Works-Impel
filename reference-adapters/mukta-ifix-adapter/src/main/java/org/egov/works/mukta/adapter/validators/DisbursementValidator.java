package org.egov.works.mukta.adapter.validators;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.tracer.model.CustomException;
import org.egov.works.mukta.adapter.constants.Error;
import org.egov.works.mukta.adapter.repository.DisbursementRepository;
import org.egov.works.mukta.adapter.web.models.*;
import org.egov.works.mukta.adapter.web.models.bill.PaymentRequest;
import org.egov.works.mukta.adapter.web.models.enums.StatusCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

@Component
@Slf4j
public class DisbursementValidator {
    private final DisbursementRepository disbursementRepository;

    @Autowired
    public DisbursementValidator(DisbursementRepository disbursementRepository) {
        this.disbursementRepository = disbursementRepository;
    }

    public void validateOnDisbursementRequest(DisbursementRequest disbursementRequest) {
        log.info("Validating on disbursement request");
        validateRequestBodyForOnDisbursement(disbursementRequest);
        validateHeader(disbursementRequest.getHeader());
        validateDisbursement(disbursementRequest.getMessage());
    }

    private void validateHeader(MsgHeader header) {
        log.info("Validating header");
        if(Objects.equals(header.getSenderId(), header.getReceiverId())){
            throw new CustomException(Error.INVALID_REQUEST, Error.SENDER_ID_AND_RECEIVER_ID_SAME_MESSAGE);
        }
    }

    public void isValidForDisbursementCreate(PaymentRequest paymentRequest){
        log.info("Validating request body for disbursement create");
        if(paymentRequest.getReferenceId() == null && paymentRequest.getPayment().getPaymentNumber() == null){
            throw new CustomException(Error.INVALID_REQUEST, Error.REFERENCE_ID_AND_PAYEMENT_NOT_FOUND);
        }
        if(paymentRequest.getReferenceId() != null && paymentRequest.getTenantId() == null){
            throw new CustomException(Error.INVALID_REQUEST, Error.REFERENCE_ID_AND_TENANT_ID_NOT_FOUND);
        }
        Pagination pagination = Pagination.builder().sortBy("createdtime").order(Pagination.OrderEnum.DESC).limit(1).build();
        DisbursementSearchRequest disbursementSearchRequest = DisbursementSearchRequest.builder()
                .requestInfo(paymentRequest.getRequestInfo())
                .criteria(DisbursementSearchCriteria.builder().paymentNumber(paymentRequest.getPayment() == null? paymentRequest.getReferenceId(): paymentRequest.getPayment().getPaymentNumber()).build())
                .pagination(pagination)
                .build();
        List<Disbursement> disbursements = disbursementRepository.searchDisbursement(disbursementSearchRequest);
        if(disbursements != null && !disbursements.isEmpty() && (disbursements.get(0).getStatus().getStatusCode().equals(StatusCode.INITIATED)
                || disbursements.get(0).getStatus().getStatusCode().equals(StatusCode.APPROVED)
                || disbursements.get(0).getStatus().getStatusCode().equals(StatusCode.IN_PROCESS) || disbursements.get(0).getStatus().getStatusCode().equals(StatusCode.SUCCESSFUL))){
            throw new CustomException(Error.PAYMENT_ALREADY_PROCESSED, Error.PAYMENT_ALREADY_PROCESSED_MESSAGE);
        }
        if(paymentRequest.getReferenceId() == null){
            paymentRequest.setReferenceId(paymentRequest.getPayment().getPaymentNumber());
        }
        log.info("No active disbursement found for the payment id : " + paymentRequest.getReferenceId());
    }
    private void validateRequestBodyForOnDisbursement(DisbursementRequest disbursementRequest) {
        log.info("Validating request body for on disbursement");
        if(disbursementRequest.getSignature() == null || disbursementRequest.getSignature().isEmpty()){
            throw new CustomException(Error.INVALID_REQUEST, Error.SIGNATURE_NOT_FOUND_MESSAGE);
        }
        if(disbursementRequest.getHeader() == null){
            throw new CustomException(Error.INVALID_REQUEST, Error.HEADER_NOT_FOUND_MESSAGE);
        }
        if(disbursementRequest.getMessage() == null) {
            throw new CustomException(Error.INVALID_REQUEST, Error.MESSAGE_NOT_FOUND_MESSAGE);
        }
    }

    public void validateDisbursement(Disbursement disbursement) {
        log.info("Validating disbursement");
        if (disbursement.getId() == null) {
            throw new CustomException(Error.INVALID_REQUEST, Error.ID_NOT_FOUND_MESSAGE);
        }
        if(disbursement.getTargetId() == null){
            throw new CustomException(Error.INVALID_REQUEST, Error.PAYMENT_REFERENCE_ID_NOT_FOUND_MESSAGE);
        }
        if(disbursement.getStatus() == null || disbursement.getStatus().getStatusCode() == null){
            throw new CustomException(Error.INVALID_REQUEST, Error.DISBURSEMENT_STATUS_NOT_FOUND);
        }
        if(disbursement.getDisbursements() == null || disbursement.getDisbursements().isEmpty()){
            throw new CustomException(Error.INVALID_REQUEST, Error.DISBURSEMENTS_NOT_FOUND_MESSAGE);
        }
        if(disbursement.getAllocationIds() == null || disbursement.getAllocationIds().isEmpty()){
            throw new CustomException(Error.INVALID_REQUEST, Error.ALLOCATION_IDS_NOT_FOUND_MESSAGE);
        }
        validateDisbursementAmount(disbursement);
        validateDisbursementFromDB(disbursement);
    }

    private void validateDisbursementAmount(Disbursement disbursement) {
        log.info("Validating disbursement amount");
        BigDecimal grossAmount = BigDecimal.ZERO;
        BigDecimal netAmount = BigDecimal.ZERO;
        for(Disbursement disbursement1: disbursement.getDisbursements()){
            if(disbursement1.getGrossAmount() == null || disbursement1.getNetAmount() == null){
                throw new CustomException(Error.INVALID_REQUEST, Error.GROSS_AMOUNT_AND_NET_AMOUNT_NOT_FOUND_MESSAGE);
            }
            grossAmount = grossAmount.add(disbursement1.getGrossAmount());
            netAmount = netAmount.add(disbursement1.getNetAmount());
        }
        if(!grossAmount.equals(disbursement.getGrossAmount()) && !netAmount.equals(disbursement.getNetAmount())){
            throw new CustomException(Error.INVALID_REQUEST, Error.GROSS_AMOUNT_AND_NET_AMOUNT_NOT_MATCHED);
        }
    }

    private void validateDisbursementFromDB(Disbursement disbursement) {
        log.info("Validating disbursement from db");
        DisbursementSearchCriteria disbursementSearchCriteria = DisbursementSearchCriteria.builder()
                .ids(Collections.singletonList(disbursement.getId()))
                .build();
        DisbursementSearchRequest disbursementSearchRequest = DisbursementSearchRequest.builder()
                .requestInfo(RequestInfo.builder().build())
                .criteria(disbursementSearchCriteria)
                .pagination(Pagination.builder().build())
                .build();
        List<Disbursement> disbursements = disbursementRepository.searchDisbursement(disbursementSearchRequest);
        if(disbursements == null || disbursements.isEmpty()){
            throw new CustomException(Error.DISBURSEMENT_NOT_FOUND, Error.DISBURSEMENT_NOT_FOUND_IN_DB_MESSAGE);
        }
        //Validating disbursement From DB
        Disbursement disbursementFromDB = disbursements.get(0);
        if(!disbursementFromDB.getTargetId().equals(disbursement.getTargetId())){
            throw new CustomException(Error.TARGET_ID_NOT_MATCHED, Error.TARGET_ID_NOT_MATCHED_MESSAGE);
        }
        if(disbursementFromDB.getDisbursements().size() != disbursement.getDisbursements().size()){
            throw new CustomException(Error.ALL_CHILDS_ARE_NOT_PRESENT, Error.ALL_CHILDS_ARE_NOT_PRESENT_MESSAGE);
        }
        HashMap<String,Disbursement> disbursementsFromDB = new HashMap<>();
        for(Disbursement disbursement1: disbursementFromDB.getDisbursements()){
            disbursementsFromDB.put(disbursement1.getId(), disbursement1);
        }
        for(Disbursement disbursement1: disbursement.getDisbursements()){
            if(disbursementsFromDB.containsKey(disbursement1.getId())){
                validateChildDisbursement(disbursementsFromDB.get(disbursement1.getId()), disbursement1);
                disbursementsFromDB.remove(disbursement1.getId());
            }
        }
        if(!disbursementsFromDB.isEmpty()){
            throw new CustomException(Error.ALL_CHILDS_ARE_NOT_PRESENT, Error.ALL_CHILDS_ARE_NOT_PRESENT_MESSAGE);
        }
    }

    private void validateChildDisbursement(Disbursement disbursementFromDB, Disbursement disbursement) {
        log.info("Validating child disbursement");
        if(!disbursementFromDB.getTargetId().equals(disbursement.getTargetId())){
            throw new CustomException(Error.TARGET_ID_NOT_MATCHED, Error.TARGET_ID_NOT_MATCHED_MESSAGE);
        }
        if(!disbursementFromDB.getGrossAmount().equals(disbursement.getGrossAmount()) && !disbursementFromDB.getNetAmount().equals(disbursement.getNetAmount())){
            throw new CustomException(Error.INVALID_REQUEST, Error.GROSS_AMOUNT_AND_NET_AMOUNT_NOT_MATCHED);
        }
        if(disbursement.getIndividual() == null){
            throw new CustomException(Error.INVALID_REQUEST, Error.INDIVIDUAL_NOT_FOUND);
        }
        if(disbursement.getIndividual().getAddress() == null){
            throw new CustomException(Error.INVALID_REQUEST, Error.INVALID_ADDRESS);
        }

    }
}
