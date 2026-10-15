package com.forward.hrms.attendance.entity;

import com.forward.hrms.attendance.enums.FactoryCode;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Persists {@link FactoryCode} as its literal code (e.g. {@code Administrator}). */
@Converter
public class FactoryCodeConverter implements AttributeConverter<FactoryCode, String> {

    @Override
    public String convertToDatabaseColumn(FactoryCode attribute) {
        return attribute == null ? null : attribute.code();
    }

    @Override
    public FactoryCode convertToEntityAttribute(String dbData) {
        return FactoryCode.fromCode(dbData);
    }
}
