package com.rental.model;

import java.time.LocalDateTime;

public class Review {
	private Long id;
	private Long reviewerId;
	private Long targetId; // carId or sellerId
	private String targetType; // "car" or "seller"
	private String content;
	private int rating;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;

	public Review() {
	}

	public Review(Long id, Long reviewerId, Long targetId, String targetType, String content, int rating,
			LocalDateTime createdAt, LocalDateTime updatedAt) {
		this.id = id;
		this.reviewerId = reviewerId;
		this.targetId = targetId;
		this.targetType = targetType;
		this.content = content;
		this.rating = rating;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getReviewerId() {
		return reviewerId;
	}

	public void setReviewerId(Long reviewerId) {
		this.reviewerId = reviewerId;
	}

	public Long getTargetId() {
		return targetId;
	}

	public void setTargetId(Long targetId) {
		this.targetId = targetId;
	}

	public String getTargetType() {
		return targetType;
	}

	public void setTargetType(String targetType) {
		this.targetType = targetType;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public int getRating() {
		return rating;
	}

	public void setRating(int rating) {
		this.rating = rating;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(LocalDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}
}
