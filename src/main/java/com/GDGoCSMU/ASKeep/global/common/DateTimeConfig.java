package com.GDGoCSMU.ASKeep.global.common;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

/**
 * API의 모든 날짜를 "2026-11-20T14:00:00+09:00"처럼 시간대(오프셋)가 붙은 형식으로 주고받는다.
 * - 응답: 서버 시간대(Asia/Seoul) 기준으로 +09:00을 붙여서 내려준다.
 * - 요청: +09:00, Z 등 오프셋이 있으면 서버 시간대로 바꿔서 받고, 없으면 서버 시간(KST)으로 본다.
 */
@Configuration
public class DateTimeConfig {

    @Bean
    SimpleModule offsetDateTimeModule() {
        SimpleModule module = new SimpleModule("offset-date-time");
        module.addSerializer(LocalDateTime.class, new ValueSerializer<>() {
            @Override
            public void serialize(LocalDateTime value, JsonGenerator gen, SerializationContext ctxt) {
                gen.writeString(format(value));
            }
        });
        module.addDeserializer(LocalDateTime.class, new ValueDeserializer<>() {
            @Override
            public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) {
                String text = p.getValueAsString();
                try {
                    return parse(text);
                } catch (DateTimeParseException e) {
                    throw ctxt.weirdStringException(text, LocalDateTime.class,
                            "날짜 형식은 2026-11-20T14:00:00+09:00 처럼 보내주세요");
                }
            }
        });
        return module;
    }

    static String format(LocalDateTime value) {
        return value.atZone(ZoneId.systemDefault())
                .toOffsetDateTime()
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    static LocalDateTime parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        TemporalAccessor parsed = DateTimeFormatter.ISO_DATE_TIME
                .parseBest(text.trim(), OffsetDateTime::from, LocalDateTime::from);
        if (parsed instanceof OffsetDateTime offset) {
            return offset.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        }
        return (LocalDateTime) parsed;
    }
}