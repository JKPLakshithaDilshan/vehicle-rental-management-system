package com.rental.service;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import org.springframework.stereotype.Service;

import com.rental.model.Booking;
import com.rental.model.Payment;
import com.rental.repository.PaymentRepository;
import com.rental.util.HashUtil;

@Service
public class PaymentService {

	private final PaymentRepository paymentRepository;
	private final BookingService bookingService;

	public PaymentService(PaymentRepository paymentRepository, BookingService bookingService) {
		this.paymentRepository = paymentRepository;
		this.bookingService = bookingService;
	}

	public Payment addCard(Long customerId, String cardHolderName, String cardNumber, String expiryMonth,
			String expiryYear, String cvv) {
		validateCard(cardHolderName, cardNumber, expiryMonth, expiryYear, cvv);

		Payment card = new Payment();
		card.setCustomerId(customerId);
		card.setBookingId(0L);
		card.setType("CARD");
		card.setCardHolderName(clean(cardHolderName));
		card.setCardNumber(HashUtil.sha256(clean(cardNumber)));
		card.setExpiryMonth(clean(expiryMonth));
		card.setExpiryYear(clean(expiryYear));
		card.setCvv(HashUtil.sha256(clean(cvv)));
		card.setAmount(0);
		card.setStatus("ACTIVE");
		card.setCreatedAt(LocalDateTime.now());
		card.setUpdatedAt(LocalDateTime.now());

		return paymentRepository.save(card);
	}

	public List<Payment> getCards(Long customerId) {
		return paymentRepository.findAll().stream()
				.filter(payment -> payment.getCustomerId().equals(customerId))
				.filter(payment -> "CARD".equalsIgnoreCase(payment.getType()))
				.toList();
	}

	public Payment updateCard(Long customerId, Long cardId, String cardHolderName, String cardNumber,
			String expiryMonth, String expiryYear, String cvv) {
		Payment card = paymentRepository.findById(cardId)
				.orElseThrow(() -> new IllegalArgumentException("Card not found."));
		if (!card.getCustomerId().equals(customerId) || !"CARD".equalsIgnoreCase(card.getType())) {
			throw new IllegalArgumentException("You can update only your own card.");
		}

		validateCard(cardHolderName, cardNumber, expiryMonth, expiryYear, cvv);
		card.setCardHolderName(clean(cardHolderName));
		card.setCardNumber(HashUtil.sha256(clean(cardNumber)));
		card.setExpiryMonth(clean(expiryMonth));
		card.setExpiryYear(clean(expiryYear));
		card.setCvv(HashUtil.sha256(clean(cvv)));
		card.setUpdatedAt(LocalDateTime.now());

		return paymentRepository.update(card);
	}

	public void deleteCard(Long customerId, Long cardId) {
		Payment card = paymentRepository.findById(cardId)
				.orElseThrow(() -> new IllegalArgumentException("Card not found."));
		if (!card.getCustomerId().equals(customerId) || !"CARD".equalsIgnoreCase(card.getType())) {
			throw new IllegalArgumentException("You can delete only your own card.");
		}
		paymentRepository.deleteById(cardId);
	}

	public Payment payForBooking(Long customerId, Long bookingId, Long cardId) {
		Booking booking = bookingService.findById(bookingId)
				.orElseThrow(() -> new IllegalArgumentException("Booking not found."));
		if (!booking.getCustomerId().equals(customerId)) {
			throw new IllegalArgumentException("You can pay only your own booking.");
		}

		Payment card = paymentRepository.findById(cardId)
				.orElseThrow(() -> new IllegalArgumentException("Card not found."));
		if (!card.getCustomerId().equals(customerId) || !"CARD".equalsIgnoreCase(card.getType())) {
			throw new IllegalArgumentException("Invalid payment card.");
		}

		Booking paidBooking = bookingService.markPaid(customerId, bookingId);

		Payment transaction = new Payment();
		transaction.setCustomerId(customerId);
		transaction.setBookingId(bookingId);
		transaction.setType("TRANSACTION");
		transaction.setCardHolderName(card.getCardHolderName());
		transaction.setCardNumber(maskCardNumber(card.getCardNumber()));
		transaction.setExpiryMonth(card.getExpiryMonth());
		transaction.setExpiryYear(card.getExpiryYear());
		transaction.setCvv("***");
		transaction.setAmount(paidBooking.getTotalAmount());
		transaction.setStatus("SUCCESS");
		transaction.setCreatedAt(LocalDateTime.now());
		transaction.setUpdatedAt(LocalDateTime.now());

		return paymentRepository.save(transaction);
	}

	public List<Payment> getTransactions(Long customerId) {
		return paymentRepository.findAll().stream()
				.filter(payment -> payment.getCustomerId().equals(customerId))
				.filter(payment -> "TRANSACTION".equalsIgnoreCase(payment.getType()))
				.toList();
	}

	private String maskCardNumber(String raw) {
		String digits = clean(raw).replaceAll("\\D", "");
		if (digits.length() < 4) {
			return "****";
		}
		return "**** **** **** " + digits.substring(digits.length() - 4);
	}

	private void validateCard(String cardHolderName, String cardNumber, String expiryMonth, String expiryYear,
			String cvv) {
		String holder = clean(cardHolderName);
		String number = clean(cardNumber).replaceAll("\\s+", "");
		String month = clean(expiryMonth);
		String year = clean(expiryYear);
		String securedCvv = clean(cvv);

		if (holder.isBlank() || holder.length() < 3 || holder.length() > 80) {
			throw new IllegalArgumentException("Card holder name must be between 3 and 80 characters.");
		}
		if (!number.matches("\\d{16}")) {
			throw new IllegalArgumentException("Card number must be 16 digits.");
		}
		if (!month.matches("0?[1-9]|1[0-2]")) {
			throw new IllegalArgumentException("Expiry month must be between 1 and 12.");
		}
		if (!year.matches("\\d{4}")) {
			throw new IllegalArgumentException("Expiry year must be 4 digits.");
		}

		// Check if card has expired
		try {
			int monthInt = Integer.parseInt(month);
			int yearInt = Integer.parseInt(year);
			YearMonth cardExpiry = YearMonth.of(yearInt, monthInt);
			YearMonth currentYearMonth = YearMonth.now();
			
			if (cardExpiry.isBefore(currentYearMonth)) {
				throw new IllegalArgumentException("Card has expired.");
			}
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Invalid expiry date format.");
		}

		if (!securedCvv.matches("\\d{3}")) {
			throw new IllegalArgumentException("CVV must be 3 digits.");
		}
	}

	private String clean(String value) {
		if (value == null) {
			return "";
		}
		return value.trim();
	}
}
