package com.rental.repository;

import com.rental.model.Review;
import com.rental.util.FileHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Repository
public class ReviewRepositoryImpl implements ReviewRepository {
    private static final String DELIMITER = "|";
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final Path filePath;
    private final FileHandler fileHandler;

    public ReviewRepositoryImpl(
            @Value("${app.data.reviews-file:src/main/resources/data/reviews.txt}") String file,
            FileHandler fileHandler) {
        this.filePath = Path.of(file);
        this.fileHandler = fileHandler;
        this.fileHandler.ensureFileExists(this.filePath);
    }

    @Override
    public synchronized Review save(Review review) {
        List<Review> reviews = findAll();
        if (review.getId() == null) {
            review.setId(nextId(reviews));
            reviews.add(review);
        } else {
            boolean found = false;
            for (int i = 0; i < reviews.size(); i++) {
                if (reviews.get(i).getId().equals(review.getId())) {
                    reviews.set(i, review);
                    found = true;
                    break;
                }
            }
            if (!found) {
                reviews.add(review);
            }
        }
        writeAll(reviews);
        return review;
    }

    @Override
    public synchronized Optional<Review> findById(Long id) {
        return findAll().stream().filter(r -> r.getId().equals(id)).findFirst();
    }

    @Override
    public synchronized List<Review> findByTarget(Long targetId, String targetType) {
        List<Review> result = new ArrayList<>();
        for (Review review : findAll()) {
            if (Objects.equals(review.getTargetId(), targetId) && Objects.equals(review.getTargetType(), targetType)) {
                result.add(review);
            }
        }
        return result;
    }

    @Override
    public synchronized List<Review> findAll() {
        List<Review> reviews = new ArrayList<>();
        for (String line : fileHandler.readLines(filePath)) {
            if (line == null || line.isBlank()) {
                continue;
            }
            reviews.add(parseLine(line));
        }
        return reviews;
    }

    @Override
    public synchronized void deleteById(Long id) {
        List<Review> reviews = findAll();
        if (reviews.removeIf(r -> r.getId().equals(id))) {
            writeAll(reviews);
        }
    }

    private void writeAll(List<Review> reviews) {
        List<String> rows = reviews.stream().map(this::toLine).toList();
        fileHandler.writeLines(filePath, rows);
    }

    private Long nextId(List<Review> reviews) {
        return reviews.stream().map(Review::getId).max(Comparator.naturalOrder()).orElse(0L) + 1;
    }

    private String toLine(Review review) {
        String createdAt = review.getCreatedAt() == null ? "" : DATETIME_FORMATTER.format(review.getCreatedAt());
        String updatedAt = review.getUpdatedAt() == null ? "" : DATETIME_FORMATTER.format(review.getUpdatedAt());

        return review.getId() + DELIMITER
                + review.getReviewerId() + DELIMITER
                + review.getTargetId() + DELIMITER
                + escape(review.getTargetType()) + DELIMITER
                + escape(review.getContent()) + DELIMITER
                + review.getRating() + DELIMITER
                + createdAt + DELIMITER
                + updatedAt;
    }

    private Review parseLine(String line) {
        String[] parts = line.split("\\|", -1);
        if (parts.length != 8 && parts.length != 9) {
            throw new IllegalStateException("Corrupted review row: " + line);
        }

        if (parts.length == 9) {
            // Legacy format: id|reviewerId|vehicleId|bookingId|sellerId|rating|content|createdAt|updatedAt
            Long id = Long.parseLong(parts[0]);
            Long reviewerId = Long.parseLong(parts[1]);
            Long targetId = Long.parseLong(parts[2]);
            String targetType = "vehicle"; // default for legacy format
            int rating = Integer.parseInt(parts[5]);
            String content = unescape(parts[6]);
            LocalDateTime createdAt = parseDateTime(parts[7]);
            LocalDateTime updatedAt = parseDateTime(parts[8]);

            return new Review(id, reviewerId, targetId, targetType, content, rating, createdAt, updatedAt);
        }

        // New format: id|reviewerId|targetId|targetType|content|rating|createdAt|updatedAt
        Long id = Long.parseLong(parts[0]);
        Long reviewerId = Long.parseLong(parts[1]);
        Long targetId = Long.parseLong(parts[2]);
        String targetType = unescape(parts[3]);
        String content = unescape(parts[4]);
        int rating = Integer.parseInt(parts[5]);
        LocalDateTime createdAt = parseDateTime(parts[6]);
        LocalDateTime updatedAt = parseDateTime(parts[7]);

        return new Review(id, reviewerId, targetId, targetType, content, rating, createdAt, updatedAt);
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(value, DATETIME_FORMATTER);
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("|", "\\|")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private String unescape(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        boolean escaping = false;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (escaping) {
                switch (character) {
                    case 'n' -> result.append('\n');
                    case 'r' -> result.append('\r');
                    default -> result.append(character);
                }
                escaping = false;
            } else if (character == '\\') {
                escaping = true;
            } else {
                result.append(character);
            }
        }

        if (escaping) {
            result.append('\\');
        }
        return result.toString();
    }
}
