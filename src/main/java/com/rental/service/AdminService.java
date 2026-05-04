package com.rental.service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.rental.model.Admin;
import com.rental.repository.AdminRepository;

@Service
public class AdminService {

	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
	private static final Pattern ROLE_PATTERN = Pattern.compile("^(SUPERADMIN|ADMIN)$");

	private final AdminRepository adminRepository;
	private final PasswordEncoder passwordEncoder;

	public AdminService(AdminRepository adminRepository) {
		this.adminRepository = adminRepository;
		this.passwordEncoder = new BCryptPasswordEncoder();
	}

	public Admin createAdmin(String name, String email, String password, String role) {
		String cleanedName = clean(name);
		String cleanedEmail = normalizeEmail(email);
		String cleanedRole = normalizeRole(role);
		validateAdminInput(cleanedName, cleanedEmail, password, cleanedRole, true);

		if (adminRepository.findByEmail(cleanedEmail).isPresent()) {
			throw new IllegalArgumentException("Admin email already exists.");
		}

		Admin admin = new Admin();
		admin.setName(cleanedName);
		admin.setEmail(cleanedEmail);
		admin.setPassword(passwordEncoder.encode(password));
		admin.setRole(cleanedRole);
		return adminRepository.save(admin);
	}

	public List<Admin> getAllAdmins() {
		return adminRepository.findAll();
	}

	public Optional<Admin> searchByAdminId(String adminId) {
		return adminRepository.findById(clean(adminId));
	}

	public Admin updateAdmin(String adminId, String name, String email, String password, String role) {
		Admin existing = adminRepository.findById(clean(adminId))
				.orElseThrow(() -> new IllegalArgumentException("Admin not found."));

		String cleanedName = clean(name);
		String cleanedEmail = normalizeEmail(email);
		String cleanedRole = normalizeRole(role);
		validateAdminInput(cleanedName, cleanedEmail, password, cleanedRole, false);

		Optional<Admin> byEmail = adminRepository.findByEmail(cleanedEmail);
		if (byEmail.isPresent() && !byEmail.get().getAdminId().equalsIgnoreCase(existing.getAdminId())) {
			throw new IllegalArgumentException("Another admin already uses this email.");
		}

		existing.setName(cleanedName);
		existing.setEmail(cleanedEmail);
		existing.setRole(cleanedRole);
		if (password != null && !password.isBlank()) {
			existing.setPassword(passwordEncoder.encode(password));
		}
		return adminRepository.update(existing);
	}

	public void deleteAdmin(String adminId) {
		if (!adminRepository.deleteById(clean(adminId))) {
			throw new IllegalArgumentException("Admin not found.");
		}
	}

	public Admin authenticate(String email, String password) {
		String cleanedEmail = normalizeEmail(email);
		if (cleanedEmail.isBlank() || password == null || password.isBlank()) {
			throw new IllegalArgumentException("Email and password are required.");
		}

		Admin admin = adminRepository.findByEmail(cleanedEmail)
				.orElseThrow(() -> new IllegalArgumentException("Invalid email or password."));

		String normalizedRole = normalizeRole(admin.getRole());
		boolean roleChanged = !normalizedRole.equals(admin.getRole());

		if (!matchesPassword(password, admin.getPassword())) {
			throw new IllegalArgumentException("Invalid email or password.");
		}

		if (admin.getPassword() != null && !admin.getPassword().startsWith("$2a$")
				&& !admin.getPassword().startsWith("$2b$")
				&& !admin.getPassword().startsWith("$2y$")) {
			admin.setPassword(passwordEncoder.encode(password));
			admin.setRole(normalizedRole);
			adminRepository.update(admin);
		} else if (roleChanged) {
			admin.setRole(normalizedRole);
			adminRepository.update(admin);
		} else {
			admin.setRole(normalizedRole);
		}

		return admin;
	}

	public long countAdmins() {
		return adminRepository.count();
	}

	private boolean matchesPassword(String raw, String stored) {
		if (stored == null || stored.isBlank()) {
			return false;
		}
		if (stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$")) {
			return passwordEncoder.matches(raw, stored);
		}
		return stored.equals(raw);
	}

	private void validateAdminInput(String name, String email, String password, String role, boolean requirePassword) {
		if (name.isBlank() || name.length() < 2 || name.length() > 80) {
			throw new IllegalArgumentException("Name must be between 2 and 80 characters.");
		}
		if (!EMAIL_PATTERN.matcher(email).matches()) {
			throw new IllegalArgumentException("Enter a valid admin email.");
		}
		if (!ROLE_PATTERN.matcher(role).matches()) {
			throw new IllegalArgumentException("Role must be ADMIN or SUPERADMIN.");
		}
		if (requirePassword || (password != null && !password.isBlank())) {
			if (password == null || password.length() < 8 || password.length() > 100) {
				throw new IllegalArgumentException("Password must be between 8 and 100 characters.");
			}
			if (!password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
				throw new IllegalArgumentException("Password must contain at least one letter and one number.");
			}
		}
	}

	private String clean(String value) {
		if (value == null) {
			return "";
		}
		return value.trim().replaceAll("\\s+", " ");
	}

	private String normalizeEmail(String email) {
		return clean(email).toLowerCase(Locale.ROOT);
	}

	private String normalizeRole(String role) {
		return clean(role).toUpperCase(Locale.ROOT);
	}
}
