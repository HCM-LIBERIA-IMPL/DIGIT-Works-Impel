package org.egov.works.measurement.service;


import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.works.measurement.enrichment.MeasurementEnrichment;
import org.egov.works.measurement.kafka.Producer;
import org.egov.works.measurement.repository.rowmapper.MeasurementRowMapper;
import org.egov.works.measurement.util.*;
import org.egov.works.measurement.validator.MeasurementServiceValidator;
import org.egov.works.measurement.validator.MeasurementValidator;
import org.egov.works.measurement.web.models.Document;
import org.egov.works.measurement.web.models.Measure;
import org.egov.works.measurement.web.models.Measurement;
import org.egov.works.measurement.web.models.MeasurementRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.egov.works.measurement.web.models.Pagination;

import java.math.BigDecimal;
import java.util.*;
import org.egov.tracer.model.CustomException;
import org.egov.works.measurement.config.Configuration;

import org.egov.works.measurement.util.MdmsUtil;
import org.egov.works.measurement.web.models.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.egov.works.measurement.repository.ServiceRequestRepository;
import org.egov.works.measurement.web.models.MeasurementCriteria;


@Service
@Slf4j
public class MeasurementService {
    @Autowired
    private MdmsUtil mdmsUtil;
    @Autowired
    private IdgenUtil idgenUtil;
    @Autowired
    private Producer producer;

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private Configuration configuration;

    @Autowired
    private MeasurementServiceValidator serviceValidator;
    @Autowired
    private MeasurementRowMapper rowMapper;

    @Autowired
    private MeasurementEnrichment enrichmentUtil;

    @Autowired
    private WorkflowUtil workflowUtil;

    @Autowired
    private ContractUtil contractUtil;

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ServiceRequestRepository serviceRequestRepository;
    @Autowired
    private ResponseInfoFactory responseInfoFactory;
    @Autowired
    private MeasurementValidator measurementValidator;


    /**
     * Handles measurement create
     * @param request
     * @return
     */
    public ResponseEntity<MeasurementResponse> createMeasurement(MeasurementRequest request){
        // Just validate tenant id from idGen
        measurementValidator.validateTenantId(request);
        serviceValidator.validateDocumentIds(request.getMeasurements());
        // enrich measurements
        enrichMeasurement(request);
        MeasurementResponse response = MeasurementResponse.builder().responseInfo(responseInfoFactory.createResponseInfoFromRequestInfo(request.getRequestInfo(),true)).measurements(request.getMeasurements()).build();

        // push to kafka topic
        producer.push(configuration.getCreateMeasurementTopic(),request);
        return new ResponseEntity<>(response, HttpStatus.ACCEPTED);
    }

    /**
     * Handles measurement update
     * @param measurementRegistrationRequest
     * @return
     */
    public MeasurementResponse updateMeasurement(MeasurementRequest measurementRegistrationRequest) {
        // Just validate tenant id
        measurementValidator.validateTenantId(measurementRegistrationRequest);

        //Validate document IDs from the measurement request
        serviceValidator.validateDocumentIds(measurementRegistrationRequest.getMeasurements());

        // Validate existing data and set audit details
        serviceValidator.validateExistingDataAndEnrich(measurementRegistrationRequest);

        //Updating Cummulative Value
        handleCumulativeUpdate(measurementRegistrationRequest);


        // Create the MeasurementResponse object
        MeasurementResponse response = makeUpdateResponse(measurementRegistrationRequest.getMeasurements(),measurementRegistrationRequest);

        // Push the response to the producer
        producer.push(configuration.getUpdateTopic(), response);

        // Return the success response
        return response;
    }


    /**
     * Helper function to search measurements
     */
     public List<Measurement> searchMeasurements(MeasurementCriteria searchCriteria, MeasurementSearchRequest measurementSearchRequest) {

        if (StringUtils.isEmpty(searchCriteria.getTenantId()) || searchCriteria == null) {
            throw new IllegalArgumentException("TenantId is required.");
        }
        List<Measurement> measurements = serviceRequestRepository.getMeasurements(searchCriteria, measurementSearchRequest);
        return measurements;
    }


    /**
     converting all measurement registry to measurement-Service
     */
    public List<org.egov.works.measurement.web.models.MeasurementService> changeToMeasurementService(List<Measurement> measurements) {
        NamedParameterJdbcTemplate namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
        List<String> mbNumbers = getMbNumbers(measurements);
        List<org.egov.works.measurement.web.models.MeasurementService> measurementServices = serviceRequestRepository.getMeasurementServicesFromMBSTable(namedParameterJdbcTemplate, mbNumbers);
        Map<String, org.egov.works.measurement.web.models.MeasurementService> mbNumberToServiceMap = getMbNumberToServiceMap(measurementServices);
        List<org.egov.works.measurement.web.models.MeasurementService> orderedExistingMeasurementService = serviceValidator.createOrderedMeasurementServiceList(mbNumbers, mbNumberToServiceMap);

        // Create measurement services for each measurement
        List<org.egov.works.measurement.web.models.MeasurementService> result = createMeasurementServices(measurements, orderedExistingMeasurementService);

        return result;
    }

