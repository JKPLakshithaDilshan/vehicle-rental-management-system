// ...existing code...
package com.rental.controller;

import com.rental.model.Seller;
import com.rental.service.SellerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/sellers")
public class SellerController {
        // Get the currently logged-in seller's profile by email
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentSeller(jakarta.servlet.http.HttpSession session) {
        Object userObj = session.getAttribute("AUTH_USER_ID");
        Object roleObj = session.getAttribute("AUTH_ROLE");
        if (userObj == null || roleObj == null || !"SELLER".equalsIgnoreCase(roleObj.toString())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Not authenticated as seller."));
        }
        // Find seller by user id (if you store seller id in session) or by email (if you store email)
        // Here, we assume userObj is the seller's user id, but if you store email, adjust accordingly
        // Let's try to get the seller by user id first, then fallback to email if needed
        Long sellerId = null;
        try {
            sellerId = Long.parseLong(userObj.toString());
        } catch (Exception ignore) {}
        Optional<Seller> seller = (sellerId != null)
            ? sellerService.getSellerById(sellerId)
            : Optional.empty();
        // If not found by id, try by email if userObj looks like an email
        if (seller.isEmpty() && userObj.toString().contains("@")) {
            seller = sellerService.getSellerByEmail(userObj.toString());
        }
        return seller
            .<ResponseEntity<?>>map(value -> ResponseEntity.ok(Map.of("seller", toSellerView(value))))
            .orElseGet(() -> ResponseEntity.ok(Map.of("seller", Map.of())));
    }
    private final SellerService sellerService;

    public SellerController(SellerService sellerService) {
        this.sellerService = sellerService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerSeller(@RequestBody Seller seller) {
        try {
            Seller created = sellerService.registerSeller(seller);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "Seller registered.", "seller", toSellerView(created)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getSeller(@PathVariable Long id) {
        Optional<Seller> seller = sellerService.getSellerById(id);
        return seller
                .<ResponseEntity<?>>map(value -> ResponseEntity.ok(Map.of("seller", toSellerView(value))))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchSellers(@RequestParam(required = false) String name,
                                                      @RequestParam(required = false) String location) {
        List<Seller> sellers = sellerService.searchSellers(name, location);
        return ResponseEntity.ok(Map.of("sellers", sellers.stream().map(this::toSellerView).toList()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateSeller(@PathVariable Long id, @RequestBody Seller seller) {
        seller.setId(id);
        try {
            Seller updated = sellerService.updateSeller(seller);
            return ResponseEntity.ok(Map.of("message", "Seller updated.", "seller", toSellerView(updated)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteSeller(@PathVariable Long id) {
        sellerService.deleteSeller(id);
        return ResponseEntity.noContent().build();
    }

    // Admin: get all pending sellers
    @GetMapping("/pending")
    public ResponseEntity<?> getPendingSellers() {
        List<Seller> pending = sellerService.getPendingSellers();
        return ResponseEntity.ok(Map.of("sellers", pending.stream().map(this::toSellerView).toList()));
    }

    // Admin: approve seller
    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approveSeller(@PathVariable Long id) {
        try {
            Seller approved = sellerService.approveSeller(id);
            return ResponseEntity.ok(Map.of("message", "Seller approved.", "seller", toSellerView(approved)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // Admin: reject seller
    @PostMapping("/{id}/reject")
    public ResponseEntity<?> rejectSeller(@PathVariable Long id) {
        try {
            Seller rejected = sellerService.rejectSeller(id);
            return ResponseEntity.ok(Map.of("message", "Seller rejected.", "seller", toSellerView(rejected)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // Seller: get own approval status
    @GetMapping("/{id}/status")
    public ResponseEntity<?> getSellerStatus(@PathVariable Long id) {
        Optional<Seller> seller = sellerService.getSellerById(id);
        if (seller.isPresent()) {
            return ResponseEntity.ok(Map.of("isApproved", seller.get().isApproved()));
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    private Map<String, Object> toSellerView(Seller seller) {
        return Map.of(
                "id", seller.getId(),
                "name", seller.getName() == null ? "" : seller.getName(),
                "contact", seller.getContact() == null ? "" : seller.getContact(),
                "email", seller.getEmail() == null ? "" : seller.getEmail(),
                "location", seller.getLocation() == null ? "" : seller.getLocation(),
                "type", seller.getType() == null ? "" : seller.getType(),
                "image", seller.getImage() == null ? "" : seller.getImage(),
                "carIds", seller.getCarIds() == null ? List.of() : seller.getCarIds(),
                "isApproved", seller.isApproved()
        );
    }
}
