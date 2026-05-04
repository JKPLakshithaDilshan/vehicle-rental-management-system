package com.rental.service;

import com.rental.model.Review;
import java.util.List;
import java.util.Optional;

public interface ReviewService {
    Review createReview(Long reviewerId, Long targetId, String targetType, int rating, String content);
    List<Review> getReviewsForTarget(Long targetId, String targetType);
    List<Review> getReviewsByReviewer(Long reviewerId);
    List<Review> getAllReviews();
    Review updateReview(Long reviewId, int rating, String content);
    Optional<Review> getReviewById(Long id);
    void deleteReview(Long id);
}
