package com.rental.repository;

import org.springframework.stereotype.Repository;

import com.rental.model.Review;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository {
    Review save(Review review);
    Optional<Review> findById(Long id);
    List<Review> findByTarget(Long targetId, String targetType);
    List<Review> findAll();
    void deleteById(Long id);
}
