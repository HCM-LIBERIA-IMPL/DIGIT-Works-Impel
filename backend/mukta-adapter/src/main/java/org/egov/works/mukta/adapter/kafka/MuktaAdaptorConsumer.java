package org.egov.works.mukta.adapter.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.tracer.model.CustomException;
import org.egov.works.mukta.adapter.service.PaymentInstructionService;
import org.egov.works.mukta.adapter.util.ProgramServiceUtil;
import org.egov.works.mukta.adapter.web.models.Disbursement;
import org.egov.works.mukta.adapter.web.models.bill.PaymentRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MuktaAdaptorConsumer {

    private final ObjectMapper objectMapper;
    private final PaymentInstructionService paymentInstructionService;
    private final ProgramServiceUtil programServiceUtil;
    private final MuktaAdaptorProducer muktaAdaptorProducer;

    @Autowired
    public MuktaAdaptorConsumer(ObjectMapper objectMapper, PaymentInstructionService paymentInstructionService, ProgramServiceUtil programServiceUtil, MuktaAdaptorProducer muktaAdaptorProducer) {
        this.objectMapper = objectMapper;
        this.paymentInstructionService = paymentInstructionService;
        this.programServiceUtil = programServiceUtil;
        this.muktaAdaptorProducer = muktaAdaptorProducer;
    }

    @KafkaListener(topics = {"${payment.create.topic}"})
    public void listen(final String record, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            log.info("Payment data received on.");
            PaymentRequest paymentRequest = objectMapper.readValue(record, PaymentRequest.class);
            log.info("Payment data is " + paymentRequest);
            Disbursement disbursementRequest = paymentInstructionService.processPaymentInstruction(paymentRequest);
//            programServiceUtil.callProgramServiceDisbursement(disbursementRequest);
            muktaAdaptorProducer.push("egf-adapter-payment-create", disbursementRequest);
        } catch (Exception e) {
            log.error("Error occurred while processing the consumed save estimate record from topic : " + topic, e);
            throw new CustomException("Error occurred while processing the consumed save estimate record from topic : " + topic, e.toString());
        }
    }
}