    private List<org.egov.works.measurement.web.models.MeasurementService> createMeasurementServices(List<Measurement> measurements, List<org.egov.works.measurement.web.models.MeasurementService> orderedExistingMeasurementService) {
        List<org.egov.works.measurement.web.models.MeasurementService> measurementServices = new ArrayList<>();

        for (int i = 0; i < measurements.size(); i++) {
            Measurement measurement = measurements.get(i);
            org.egov.works.measurement.web.models.MeasurementService measurementService = new org.egov.works.measurement.web.models.MeasurementService();

            // Set properties of the measurement service based on the measurement object
            measurementService.setId(measurement.getId());
            measurementService.setTenantId(measurement.getTenantId());
            measurementService.setMeasurementNumber(measurement.getMeasurementNumber());
            measurementService.setPhysicalRefNumber(measurement.getPhysicalRefNumber());
            measurementService.setReferenceId(measurement.getReferenceId());
            measurementService.setEntryDate(measurement.getEntryDate());
            measurementService.setMeasures(measurement.getMeasures());
            measurementService.setDocuments(measurement.getDocuments());
            measurementService.setIsActive(measurement.getIsActive());
            measurementService.setAuditDetails(measurement.getAuditDetails());

            // If an existing measurement service exists, set its workflow status
            org.egov.works.measurement.web.models.MeasurementService existingMeasurementService = orderedExistingMeasurementService.get(i);
            if (existingMeasurementService != null) {
                measurementService.setWfStatus(existingMeasurementService.getWfStatus());
            }

            measurementServices.add(measurementService);
        }

        return measurementServices;
    }



    public  List<String> getMbNumbers(List<Measurement> measurements){
        List<String> mbNumbers=new ArrayList<>();
        for(Measurement measurement:measurements){
            mbNumbers.add(measurement.getMeasurementNumber());
        }
        return mbNumbers;
    }

    public Map<String, org.egov.works.measurement.web.models.MeasurementService> getMbNumberToServiceMap(List<org.egov.works.measurement.web.models.MeasurementService> measurementServices){
        Map<String, org.egov.works.measurement.web.models.MeasurementService> mbNumberToServiceMap = new HashMap<>();
        for (org.egov.works.measurement.web.models.MeasurementService existingService : measurementServices) {
            mbNumberToServiceMap.put(existingService.getMeasurementNumber(), existingService);
        }
        return mbNumberToServiceMap;
    }




    public  void handleCumulativeUpdate(MeasurementRequest measurementRequest){
        for(Measurement measurement:measurementRequest.getMeasurements()){
            try {
                enrichCumulativeValueOnUpdate(measurement);
            }
            catch (Exception  e){
                throw new CustomException("","Error during Cumulative enrichment");
            }
        }
    }

    public MeasurementResponse makeUpdateResponse(List<Measurement> measurements,MeasurementRequest measurementRegistrationRequest) {
        MeasurementResponse response = new MeasurementResponse();
        response.setResponseInfo(ResponseInfo.builder()
                .apiId(measurementRegistrationRequest.getRequestInfo().getApiId())
                .msgId(measurementRegistrationRequest.getRequestInfo().getMsgId())
                .ts(measurementRegistrationRequest.getRequestInfo().getTs())
                .status("successful")
                .build());
        response.setMeasurements(measurements);
        return response;
    }

    public MeasurementServiceResponse makeSearchResponse(MeasurementSearchRequest measurementSearchRequest) {
        MeasurementServiceResponse response = new MeasurementServiceResponse();
        response.setResponseInfo(ResponseInfo.builder()
                .apiId(measurementSearchRequest.getRequestInfo().getApiId())
                .msgId(measurementSearchRequest.getRequestInfo().getMsgId())
                .ts(measurementSearchRequest.getRequestInfo().getTs())
                .status("successful")
                .build());
        return response;
    }


