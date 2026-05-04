package com.rental.service;

import com.rental.model.Review;
import com.rental.repository.ReviewRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
public class ReviewServiceImpl implements ReviewService {
    private final ReviewRepository reviewRepository;

    public ReviewServiceImpl(ReviewRepository reviewRepository) {
        this.reviewRepository = reviewRepository;
    }

    @Override
    public Review createReview(Long reviewerId, Long targetId, String targetType, int rating, String content) {
        Review review = new Review();
        review.setReviewerId(reviewerId);
        review.setTargetId(targetId);
        review.setTargetType(targetType);
        review.setRating(rating);
        review.setContent(content);
        review.setCreatedAt(java.time.LocalDateTime.now());
        review.setUpdatedAt(java.time.LocalDateTime.now());
        return reviewRepository.save(review);
    }

    @Override
    public List<Review> getReviewsForTarget(Long targetId, String targetType) {
        return reviewRepository.findByTarget(targetId, targetType);
    }

    @Override
    public List<Review> getReviewsByReviewer(Long reviewerId) {
        return reviewRepository.findAll().stream()
                .filter(review -> review.getReviewerId() != null && review.getReviewerId().equals(reviewerId))
                .toList();
    }

    @Override
    public List<Review> getAllReviews() {
        return reviewRepository.findAll();
    }

    @Override
    public Review updateReview(Long reviewId, int rating, String content) {
        Review existing = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found."));
        existing.setRating(rating);
        existing.setContent(content);
        existing.setUpdatedAt(java.time.LocalDateTime.now());
        return reviewRepository.save(existing);
    }

    @Override
    public Optional<Review> getReviewById(Long id) {
        return reviewRepository.findById(id);
    }

    @Override
    public void deleteReview(Long id) {
        reviewRepository.deleteById(id);
    }
}
