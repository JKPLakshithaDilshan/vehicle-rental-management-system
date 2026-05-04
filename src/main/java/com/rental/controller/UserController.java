package com.rental.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


import com.rental.model.Admin;
import com.rental.model.User;
import com.rental.model.Seller;
import com.rental.service.AdminService;
import com.rental.service.UserService;
import com.rental.service.SellerService;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api")
public class UserController {

	private static final String USER_ID_SESSION_KEY = "AUTH_USER_ID";
	private static final String ADMIN_ID_SESSION_KEY = "AUTH_ADMIN_ID";
	private static final String AUTH_ROLE_SESSION_KEY = "AUTH_ROLE";

	private final UserService userService;
	private final AdminService adminService;
	private final SellerService sellerService;

	public UserController(UserService userService, AdminService adminService, SellerService sellerService) {
		this.userService = userService;
		this.adminService = adminService;
		this.sellerService = sellerService;
	}

	@PostMapping("/auth/register")
	public ResponseEntity<?> register(@RequestBody RegisterRequest request, HttpSession session) {
		try {
			String role = normalizeBuyerSellerDealerRole(request.role());
			session.removeAttribute(ADMIN_ID_SESSION_KEY);
			String redirectPage = "account.html";
			if ("SELLER".equalsIgnoreCase(role) || "DEALER".equalsIgnoreCase(role)) {
				// Save seller in sellers.txt
				Seller seller = new Seller();
				seller.setName(request.name());
				seller.setEmail(request.email());
				seller.setContact(request.phone());
				seller.setPassword(request.password());
				seller.setLocation(request.address());
				seller.setType("SELLER".equalsIgnoreCase(role) ? "individual" : "dealer");
				// Optionally set image and carIds as needed
				Seller created = sellerService.registerSeller(seller);
				session.setAttribute(USER_ID_SESSION_KEY, created.getId());
				session.setAttribute(AUTH_ROLE_SESSION_KEY, role);
				redirectPage = "renter-dashboard.html";
				return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
						"message", "Account created successfully.",
						"role", role,
						"user", Map.of(
							"id", created.getId(),
							"name", created.getName(),
							"email", created.getEmail(),
							"phone", created.getContact(),
							"address", created.getLocation(),
							"role", role
						),
						"redirectPage", redirectPage));
			} else {
				// Save buyer in users.txt
				User user = userService.register(request.name(), request.email(), request.phone(), request.address(),
						request.password(), request.role());
				session.setAttribute(USER_ID_SESSION_KEY, user.getId());
				session.setAttribute(AUTH_ROLE_SESSION_KEY, role);
				return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
						"message", "Account created successfully.",
						"role", role,
						"user", toPublicUser(user),
						"redirectPage", redirectPage));
			}
		} catch (IllegalArgumentException exception) {
			return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
		}
	}

	@PostMapping("/auth/login")
	public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpSession session) {
		try {
			Admin admin = null;
			try {
				admin = adminService.authenticate(request.email(), request.password());
			} catch (IllegalArgumentException ignored) {
				// Try regular user authentication when admin auth fails.
			}

			if (admin != null) {
				String normalizedAdminRole = normalizeRole(admin.getRole());
				session.removeAttribute(USER_ID_SESSION_KEY);
				session.setAttribute(ADMIN_ID_SESSION_KEY, admin.getAdminId());
				session.setAttribute(AUTH_ROLE_SESSION_KEY, normalizedAdminRole);
				return ResponseEntity.ok(Map.of(
						"message", "Login successful.",
						"role", normalizedAdminRole,
						"admin", toAdminView(admin),
						"redirectPage", "admin-dashboard.html"));
			}

			// Try user login (buyers)
			try {
				User user = userService.login(request.email(), request.password());
				String role = normalizeBuyerSellerDealerRole(user.getRole());
				session.removeAttribute(ADMIN_ID_SESSION_KEY);
				session.setAttribute(USER_ID_SESSION_KEY, user.getId());
				session.setAttribute(AUTH_ROLE_SESSION_KEY, role);
				return ResponseEntity.ok(Map.of(
						"message", "Login successful.",
						"role", role,
						"user", toPublicUser(user),
						"redirectPage", "account.html"));
			} catch (IllegalArgumentException ignored) {
				// Try seller login if user login fails
			}

			// Try seller login
			return sellerService.getSellerByEmail(request.email())
				.filter(seller -> seller.getPassword() != null && seller.getPassword().equals(request.password()))
				.filter(seller -> seller.isApproved())
				.map(seller -> {
					String role = "SELLER";
					session.removeAttribute(ADMIN_ID_SESSION_KEY);
					session.setAttribute(USER_ID_SESSION_KEY, seller.getId());
					session.setAttribute(AUTH_ROLE_SESSION_KEY, role);
					return ResponseEntity.ok(Map.of(
						"message", "Login successful.",
						"role", role,
						"seller", Map.of(
							"id", seller.getId(),
							"name", seller.getName(),
							"email", seller.getEmail(),
							"contact", seller.getContact(),
							"location", seller.getLocation(),
							"type", seller.getType()
						),
						"redirectPage", "renter-dashboard.html"
					));
				})
				.orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(Map.of("message", "Invalid email or password, or seller not approved.")));
		} catch (Exception exception) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", exception.getMessage()));
		}
	}

	@PostMapping("/auth/logout")
	public ResponseEntity<?> logout(HttpSession session) {
		session.invalidate();
		return ResponseEntity.ok(Map.of("message", "Logged out successfully."));
	}

	@GetMapping("/auth/status")
	public ResponseEntity<?> authStatus(HttpSession session) {
		String role = currentRole(session);
		if (isAdminRole(role)) {
			String adminId = currentAdminId(session);
			if (adminId == null) {
				return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
						.body(Map.of("authenticated", false, "message", "Not authenticated."));
			}

			return adminService.searchByAdminId(adminId)
					.map(admin -> ResponseEntity.ok(Map.of(
							"authenticated", true,
							"role", "ADMIN",
							"admin", toAdminView(admin),
							"redirectPage", "admin-dashboard.html")))
					.orElseGet(() -> {
						session.invalidate();
						return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
								.body(Map.of("authenticated", false, "message", "Session expired."));
					});
		}

		Long userId = currentUserId(session);
		if (userId == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(Map.of("authenticated", false, "message", "Not authenticated."));
		}

		return userService.findById(userId)
				.map(user -> ResponseEntity.ok(Map.of(
						"authenticated", true,
						"role", normalizeBuyerSellerDealerRole(user.getRole()),
						"user", toPublicUser(user),
						"redirectPage", "account.html")))
				.orElseGet(() -> {
					session.invalidate();
					return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
							.body(Map.of("authenticated", false, "message", "Session expired."));
				});
	}

	@GetMapping("/account/me")
	public ResponseEntity<?> getMyAccount(HttpSession session) {
		if (!isBuyerSellerDealerRole(currentRole(session))) {
			return unauthorized();
		}

		Long userId = currentUserId(session);
		if (userId == null) {
			return unauthorized();
		}

		User user = userService.findById(userId).orElse(null);
		if (user == null) {
			session.removeAttribute(USER_ID_SESSION_KEY);
			return unauthorized();
		}

		return ResponseEntity.ok(Map.of("user", toPublicUser(user)));
	}

	@PutMapping("/account/me")
	public ResponseEntity<?> updateMyAccount(@RequestBody UpdateRequest request, HttpSession session) {
		if (!isBuyerSellerDealerRole(currentRole(session))) {
			return unauthorized();
		}

		Long userId = currentUserId(session);
		if (userId == null) {
			return unauthorized();
		}

		try {
			User user = userService.updateProfile(userId, request.name(), request.email(), request.phone(),
					request.address(), request.password());
			return ResponseEntity.ok(Map.of("message", "Account updated successfully.", "user", toPublicUser(user)));
		} catch (IllegalArgumentException exception) {
			return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
		}
	}

	@DeleteMapping("/account/me")
	public ResponseEntity<?> deleteMyAccount(HttpSession session) {
		if (!isBuyerSellerDealerRole(currentRole(session))) {
			return unauthorized();
		}

		Long userId = currentUserId(session);
		if (userId == null) {
			return unauthorized();
		}

		try {
			userService.deleteAccount(userId);
			session.invalidate();
			return ResponseEntity.ok(Map.of("message", "Account deleted successfully."));
		} catch (IllegalArgumentException exception) {
			return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
		}
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

	private String currentAdminId(HttpSession session) {
		Object raw = session.getAttribute(ADMIN_ID_SESSION_KEY);
		if (raw instanceof String id) {
			return id;
		}
		return null;
	}

	private String currentRole(HttpSession session) {
		Object raw = session.getAttribute(AUTH_ROLE_SESSION_KEY);
		if (raw instanceof String role) {
			return role;
		}
		return "";
	}

	private boolean isAdminRole(String role) {
		return "ADMIN".equalsIgnoreCase(role) || "SUPERADMIN".equalsIgnoreCase(role);
	}

	private boolean isBuyerSellerDealerRole(String role) {
		return "BUYER".equalsIgnoreCase(role)
				|| "SELLER".equalsIgnoreCase(role)
				|| "DEALER".equalsIgnoreCase(role)
				// Legacy demo roles (treated as buyer/seller depending on normalization).
				|| "RENTER".equalsIgnoreCase(role)
				|| "USER".equalsIgnoreCase(role);
	}

	private String normalizeBuyerSellerDealerRole(String role) {
		if (role == null) {
			return "BUYER";
		}
		String cleaned = role.trim().toUpperCase();
		if (cleaned.isBlank()) {
			return "BUYER";
		}

		// Legacy mapping: old demo "RENTER" users are treated as SELLERS (they can list cars),
		// while old demo "USER" users are treated as BUYERS.
		if ("RENTER".equals(cleaned)) {
			return "SELLER";
		}
		if ("USER".equals(cleaned)) {
			return "BUYER";
		}

		// Expected new roles.
		if ("BUYER".equals(cleaned) || "SELLER".equals(cleaned) || "DEALER".equals(cleaned)) {
			return cleaned;
		}

		return "BUYER";
	}

	private String normalizeRole(String role) {
		if (role == null) {
			return "ADMIN";
		}
		return role.trim().toUpperCase();
	}

	private ResponseEntity<?> unauthorized() {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Please login first."));
	}

	private Map<String, Object> toPublicUser(User user) {
		return Map.of(
				"id", user.getId(),
				"name", user.getName(),
				"email", user.getEmail(),
				"phone", user.getPhone(),
				"address", user.getAddress(),
				"role", normalizeBuyerSellerDealerRole(user.getRole()),
				"createdAt", user.getCreatedAt() == null ? "" : user.getCreatedAt().toString(),
				"updatedAt", user.getUpdatedAt() == null ? "" : user.getUpdatedAt().toString());
	}

	private Map<String, Object> toAdminView(Admin admin) {
		return Map.of(
				"adminId", admin.getAdminId(),
				"name", admin.getName(),
				"email", admin.getEmail(),
				"role", admin.getRole());
	}

	public record RegisterRequest(String name, String email, String phone, String address, String password,
			String role) {
	}

	public record LoginRequest(String email, String password) {
	}

	public record UpdateRequest(String name, String email, String phone, String address, String password) {
	}
}
