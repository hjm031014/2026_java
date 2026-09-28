package com.nsu.team.domain.image;

import com.nsu.team.common.entity.BaseTimeEntity;
import com.nsu.team.domain.user.User;
import com.nsu.team.post.SalePost;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * ERD POST_IMAGES 테이블.
 *
 * <p>post_id 가 null 이면 임시 업로드, 값이 있으면 판매글에 첨부된 상태입니다.
 */
@Entity
@Table(name = "post_images")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostImage extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "post_id")
	private SalePost post;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "uploader_id", nullable = false)
	private User uploader;

	@Column(name = "image_url", nullable = false, length = 500)
	private String imageUrl;

	@Column(name = "cloudinary_public_id", length = 255)
	private String cloudinaryPublicId;

	@Convert(converter = ImageMimeTypeConverter.class)
	@Column(name = "mime_type", nullable = false, length = 10)
	private ImageMimeType mimeType;

	@Column(name = "size_bytes", nullable = false)
	private Long sizeBytes;

	private Integer width;

	private Integer height;

	@Column(name = "display_order")
	private Integer displayOrder;

	@Column(name = "expires_at")
	private Instant expiresAt;

	@Column(name = "attached_at")
	private Instant attachedAt;

	@Builder
	private PostImage(User uploader, String imageUrl, String cloudinaryPublicId, ImageMimeType mimeType, Long sizeBytes,
			Integer width, Integer height) {
		this.uploader = uploader;
		this.imageUrl = imageUrl;
		this.cloudinaryPublicId = cloudinaryPublicId;
		this.mimeType = mimeType;
		this.sizeBytes = sizeBytes;
		this.width = width;
		this.height = height;
		this.displayOrder = 0;
	}

	public boolean isAttached() {
		return post != null;
	}

	public void attachTo(SalePost post, int displayOrder) {
		this.post = post;
		this.displayOrder = displayOrder;
		this.attachedAt = Instant.now();
		this.expiresAt = null;
	}

	public void detach() {
		this.post = null;
		this.displayOrder = 0;
		this.attachedAt = null;
	}
}
