package com.campusmarket.domain.image;

import com.campusmarket.common.entity.BaseTimeEntity;
import com.campusmarket.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * <p>post_id 는 SALE_POSTS(B 파트에서 구현)를 참조하지만, 아직 Post 엔티티가 없어
 * 지금은 일반 컬럼(nullable Long)으로만 선언했습니다. B가 Post 엔티티를 추가하면
 * {@code @ManyToOne} 연관관계와 DB FK 제약으로 교체해주세요. post_id 가 null 이면
 * "임시 업로드(글에 아직 첨부되지 않음)" 상태를 의미합니다.
 */
@Entity
@Table(name = "post_images")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostImage extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "post_id")
	private Long postId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "uploader_id", nullable = false)
	private User uploader;

	@Column(name = "image_url", nullable = false, length = 500)
	private String imageUrl;

	@Enumerated(EnumType.STRING)
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
	private PostImage(User uploader, String imageUrl, ImageMimeType mimeType, Long sizeBytes,
			Integer width, Integer height) {
		this.uploader = uploader;
		this.imageUrl = imageUrl;
		this.mimeType = mimeType;
		this.sizeBytes = sizeBytes;
		this.width = width;
		this.height = height;
		this.displayOrder = 0;
	}

	public boolean isAttached() {
		return postId != null;
	}
}
