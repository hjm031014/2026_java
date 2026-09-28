package com.nsu.team.domain.image;

import com.nsu.team.common.exception.BusinessException;
import com.nsu.team.common.exception.ErrorCode;
import com.nsu.team.domain.image.dto.ImageResponse;
import com.nsu.team.domain.user.User;
import com.nsu.team.domain.user.UserRepository;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
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
		Long size = toLong(uploadResult.get("bytes"));
		PostImage image = PostImage.builder()
				.uploader(uploader)
				.imageUrl(url)
				.mimeType(mimeType)
				.sizeBytes(size == null ? file.getSize() : size)
				.width(width)
				.height(height)
				.build();
		postImageRepository.save(image);

		return ImageResponse.from(image);
	}

	@Transactional
	public void deleteUnattached(Long imageId, Long requesterId) {
		PostImage image = postImageRepository.findByIdForUpdate(imageId)
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
		ImageMimeType declared = ImageMimeType.fromContentType(file.getContentType());
		ImageMimeType detected = detectSignature(file);
		if (declared != detected) {
			throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
		}
		return detected;
	}

	private ImageMimeType detectSignature(MultipartFile file) {
		byte[] header = new byte[12];
		int length;
		try (InputStream input = file.getInputStream()) {
			length = input.read(header);
		} catch (IOException e) {
			throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
		}
		if (length >= 3 && (header[0] & 0xff) == 0xff && (header[1] & 0xff) == 0xd8
				&& (header[2] & 0xff) == 0xff) {
			return ImageMimeType.JPEG;
		}
		byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
		if (length >= 8 && Arrays.equals(Arrays.copyOf(header, 8), png)) {
			return ImageMimeType.PNG;
		}
		if (length >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
				&& header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
			return ImageMimeType.WEBP;
		}
		throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
	}

	private Integer toInteger(Object value) {
		if (value instanceof Number number) {
			return number.intValue();
		}
		return null;
	}

	private Long toLong(Object value) {
		return value instanceof Number number ? number.longValue() : null;
	}
}
