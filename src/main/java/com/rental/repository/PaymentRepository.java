package com.rental.repository;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.rental.model.Payment;
import com.rental.util.FileHandler;

@Repository
public class PaymentRepository {

	private static final String DELIMITER = "|";
	private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

	private final Path paymentsFilePath;
	private final FileHandler fileHandler;

	public PaymentRepository(
			@Value("${app.data.payments-file:src/main/resources/data/payments.txt}") String paymentsFile,
			FileHandler fileHandler) {
		this.paymentsFilePath = Path.of(paymentsFile);
		this.fileHandler = fileHandler;
		this.fileHandler.ensureFileExists(this.paymentsFilePath);
	}

	public synchronized List<Payment> findAll() {
		List<Payment> payments = new ArrayList<>();
		for (String line : fileHandler.readLines(paymentsFilePath)) {
			if (line == null || line.isBlank()) {
				continue;
			}
			payments.add(parseLine(line));
		}
		return payments;
	}

	public synchronized Optional<Payment> findById(Long id) {
		return findAll().stream().filter(payment -> payment.getId().equals(id)).findFirst();
	}

	public synchronized Payment save(Payment payment) {
		List<Payment> payments = findAll();
		if (payment.getId() == null) {
			payment.setId(nextId(payments));
		}
		payments.add(payment);
		writeAll(payments);
		return payment;
	}

	public synchronized Payment update(Payment payment) {
		List<Payment> payments = findAll();
		int index = -1;
		for (int i = 0; i < payments.size(); i++) {
			if (payments.get(i).getId().equals(payment.getId())) {
				index = i;
				break;
			}
		}

		if (index < 0) {
			throw new IllegalArgumentException("Payment record not found.");
		}

		payments.set(index, payment);
		writeAll(payments);
		return payment;
	}

	public synchronized boolean deleteById(Long id) {
		List<Payment> payments = findAll();
		boolean removed = payments.removeIf(payment -> payment.getId().equals(id));
		if (removed) {
			writeAll(payments);
		}
		return removed;
	}

	private void writeAll(List<Payment> payments) {
		List<String> rows = payments.stream().map(this::toLine).toList();
		fileHandler.writeLines(paymentsFilePath, rows);
	}

	private Long nextId(List<Payment> payments) {
		return payments.stream().map(Payment::getId).max(Comparator.naturalOrder()).orElse(0L) + 1;
	}

	private String toLine(Payment payment) {
		String createdAt = payment.getCreatedAt() == null ? "" : DATETIME_FORMATTER.format(payment.getCreatedAt());
		String updatedAt = payment.getUpdatedAt() == null ? "" : DATETIME_FORMATTER.format(payment.getUpdatedAt());

		return payment.getId() + DELIMITER
				+ payment.getCustomerId() + DELIMITER
				+ payment.getBookingId() + DELIMITER
				+ escape(payment.getType()) + DELIMITER
				+ escape(payment.getCardHolderName()) + DELIMITER
				+ escape(payment.getCardNumber()) + DELIMITER
				+ escape(payment.getExpiryMonth()) + DELIMITER
				+ escape(payment.getExpiryYear()) + DELIMITER
				+ escape(payment.getCvv()) + DELIMITER
				+ payment.getAmount() + DELIMITER
				+ escape(payment.getStatus()) + DELIMITER
				+ createdAt + DELIMITER
				+ updatedAt;
	}

	private Payment parseLine(String line) {
		String[] parts = line.split("\\|", -1);
		if (parts.length != 13) {
			throw new IllegalStateException("Corrupted payment row: " + line);
		}

		return new Payment(
				Long.parseLong(parts[0]),
				Long.parseLong(parts[1]),
				Long.parseLong(parts[2]),
				unescape(parts[3]),
				unescape(parts[4]),
				unescape(parts[5]),
				unescape(parts[6]),
				unescape(parts[7]),
				unescape(parts[8]),
				Double.parseDouble(parts[9]),
				unescape(parts[10]),
				parseDateTime(parts[11]),
				parseDateTime(parts[12]));
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
