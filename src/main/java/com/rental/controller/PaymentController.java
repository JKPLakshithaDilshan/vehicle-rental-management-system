
package com.rental.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rental.model.Payment;
import com.rental.service.PaymentService;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

	private static final String USER_ID_SESSION_KEY = "AUTH_USER_ID";
	private static final String AUTH_ROLE_SESSION_KEY = "AUTH_ROLE";

	private final PaymentService paymentService;

	public PaymentController(PaymentService paymentService) {
		this.paymentService = paymentService;
	}

	@GetMapping("/cards")
	public ResponseEntity<?> getCards(HttpSession session) {
		Long userId = currentUserId(session);
		if (userId == null || !isBuyerSellerDealerRole(currentRole(session))) {
			return unauthorized();
		}

		List<Map<String, Object>> cards = paymentService.getCards(userId).stream().map(this::toPaymentView).toList();
		return ResponseEntity.ok(Map.of("cards", cards));
	}

	@PostMapping("/cards")
	public ResponseEntity<?> addCard(@RequestBody CardRequest request, HttpSession session) {
		Long userId = currentUserId(session);
		if (userId == null || !isBuyerSellerDealerRole(currentRole(session))) {
			return unauthorized();
		}

		try {
			Payment card = paymentService.addCard(userId, request.cardHolderName(), request.cardNumber(),
					request.expiryMonth(), request.expiryYear(), request.cvv());
			return ResponseEntity.status(HttpStatus.CREATED)
					.body(Map.of("message", "Card added.", "card", toPaymentView(card)));
		} catch (IllegalArgumentException exception) {
			return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
		}
	}

	@PutMapping("/cards/{cardId}")
	public ResponseEntity<?> updateCard(@PathVariable Long cardId, @RequestBody CardRequest request,
			HttpSession session) {
		Long userId = currentUserId(session);
		if (userId == null || !isBuyerSellerDealerRole(currentRole(session))) {
			return unauthorized();
		}

		try {
			Payment card = paymentService.updateCard(userId, cardId, request.cardHolderName(), request.cardNumber(),
					request.expiryMonth(), request.expiryYear(), request.cvv());
			return ResponseEntity.ok(Map.of("message", "Card updated.", "card", toPaymentView(card)));
		} catch (IllegalArgumentException exception) {
			return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
		}
	}

	@DeleteMapping("/cards/{cardId}")
	public ResponseEntity<?> deleteCard(@PathVariable Long cardId, HttpSession session) {
		Long userId = currentUserId(session);
		if (userId == null || !isBuyerSellerDealerRole(currentRole(session))) {
			return unauthorized();
		}

		try {
			paymentService.deleteCard(userId, cardId);
			return ResponseEntity.ok(Map.of("message", "Card deleted."));
		} catch (IllegalArgumentException exception) {
			return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
		}
	}

	@PostMapping("/bookings/{bookingId}/pay")
	public ResponseEntity<?> payBooking(@PathVariable Long bookingId, @RequestBody PayRequest request, HttpSession session) {
		Long userId = currentUserId(session);
		if (userId == null || !isBuyerSellerDealerRole(currentRole(session))) {
			return unauthorized();
		}

		try {
			Payment transaction = paymentService.payForBooking(userId, bookingId, request.cardId());
			return ResponseEntity.ok(Map.of("message", "Payment successful.", "payment", toPaymentView(transaction)));
		} catch (IllegalArgumentException exception) {
			return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
		}
	}

	@GetMapping("/transactions")
	public ResponseEntity<?> getTransactions(HttpSession session) {
		Long userId = currentUserId(session);
		if (userId == null || !isBuyerSellerDealerRole(currentRole(session))) {
			return unauthorized();
		}

		List<Map<String, Object>> transactions = paymentService.getTransactions(userId).stream()
				.map(this::toPaymentView)
				.toList();
		return ResponseEntity.ok(Map.of("transactions", transactions));
	}

	private Map<String, Object> toPaymentView(Payment payment) {
		return Map.ofEntries(
				Map.entry("id", payment.getId()),
				Map.entry("customerId", payment.getCustomerId()),
				Map.entry("bookingId", payment.getBookingId()),
				Map.entry("type", payment.getType()),
				Map.entry("cardHolderName", payment.getCardHolderName() == null ? "" : payment.getCardHolderName()),
				Map.entry("cardNumber", payment.getCardNumber() == null ? "" : payment.getCardNumber()),
				Map.entry("expiryMonth", payment.getExpiryMonth() == null ? "" : payment.getExpiryMonth()),
				Map.entry("expiryYear", payment.getExpiryYear() == null ? "" : payment.getExpiryYear()),
				Map.entry("amount", payment.getAmount()),
				Map.entry("status", payment.getStatus() == null ? "" : payment.getStatus()),
				Map.entry("createdAt", payment.getCreatedAt() == null ? "" : payment.getCreatedAt().toString()),
				Map.entry("updatedAt", payment.getUpdatedAt() == null ? "" : payment.getUpdatedAt().toString()));
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

	private String currentRole(HttpSession session) {
		Object raw = session.getAttribute(AUTH_ROLE_SESSION_KEY);
		if (raw instanceof String role) {
			return role;
		}
		return "";
	}

	private boolean isBuyerSellerDealerRole(String role) {
		return "BUYER".equalsIgnoreCase(role)
				|| "SELLER".equalsIgnoreCase(role)
				|| "DEALER".equalsIgnoreCase(role)
				// Legacy demo roles
				|| "RENTER".equalsIgnoreCase(role)
				|| "USER".equalsIgnoreCase(role);
	}

	private boolean isAdminRole(String role) {
		return "ADMIN".equalsIgnoreCase(role) || "SUPERADMIN".equalsIgnoreCase(role);
	}

	private ResponseEntity<?> unauthorized() {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Please login first."));
	}

	public record CardRequest(String cardHolderName, String cardNumber, String expiryMonth, String expiryYear,
			String cvv) {
	}

	public record PayRequest(Long cardId) {
	}
}
