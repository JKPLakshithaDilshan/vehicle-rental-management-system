package com.rental.controller;

import com.rental.model.Review;
import com.rental.model.Vehicle;
import com.rental.service.ReviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import com.rental.service.VehicleService;

import jakarta.servlet.http.HttpSession;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/api/reviews")
public class ReviewController {
    private static final String USER_ID_SESSION_KEY = "AUTH_USER_ID";

    private final ReviewService reviewService;
    private final VehicleService vehicleService;

    public ReviewController(ReviewService reviewService, VehicleService vehicleService) {
        this.reviewService = reviewService;
        this.vehicleService = vehicleService;
    }

    @PostMapping
    public ResponseEntity<?> createReview(@RequestBody CreateReviewRequest request, HttpSession session) {
        Long reviewerId = currentUserId(session);
        if (reviewerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Please login first."));
        }

        if (request.vehicleId() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "vehicleId is required."));
        }

        Review created = reviewService.createReview(
                reviewerId,
                request.vehicleId(),
                "vehicle",
                request.rating(),
                request.comment()
        );

        return ResponseEntity.ok(Map.of("message", "Review submitted.", "review", toReviewView(created)));
    }

    @GetMapping("/vehicle/{vehicleId}")
    public ResponseEntity<?> getVehicleReviews(@PathVariable Long vehicleId) {
        List<Map<String, Object>> reviews = reviewService.getReviewsForTarget(vehicleId, "vehicle")
                .stream()
                .map(this::toReviewView)
                .collect(Collectors.toList());
        return ResponseEntity.ok(Map.of("reviews", reviews));
    }

    @GetMapping("/mine/customer")
    public ResponseEntity<?> getMyCustomerReviews(HttpSession session) {
        Long reviewerId = currentUserId(session);
        if (reviewerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Please login first."));
        }

        List<Map<String, Object>> reviews = reviewService.getReviewsByReviewer(reviewerId)
                .stream()
                .filter(r -> "vehicle".equalsIgnoreCase(r.getTargetType()))
                .map(this::toReviewView)
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("reviews", reviews));
    }

    @GetMapping("/mine/renter")
    public ResponseEntity<?> getMyRenterVehicleReviews(HttpSession session) {
        Long sellerId = currentUserId(session);
        if (sellerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Please login first."));
        }

        List<Long> vehicleIds = vehicleService.getVehiclesBySeller(sellerId)
                .stream()
                .map(Vehicle::getId)
                .collect(Collectors.toList());

        List<Map<String, Object>> reviews = vehicleIds.stream()
                .flatMap(id -> reviewService.getReviewsForTarget(id, "vehicle").stream())
                .map(this::toReviewView)
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("reviews", reviews));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateReview(@PathVariable Long id, @RequestBody UpdateReviewRequest request,
                                           HttpSession session) {
        Long reviewerId = currentUserId(session);
        if (reviewerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Please login first."));
        }

        Review existing = reviewService.getReviewById(id).orElse(null);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        if (existing.getReviewerId() != null && !existing.getReviewerId().equals(reviewerId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "You can edit only your own reviews."));
        }

        Review updated = reviewService.updateReview(id, request.rating(), request.comment());
        return ResponseEntity.ok(Map.of("message", "Review updated.", "review", toReviewView(updated)));
    }

    // Delete a review
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteReview(@PathVariable Long id, HttpSession session) {
        Long reviewerId = currentUserId(session);
        if (reviewerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Please login first."));
        }

        Review existing = reviewService.getReviewById(id).orElse(null);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        if (existing.getReviewerId() != null && !existing.getReviewerId().equals(reviewerId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "You can delete only your own reviews."));
        }

        reviewService.deleteReview(id);
        return ResponseEntity.noContent().build();
    }

    private Long currentUserId(HttpSession session) {
        Object raw = session.getAttribute(USER_ID_SESSION_KEY);
        if (raw instanceof Long id) {
            return id;
        }
        if (raw instanceof Integer intId) {
            return intId.longValue();
        }
        return null;
    }

    private Map<String, Object> toReviewView(Review review) {
        return Map.of(
                "id", review.getId(),
                "vehicleId", review.getTargetId(),
                // Frontend calls "customerId" (reviewer).
                "customerId", review.getReviewerId(),
                "rating", review.getRating(),
                "comment", review.getContent(),
                "updatedAt", review.getUpdatedAt() == null ? "" : review.getUpdatedAt().toString()
        );
    }

    public record CreateReviewRequest(Long vehicleId, Long bookingId, int rating, String comment) {
    }

    public record UpdateReviewRequest(int rating, String comment) {
    }
}
