package com.GDGoCSMU.ASKeep.domain.session.summary;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 태그 목록을 한 컬럼(TEXT)에 줄바꿈으로 구분해 저장한다. 태그 안의 줄바꿈은 저장 전에 제거된다. */
@Converter
public class TagListConverter implements AttributeConverter<List<String>, String> {

    private static final String SEPARATOR = "\n";

    @Override
    public String convertToDatabaseColumn(List<String> tags) {
        return (tags == null || tags.isEmpty()) ? null : String.join(SEPARATOR, tags);
    }

    @Override
    public List<String> convertToEntityAttribute(String column) {
        if (column == null || column.isBlank()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(column.split(SEPARATOR)));
    }
}
