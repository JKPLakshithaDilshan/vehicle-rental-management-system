package com.rental.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.rental.model.Admin;
import com.rental.model.Review;
import com.rental.model.User;
import com.rental.model.Vehicle;
import com.rental.service.AdminService;
import com.rental.service.ReviewService;
import com.rental.service.UserService;
import com.rental.service.VehicleService;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

	private static final String AUTH_ROLE_SESSION_KEY = "AUTH_ROLE";
	private static final String AUTH_ADMIN_ID_SESSION_KEY = "AUTH_ADMIN_ID";

	private final AdminService adminService;
	private final UserService userService;
	private final VehicleService vehicleService;
	private final ReviewService reviewService;

	public AdminController(AdminService adminService, UserService userService, VehicleService vehicleService,
			ReviewService reviewService) {
		this.adminService = adminService;
		this.userService = userService;
		this.vehicleService = vehicleService;
		this.reviewService = reviewService;
	}

	@PostMapping("/admins")
	public ResponseEntity<?> createAdmin(@RequestBody AdminRequest request, HttpSession session) {
		if (!isAdmin(session) && adminService.countAdmins() > 0) {
			return forbidden();
		}

		try {
			Admin admin = adminService.createAdmin(request.name(), request.email(), request.password(), request.role());
			return ResponseEntity.status(HttpStatus.CREATED)
					.body(Map.of("message", "Admin created successfully.", "admin", toAdminView(admin)));
		} catch (IllegalArgumentException exception) {
			return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
		}
	}

	@GetMapping("/me")
	public ResponseEntity<?> getMyAdminAccount(HttpSession session) {
		if (!isAdmin(session)) {
			return forbidden();
		}

		String adminId = currentAdminId(session);
		if (adminId == null || adminId.isBlank()) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Please login first."));
		}

		Admin admin = adminService.searchByAdminId(adminId).orElse(null);
		if (admin == null) {
			session.invalidate();
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Session expired."));
		}

		return ResponseEntity.ok(Map.of("admin", toAdminView(admin)));
	}

	@PutMapping("/me")
	public ResponseEntity<?> updateMyAdminAccount(@RequestBody AdminSelfUpdateRequest request, HttpSession session) {
		if (!isAdmin(session)) {
			return forbidden();
		}

		String adminId = currentAdminId(session);
		if (adminId == null || adminId.isBlank()) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Please login first."));
		}

		try {
			Admin current = adminService.searchByAdminId(adminId)
					.orElseThrow(() -> new IllegalArgumentException("Admin not found."));
			Admin updated = adminService.updateAdmin(adminId, request.name(), request.email(), request.password(),
					current.getRole());
			return ResponseEntity.ok(Map.of("message", "Admin account updated successfully.", "admin", toAdminView(updated)));
		} catch (IllegalArgumentException exception) {
			return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
		}
	}

	@GetMapping("/admins")
	public ResponseEntity<?> viewAllAdmins(HttpSession session) {
		if (!isAdmin(session)) {
			return forbidden();
		}

		List<Map<String, Object>> admins = adminService.getAllAdmins().stream().map(this::toAdminView).toList();
		return ResponseEntity.ok(Map.of("admins", admins));
	}

	@GetMapping("/admins/{adminId}")
	public ResponseEntity<?> searchAdminById(@PathVariable String adminId, HttpSession session) {
		if (!isAdmin(session)) {
			return forbidden();
		}

		Admin admin = adminService.searchByAdminId(adminId).orElse(null);
		if (admin == null) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Admin not found."));
		}

		return ResponseEntity.ok(Map.of("admin", toAdminView(admin)));
	}

	@PutMapping("/admins/{adminId}")
	public ResponseEntity<?> updateAdmin(@PathVariable String adminId, @RequestBody AdminRequest request,
			HttpSession session) {
		if (!isAdmin(session)) {
			return forbidden();
		}

		try {
			Admin updated = adminService.updateAdmin(adminId, request.name(), request.email(), request.password(),
					request.role());
			return ResponseEntity.ok(Map.of("message", "Admin updated successfully.", "admin", toAdminView(updated)));
		} catch (IllegalArgumentException exception) {
			return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
		}
	}

	@DeleteMapping("/admins/{adminId}")
	public ResponseEntity<?> deleteAdmin(@PathVariable String adminId, HttpSession session) {
		if (!isAdmin(session)) {
			return forbidden();
		}

		try {
			adminService.deleteAdmin(adminId);
			return ResponseEntity.ok(Map.of("message", "Admin deleted successfully."));
		} catch (IllegalArgumentException exception) {
			return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
		}
	}

	@GetMapping("/users")
	public ResponseEntity<?> viewAndSearchUsers(@RequestParam(required = false) String query, HttpSession session) {
		if (!isAdmin(session)) {
			return forbidden();
		}

		List<Map<String, Object>> users = userService.searchUsers(query).stream()
				.map(this::toUserView)
				.collect(Collectors.toList());

		return ResponseEntity.ok(Map.of("users", users));
	}

	@DeleteMapping("/users/{userId}")
	public ResponseEntity<?> deleteUser(@PathVariable Long userId, HttpSession session) {
		if (!isAdmin(session)) {
			return forbidden();
		}

		try {
			userService.deleteUserByAdmin(userId);
			return ResponseEntity.ok(Map.of("message", "User deleted successfully."));
		} catch (IllegalArgumentException exception) {
			return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
		}
	}

	@GetMapping("/vehicles")
	public ResponseEntity<?> viewAndSearchVehicles(@RequestParam(required = false) String query, HttpSession session) {
		if (!isAdmin(session)) {
			return forbidden();
		}

		       List<Map<String, Object>> vehicles = vehicleService.getAvailableVehicles(query).stream()
			       .map(this::toVehicleView)
			       .toList();
		return ResponseEntity.ok(Map.of("vehicles", vehicles));
	}

	@DeleteMapping("/vehicles/{vehicleId}")
	public ResponseEntity<?> deleteVehicleByAdmin(@PathVariable Long vehicleId, HttpSession session) {
		if (!isAdmin(session)) {
			return forbidden();
		}

		try {
			vehicleService.deleteVehicleByAdmin(vehicleId);
			return ResponseEntity.ok(Map.of("message", "Vehicle removed successfully."));
		} catch (IllegalArgumentException exception) {
			return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
		}
	}

	@GetMapping("/reviews")
	public ResponseEntity<?> viewAndSearchReviews(@RequestParam(required = false) String query, HttpSession session) {
		if (!isAdmin(session)) {
			return forbidden();
		}

		String normalizedQuery = query == null ? "" : query.trim().toLowerCase();
		List<Map<String, Object>> reviews = reviewService.getAllReviews().stream()
				.filter(review -> {
					if (normalizedQuery.isBlank()) {
						return true;
					}
					String id = String.valueOf(review.getId() == null ? "" : review.getId());
					String vehicleId = String.valueOf(review.getTargetId() == null ? "" : review.getTargetId());
					String customerId = String.valueOf(review.getReviewerId() == null ? "" : review.getReviewerId());
					String rating = String.valueOf(review.getRating());
					String content = review.getContent() == null ? "" : review.getContent().toLowerCase();

					return id.contains(normalizedQuery)
							|| vehicleId.contains(normalizedQuery)
							|| customerId.contains(normalizedQuery)
							|| rating.contains(normalizedQuery)
							|| content.contains(normalizedQuery);
				})
				.map(this::toReviewView)
				.toList();
		return ResponseEntity.ok(Map.of("reviews", reviews));
	}

	@DeleteMapping("/reviews/{reviewId}")
	public ResponseEntity<?> deleteReviewByAdmin(@PathVariable Long reviewId, HttpSession session) {
		if (!isAdmin(session)) {
			return forbidden();
		}

		   try {
			   reviewService.deleteReview(reviewId);
			   return ResponseEntity.ok(Map.of("message", "Review removed successfully."));
		   } catch (IllegalArgumentException exception) {
			   return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
		   }
	}

	private boolean isAdmin(HttpSession session) {
		Object role = session.getAttribute(AUTH_ROLE_SESSION_KEY);
		if (!(role instanceof String roleText)) {
			return false;
		}
		return "ADMIN".equalsIgnoreCase(roleText) || "SUPERADMIN".equalsIgnoreCase(roleText);
	}

	private ResponseEntity<Map<String, String>> forbidden() {
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Admin access required."));
	}

	private String currentAdminId(HttpSession session) {
		Object raw = session.getAttribute(AUTH_ADMIN_ID_SESSION_KEY);
		if (raw instanceof String adminId) {
			return adminId;
		}
		return null;
	}

	private Map<String, Object> toAdminView(Admin admin) {
		return Map.of(
				"adminId", admin.getAdminId(),
				"name", admin.getName(),
				"email", admin.getEmail(),
				"role", admin.getRole());
	}

	private Map<String, Object> toUserView(User user) {
		String effectiveRole = resolveUserType(user);
		return Map.of(
				"id", user.getId(),
				"name", user.getName(),
				"email", user.getEmail(),
				"phone", user.getPhone(),
				"address", user.getAddress(),
				"role", effectiveRole,
				"userType", effectiveRole);
	}

	   private Map<String, Object> toVehicleView(Vehicle vehicle) {
		   String imageUrl = (vehicle.getImages() == null || vehicle.getImages().isEmpty()) ? "" : vehicle.getImages().get(0);
		   return Map.ofEntries(
				   Map.entry("id", vehicle.getId()),
				   // Admin UI calls this column "Renter" (seller) for legacy reasons.
				   Map.entry("renterId", vehicle.getSellerId()),
				   Map.entry("sellerId", vehicle.getSellerId()),
				   Map.entry("brand", vehicle.getBrand()),
				   Map.entry("model", vehicle.getModel()),
				   Map.entry("title", vehicle.getTitle()),
				   Map.entry("year", vehicle.getYear()),
				   Map.entry("pricePerDay", vehicle.getPrice()),
				   Map.entry("price", vehicle.getPrice()),
				   Map.entry("imageUrl", imageUrl),
				   Map.entry("images", vehicle.getImages()),
				   Map.entry("status", vehicle.getStatus()),
				   Map.entry("description", vehicle.getDescription() == null ? "" : vehicle.getDescription()),
				   Map.entry("createdAt", vehicle.getCreatedAt() == null ? "" : vehicle.getCreatedAt().toString()),
				   Map.entry("updatedAt", vehicle.getUpdatedAt() == null ? "" : vehicle.getUpdatedAt().toString()));
	   }

	   private Map<String, Object> toReviewView(Review review) {
		   String targetType = review.getTargetType();
		   String vehicleName = "";
		   if ("vehicle".equalsIgnoreCase(targetType)) {
			   Vehicle vehicle = vehicleService.findVehicle(review.getTargetId()).orElse(null);
			   vehicleName = vehicle != null ? (vehicle.getBrand() + " " + vehicle.getModel()).trim() : ("Vehicle #" + review.getTargetId());
		   } else {
			   vehicleName = "Target #" + review.getTargetId();
		   }
		       return Map.ofEntries(
			       Map.entry("id", review.getId()),
			       Map.entry("vehicleId", review.getTargetId()),
			       Map.entry("vehicleName", vehicleName),
			       Map.entry("rating", review.getRating()),
			       // Frontend expects `comment` and `customerId`.
			       Map.entry("comment", review.getContent() == null ? "" : review.getContent()),
			       Map.entry("customerId", review.getReviewerId()),
			       Map.entry("updatedAt", review.getUpdatedAt() == null ? "" : review.getUpdatedAt().toString()));
	   }

	   private String resolveUserType(User user) {
		   if (user == null || user.getId() == null) {
			   return "BUYER";
		   }
		   boolean hasListedVehicles = !vehicleService.getVehiclesBySeller(user.getId()).isEmpty();
		   return hasListedVehicles ? "SELLER" : "BUYER";
	   }

	public record AdminRequest(String name, String email, String password, String role) {
	}

	public record AdminSelfUpdateRequest(String name, String email, String password) {
	}
}