    /**
     * Helper function to enrich a measurement
     * @param request
     */
    public void enrichMeasurement(MeasurementRequest request){

        String tenantId = request.getMeasurements().get(0).getTenantId(); // each measurement should have same tenantId otherwise this will fail
        List<String> measurementNumberList = idgenUtil.getIdList(request.getRequestInfo(), tenantId, configuration.getIdName(), configuration.getIdFormat(), request.getMeasurements().size());
        List<Measurement> measurements = request.getMeasurements();
//        enrichCumulativeValue(request);
        for (int i = 0; i < measurements.size(); i++) {
            Measurement measurement = measurements.get(i);

            // enrich UUID
            measurement.setId(UUID.randomUUID());
            // enrich the Audit details
            measurement.setAuditDetails(AuditDetails.builder()
                    .createdBy(request.getRequestInfo().getUserInfo().getUuid())
                    .createdTime(System.currentTimeMillis())
                    .lastModifiedTime(System.currentTimeMillis())
                    .build());

            // enrich measures in a measurement
            enrichMeasures(measurement);
            // enrich IdGen
            measurement.setMeasurementNumber(measurementNumberList.get(i));
            // enrich Cumulative value
            try {
                enrichCumulativeValue(measurement);
            }
            catch (Exception e){
                throw new CustomException("","Error during Cumulative enrichment");
            }
            // measurement.setMeasurementNumber("DEMO_ID_TILL_MDMS_DOWN");  // for local-dev remove this
        }
    }
    public void enrichCumulativeValue(Measurement measurement){
        MeasurementCriteria measurementCriteria = MeasurementCriteria.builder()
                                                  .referenceId(Collections.singletonList(measurement.getReferenceId()))
                                                  .tenantId(measurement.getTenantId())
                                                  .build();
        Pagination pagination= Pagination.builder().offSet(0).build();
        MeasurementSearchRequest measurementSearchRequest=MeasurementSearchRequest.builder().criteria(measurementCriteria).pagination(pagination).build();
        List<Measurement> measurementList = searchMeasurements(measurementCriteria,measurementSearchRequest);
        if(!measurementList.isEmpty()){
            Measurement latestMeasurement = measurementList.get(0);
            calculateCumulativeValue(latestMeasurement,measurement);
        }
        else{
            for(Measure measure : measurement.getMeasures()){
                measure.setCumulativeValue(measure.getCurrentValue());
            }
        }
    }

    public void enrichCumulativeValueOnUpdate(Measurement measurement){
        MeasurementCriteria measurementCriteria = MeasurementCriteria.builder()
                .referenceId(Collections.singletonList(measurement.getReferenceId()))
                .tenantId(measurement.getTenantId())
                .build();
        Pagination pagination= Pagination.builder().offSet(0).build();
        MeasurementSearchRequest measurementSearchRequest=MeasurementSearchRequest.builder().criteria(measurementCriteria).pagination(pagination).build();
        List<Measurement> measurementList =searchMeasurements(measurementCriteria,measurementSearchRequest);
        if(!measurementList.isEmpty()){
            Measurement latestMeasurement = measurementList.get(0);
            calculateCumulativeValueOnUpdate(latestMeasurement,measurement);
        }
        else{
            for(Measure measure : measurement.getMeasures()){
                measure.setCumulativeValue(measure.getCurrentValue());
            }
        }
    }

    public void calculateCumulativeValue(Measurement latestMeasurement,Measurement currMeasurement){
        Map<String,BigDecimal> targetIdtoCumulativeMap = new HashMap<>();
        for(Measure measure:latestMeasurement.getMeasures()){
            targetIdtoCumulativeMap.put(measure.getTargetId(),measure.getCumulativeValue());
        }
        for(Measure measure:currMeasurement.getMeasures()){
            measure.setCumulativeValue( targetIdtoCumulativeMap.get(measure.getTargetId()).add(measure.getCurrentValue()));
        }
    }
    public void calculateCumulativeValueOnUpdate(Measurement latestMeasurement,Measurement currMeasurement){
        Map<String,BigDecimal> targetIdtoCumulativeMap = new HashMap<>();
        for(Measure measure:latestMeasurement.getMeasures()){
            targetIdtoCumulativeMap.put(measure.getTargetId(),measure.getCumulativeValue().subtract(measure.getCurrentValue()));
        }
        for(Measure measure:currMeasurement.getMeasures()){
            measure.setCumulativeValue( targetIdtoCumulativeMap.get(measure.getTargetId()).add(measure.getCurrentValue()));
        }
    }
    /**
     * Helper function to enriches a measure
     * @param measurement
     */
    public void enrichMeasures(Measurement measurement){
        List<Measure> measureList = measurement.getMeasures();
        if(measurement.getDocuments()!=null){
            for(Document document:measurement.getDocuments()){
                document.setId(UUID.randomUUID().toString());
            }
        }
        for (Measure measure : measureList) {
            measure.setId(UUID.randomUUID());
            measure.setReferenceId(measurement.getId().toString());
            measure.setAuditDetails(measurement.getAuditDetails());
            measure.setCurrentValue(measure.getLength().multiply(measure.getHeight().multiply(measure.getBreadth().multiply(measure.getNumItems()))));
        }
    }
}
