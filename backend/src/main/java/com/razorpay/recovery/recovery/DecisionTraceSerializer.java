package com.razorpay.recovery.recovery;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * Serializes DecisionTrace as a flat JSON array of {step, detail} objects
 * in API responses, so the frontend gets a clean array without nesting.
 */
public class DecisionTraceSerializer extends JsonSerializer<DecisionTrace> {

    @Override
    public void serialize(DecisionTrace trace, JsonGenerator gen, SerializerProvider provider) throws IOException {
        if (trace == null) {
            gen.writeStartArray();
            gen.writeEndArray();
            return;
        }
        gen.writeStartArray();
        for (DecisionTrace.Step step : trace.getSteps()) {
            gen.writeStartObject();
            gen.writeStringField("step", step.step());
            gen.writeStringField("detail", step.detail());
            gen.writeEndObject();
        }
        gen.writeEndArray();
    }
}
