package com.ceudelavanda.lavandaflow.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

import java.math.BigDecimal;

/**
 * Configures exact decimal HTTP response serialization.
 *
 * <p>Plain JSON strings keep browser clients from coercing supported {@link BigDecimal} values
 * through IEEE-754 numbers. Request deserialization intentionally remains Jackson's standard
 * {@code BigDecimal} behavior, which accepts both quoted decimal strings and legacy JSON numbers.</p>
 */
@Configuration(proxyBeanMethods = false)
public class ExactDecimalJsonConfiguration {

    @Bean
    JacksonModule exactDecimalJsonModule() {
        var module = new SimpleModule();
        module.addSerializer(BigDecimal.class, new PlainBigDecimalSerializer());
        return module;
    }

    private static final class PlainBigDecimalSerializer extends ValueSerializer<BigDecimal> {

        @Override
        public void serialize(BigDecimal value, JsonGenerator generator, SerializationContext context)
            throws JacksonException {
            generator.writeString(value.toPlainString());
        }
    }
}
