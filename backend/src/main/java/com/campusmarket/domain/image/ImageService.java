package com.campusmarket.domain.image;

import com.campusmarket.common.exception.BusinessException;
import com.campusmarket.common.exception.ErrorCode;
import com.campusmarket.domain.image.dto.ImageResponse;
import com.campusmarket.domain.user.User;
import com.campusmarket.domain.user.UserRepository;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * 이미지 업로드/삭제. 실제 파일은 Cloudinary 에 저장하고, 반환된 URL만 DB(post_images)에 저장합니다.
 * (API.md "이미지 저장" 규칙)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageService {

	private static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;

	private final PostImageRepository postImageRepository;
	private final UserRepository userRepository;
	private final Cloudinary cloudinary;

	@Transactional
	public ImageResponse upload(MultipartFile file, Long uploaderId) {
		ImageMimeType mimeType = validate(file);

		Map<?, ?> uploadResult;
		try {
			uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap("resource_type", "image"));
		} catch (IOException e) {
			log.error("Cloudinary 업로드 실패", e);
			throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "이미지 업로드에 실패했습니다.");
		}

		String url = String.valueOf(uploadResult.get("secure_url"));
		Integer width = toInteger(uploadResult.get("width"));
		Integer height = toInteger(uploadResult.get("height"));

		User uploader = userRepository.getReferenceById(uploaderId);
		PostImage image = PostImage.builder()
				.uploader(uploader)
				.imageUrl(url)
				.mimeType(mimeType)
				.sizeBytes(file.getSize())
				.width(width)
				.height(height)
				.build();
		postImageRepository.save(image);

		return ImageResponse.from(image);
	}

	@Transactional
	public void deleteUnattached(Long imageId, Long requesterId) {
		PostImage image = postImageRepository.findById(imageId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

		if (!image.getUploader().getId().equals(requesterId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}
		if (image.isAttached()) {
			throw new BusinessException(ErrorCode.IMAGE_IN_USE);
		}

		postImageRepository.delete(image);
	}

	private ImageMimeType validate(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "파일이 비어 있습니다.");
		}
		if (file.getSize() > MAX_SIZE_BYTES) {
			throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE);
		}
		return ImageMimeType.fromContentType(file.getContentType());
	}

	private Integer toInteger(Object value) {
		if (value instanceof Number number) {
			return number.intValue();
		}
		return null;
	}
}
