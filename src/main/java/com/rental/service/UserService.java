package com.rental.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.rental.model.User;
import com.rental.repository.UserRepository;

@Service
public class UserService {

	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
	private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9]{10}$");

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public UserService(UserRepository userRepository) {
		this.userRepository = userRepository;
		this.passwordEncoder = new BCryptPasswordEncoder();
	}

	public User register(String name, String email, String phone, String address, String password, String role) {
		String normalizedEmail = normalizeEmail(email);
		String normalizedRole = normalizeRole(role);
		validateUserInput(name, normalizedEmail, phone, address, password, true);

		if (userRepository.findByEmail(normalizedEmail).isPresent()) {
			throw new IllegalArgumentException("Email is already registered.");
		}

		LocalDateTime now = LocalDateTime.now();
		User user = new User();
		user.setName(cleanText(name));
		user.setEmail(normalizedEmail);
		user.setPhone(cleanText(phone));
		user.setAddress(cleanText(address));
		user.setRole(normalizedRole);
		user.setPasswordHash(passwordEncoder.encode(password));
		user.setCreatedAt(now);
		user.setUpdatedAt(now);

		return userRepository.save(user);
	}

	public User login(String email, String password) {
		String normalizedEmail = normalizeEmail(email);
		if (normalizedEmail.isBlank() || password == null || password.isBlank()) {
			throw new IllegalArgumentException("Email and password are required.");
		}

		User user = userRepository.findByEmail(normalizedEmail)
				.orElseThrow(() -> new IllegalArgumentException("Invalid email or password."));

		if (!passwordEncoder.matches(password, user.getPasswordHash())) {
			throw new IllegalArgumentException("Invalid email or password.");
		}

		return user;
	}

	public Optional<User> findById(Long userId) {
		return userRepository.findById(userId);
	}

	public List<User> searchUsers(String query) {
		List<User> users = userRepository.findAll();
		if (query == null || query.isBlank()) {
			return users;
		}

		String normalizedQuery = cleanText(query).toLowerCase();
		return users.stream()
				.filter(user -> contains(user.getName(), normalizedQuery)
						|| contains(user.getEmail(), normalizedQuery)
						|| contains(user.getPhone(), normalizedQuery)
						|| contains(user.getAddress(), normalizedQuery))
				.toList();
	}

	public void deleteUserByAdmin(Long userId) {
		if (!userRepository.deleteById(userId)) {
			throw new IllegalArgumentException("User not found.");
		}
	}

	public User updateProfile(Long userId, String name, String email, String phone, String address, String password) {
		User existing = userRepository.findById(userId)
				.orElseThrow(() -> new IllegalArgumentException("User not found."));

		String updatedName = cleanText(name);
		String updatedEmail = normalizeEmail(email);
		String updatedPhone = cleanText(phone);
		String updatedAddress = cleanText(address);

		validateUserInput(updatedName, updatedEmail, updatedPhone, updatedAddress, password, false);

		Optional<User> userByEmail = userRepository.findByEmail(updatedEmail);
		if (userByEmail.isPresent() && !userByEmail.get().getId().equals(userId)) {
			throw new IllegalArgumentException("Another account already uses this email.");
		}

		existing.setName(updatedName);
		existing.setEmail(updatedEmail);
		existing.setPhone(updatedPhone);
		existing.setAddress(updatedAddress);
		if (password != null && !password.isBlank()) {
			existing.setPasswordHash(passwordEncoder.encode(password));
		}
		existing.setUpdatedAt(LocalDateTime.now());

		return userRepository.update(existing);
	}

	public void deleteAccount(Long userId) {
		if (!userRepository.deleteById(userId)) {
			throw new IllegalArgumentException("User not found.");
		}
	}

	private void validateUserInput(String name, String email, String phone, String address, String password,
			boolean isRegistration) {
		if (name.isBlank() || name.length() < 2 || name.length() > 80) {
			throw new IllegalArgumentException("Name must be between 2 and 80 characters.");
		}

		if (!EMAIL_PATTERN.matcher(email).matches()) {
			throw new IllegalArgumentException("Enter a valid email address.");
		}

		if (!PHONE_PATTERN.matcher(phone).matches()) {
			throw new IllegalArgumentException("Phone number must contain exactly 10 digits.");
		}

		if (address.isBlank() || address.length() < 5 || address.length() > 250) {
			throw new IllegalArgumentException("Address must be between 5 and 250 characters.");
		}

		if (isRegistration || (password != null && !password.isBlank())) {
			if (password == null || password.length() < 8 || password.length() > 100) {
				throw new IllegalArgumentException("Password must be between 8 and 100 characters.");
			}
			if (!password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
				throw new IllegalArgumentException("Password must contain at least one letter and one number.");
			}
		}
	}

	private String normalizeEmail(String email) {
		return cleanText(email).toLowerCase();
	}

	private String normalizeRole(String role) {
		String cleaned = cleanText(role).toUpperCase();
		if (cleaned.isBlank()) {
			return "RENTER";
		}
		if ("USER".equals(cleaned)) {
			return "RENTER";
		}
		if (!"RENTER".equals(cleaned)) {
			return "RENTER";
		}
		return cleaned;
	}

	private String cleanText(String value) {
		if (value == null) {
			return "";
		}
		return value.trim().replaceAll("\\s+", " ");
	}

	private boolean contains(String source, String query) {
		if (source == null) {
			return false;
		}
		return source.toLowerCase().contains(query);
	}
}
