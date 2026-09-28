package com.nsu.team.domain.image;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** PostgreSQL에는 표준 MIME 문자열(image/jpeg 등)로 저장합니다. */
@Converter
public class ImageMimeTypeConverter implements AttributeConverter<ImageMimeType, String> {

	@Override
	public String convertToDatabaseColumn(ImageMimeType attribute) {
		return attribute == null ? null : attribute.getContentType();
	}

	@Override
	public ImageMimeType convertToEntityAttribute(String dbData) {
		return dbData == null ? null : ImageMimeType.fromContentType(dbData);
	}
}
