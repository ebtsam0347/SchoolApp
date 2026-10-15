package com.forward.hrms.commons.core.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.forward.hrms.commons.core.money.Money;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * Money is serialized as a JSON decimal number with two decimal places
 * (e.g. {@code "basic": 75000.00}). Deserialization accepts either a
 * decimal number or a string — the wire used to be a paisa long, and a
 * client that hasn't migrated will still send strings during the
 * transition.
 */
public class MoneyJacksonModule extends SimpleModule {

    public MoneyJacksonModule() {
        super("hrms-money");
        addSerializer(Money.class, new JsonSerializer<>() {
            @Override
            public void serialize(Money value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                gen.writeNumber(value.amount());
            }
        });
        addDeserializer(Money.class, new JsonDeserializer<>() {
            @Override
            public Money deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                JsonToken t = p.currentToken();
                if (t == JsonToken.VALUE_NUMBER_FLOAT || t == JsonToken.VALUE_NUMBER_INT) {
                    return Money.of(p.getDecimalValue());
                }
                if (t == JsonToken.VALUE_STRING) {
                    String text = p.getText().trim();
                    if (text.isEmpty()) return Money.ZERO;
                    return Money.of(new BigDecimal(text));
                }
                return (Money) ctxt.handleUnexpectedToken(Money.class, p);
            }
        });
    }
}
