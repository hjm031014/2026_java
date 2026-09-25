package com.campusmarket.domain.image.dto;

import com.campusmarket.domain.image.PostImage;

public record ImageResponse(
		String id,
		String url,
		String mimeType,
		long size,
		Integer width,
		Integer height
) {
	public static ImageResponse from(PostImage image) {
		return new ImageResponse(
				String.valueOf(image.getId()),
				image.getImageUrl(),
				image.getMimeType().getContentType(),
				image.getSizeBytes(),
				image.getWidth(),
				image.getHeight()
		);
	}
}
