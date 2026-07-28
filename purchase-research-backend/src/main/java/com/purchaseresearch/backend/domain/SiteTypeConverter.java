package com.purchaseresearch.backend.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class SiteTypeConverter implements AttributeConverter<SiteType, Integer> {

	@Override
	public Integer convertToDatabaseColumn(SiteType attribute) {
		return attribute == null ? null : attribute.getCode();
	}

	@Override
	public SiteType convertToEntityAttribute(Integer dbData) {
		return dbData == null ? null : SiteType.fromCode(dbData);
	}
}
