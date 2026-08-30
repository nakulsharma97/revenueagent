package com.razorpay.recovery.recovery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;

/**
 * JPA converter: DecisionTrace <-> JSON string for storage in a @Lob text column.
 */
@Converter(autoApply = false)
public class DecisionTraceConverter implements AttributeConverter<DecisionTrace, String> {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final TypeReference<List<DecisionTrace.Step>> STEP_LIST_TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(DecisionTrace trace) {
        if (trace == null) return null;
        try {
            return mapper.writeValueAsString(trace.getSteps());
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    @Override
    public DecisionTrace convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) return new DecisionTrace();
        try {
            List<DecisionTrace.Step> steps = mapper.readValue(json, STEP_LIST_TYPE);
            DecisionTrace trace = new DecisionTrace();
            for (DecisionTrace.Step step : steps) {
                trace.add(step.step(), step.detail());
            }
            return trace;
        } catch (Exception e) {
            return new DecisionTrace();
        }
    }
}
