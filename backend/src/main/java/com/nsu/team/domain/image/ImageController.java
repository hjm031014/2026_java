package com.nsu.team.domain.image;

import com.nsu.team.common.response.ApiResponse;
import com.nsu.team.domain.image.dto.ImageResponse;
import com.nsu.team.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/images")
@RequiredArgsConstructor
public class ImageController {

	private final ImageService imageService;

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<ApiResponse<ImageResponse>> upload(
			@RequestPart("file") MultipartFile file,
			@AuthenticationPrincipal UserPrincipal principal) {
		ImageResponse response = imageService.upload(file, principal.userId());
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response));
	}

	@DeleteMapping("/{imageId}")
	public ResponseEntity<Void> delete(
			@PathVariable Long imageId,
			@AuthenticationPrincipal UserPrincipal principal) {
		imageService.deleteUnattached(imageId, principal.userId());
		return ResponseEntity.noContent().build();
	}
}
