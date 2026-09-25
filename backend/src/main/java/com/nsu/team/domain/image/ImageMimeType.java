package com.nsu.team.domain.image;

import com.nsu.team.common.exception.BusinessException;
import com.nsu.team.common.exception.ErrorCode;

public enum ImageMimeType {
	JPEG("image/jpeg"),
	PNG("image/png"),
	WEBP("image/webp");

	private final String contentType;

	ImageMimeType(String contentType) {
		this.contentType = contentType;
	}

	public String getContentType() {
		return contentType;
	}

	public static ImageMimeType fromContentType(String contentType) {
		for (ImageMimeType value : values()) {
			if (value.contentType.equalsIgnoreCase(contentType)) {
				return value;
			}
		}
		throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
	}
}
