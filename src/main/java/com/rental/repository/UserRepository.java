package com.rental.repository;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.rental.model.User;
import com.rental.util.FileHandler;

@Repository
public class UserRepository {

	private static final String DELIMITER = "|";
	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
	private static final Pattern BASE64_URL_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+={0,2}$");

	private final Path usersFilePath;
	private final FileHandler fileHandler;

	public UserRepository(@Value("${app.data.users-file:src/main/resources/data/users.txt}") String usersFile,
			FileHandler fileHandler) {
		this.usersFilePath = Path.of(usersFile);
		this.fileHandler = fileHandler;
		this.fileHandler.ensureFileExists(this.usersFilePath);
		migrateLegacyEncodedData();
	}

	public synchronized List<User> findAll() {
		return readUsers();
	}

	public synchronized Optional<User> findById(Long id) {
		return readUsers().stream().filter(user -> user.getId().equals(id)).findFirst();
	}

	public synchronized Optional<User> findByEmail(String email) {
		String normalized = email == null ? "" : email.trim().toLowerCase();
		return readUsers().stream()
				.filter(user -> user.getEmail() != null && user.getEmail().equalsIgnoreCase(normalized))
				.findFirst();
	}

	public synchronized User save(User user) {
		List<User> users = readUsers();
		if (user.getId() == null) {
			user.setId(nextId(users));
		}
		users.add(user);
		writeUsers(users);
		return user;
	}

	public synchronized User update(User user) {
		List<User> users = readUsers();
		int index = -1;
		for (int i = 0; i < users.size(); i++) {
			if (users.get(i).getId().equals(user.getId())) {
				index = i;
				break;
			}
		}

		if (index < 0) {
			throw new IllegalArgumentException("User not found");
		}

		users.set(index, user);
		writeUsers(users);
		return user;
	}

	public synchronized boolean deleteById(Long id) {
		List<User> users = readUsers();
		boolean removed = users.removeIf(user -> user.getId().equals(id));
		if (removed) {
			writeUsers(users);
		}
		return removed;
	}

	private List<User> readUsers() {
		List<User> users = new ArrayList<>();
		for (String line : fileHandler.readLines(usersFilePath)) {
			if (line == null || line.isBlank()) {
				continue;
			}
			users.add(parseLine(line));
		}
		return users;
	}

	private void writeUsers(List<User> users) {
		List<String> lines = users.stream().map(this::toLine).toList();
		fileHandler.writeLines(usersFilePath, lines);
	}

	private Long nextId(List<User> users) {
		return users.stream().map(User::getId).max(Comparator.naturalOrder()).orElse(0L) + 1;
	}

	private String toLine(User user) {
		String createdAt = user.getCreatedAt() == null ? "" : DATE_FORMATTER.format(user.getCreatedAt());
		String updatedAt = user.getUpdatedAt() == null ? "" : DATE_FORMATTER.format(user.getUpdatedAt());
		String role = user.getRole() == null || user.getRole().isBlank() ? "USER" : user.getRole();

		return user.getId() + DELIMITER
				+ escape(user.getName()) + DELIMITER
				+ escape(user.getEmail()) + DELIMITER
				+ escape(user.getPhone()) + DELIMITER
				+ escape(user.getAddress()) + DELIMITER
				+ escape(role) + DELIMITER
				+ escape(user.getPasswordHash()) + DELIMITER
				+ createdAt + DELIMITER
				+ updatedAt;
	}

	private User parseLine(String line) {
		String[] parts = line.split("\\|", -1);
		if (parts.length != 8 && parts.length != 9) {
			throw new IllegalStateException("Corrupted user row: " + line);
		}

		boolean legacyEncoded = isLegacyEncoded(parts, parts.length == 9 ? 6 : 5);
		boolean hasRoleColumn = parts.length == 9;
		String role = hasRoleColumn ? readField(parts[5], legacyEncoded) : "RENTER";
		String passwordValue = hasRoleColumn ? readField(parts[6], legacyEncoded) : readField(parts[5], legacyEncoded);
		String createdAtValue = hasRoleColumn ? parts[7] : parts[6];
		String updatedAtValue = hasRoleColumn ? parts[8] : parts[7];

		return new User(
				Long.parseLong(parts[0]),
				readField(parts[1], legacyEncoded),
				readField(parts[2], legacyEncoded),
				readField(parts[3], legacyEncoded),
				readField(parts[4], legacyEncoded),
				role == null || role.isBlank() ? "RENTER" : role,
				passwordValue,
				parseDate(createdAtValue),
				parseDate(updatedAtValue));
	}

	private LocalDateTime parseDate(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return LocalDateTime.parse(value, DATE_FORMATTER);
	}

	private String readField(String value, boolean legacyEncoded) {
		if (legacyEncoded) {
			return decodeLegacy(value);
		}
		return unescape(value);
	}

	private String decodeLegacy(String value) {
		if (value == null || value.isBlank()) {
			return "";
		}
		byte[] decoded = java.util.Base64.getUrlDecoder().decode(value);
		return new String(decoded, StandardCharsets.UTF_8);
	}

	private boolean isLegacyEncoded(String[] parts, int passwordIndex) {
		return looksLikeBase64(parts[1])
				&& looksLikeBase64(parts[2])
				&& looksLikeBase64(parts[3])
				&& looksLikeBase64(parts[4])
				&& looksLikeBase64(parts[passwordIndex]);
	}

	private boolean looksLikeBase64(String value) {
		if (value == null || value.isBlank()) {
			return false;
		}

		if (!BASE64_URL_PATTERN.matcher(value).matches()) {
			return false;
		}

		try {
			new String(java.util.Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
			return true;
		} catch (IllegalArgumentException exception) {
			return false;
		}
	}

	private String escape(String value) {
		String safe = value == null ? "" : value;
		return safe
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

	private void migrateLegacyEncodedData() {
		List<String> lines = fileHandler.readLines(usersFilePath);
		if (lines.isEmpty()) {
			return;
		}

		boolean hasLegacyEncodedRows = false;
		for (String line : lines) {
			if (line == null || line.isBlank()) {
				continue;
			}
			String[] parts = line.split("\\|", -1);
			if ((parts.length == 8 && isLegacyEncoded(parts, 5)) || (parts.length == 9 && isLegacyEncoded(parts, 6))) {
				hasLegacyEncodedRows = true;
				break;
			}
		}

		if (!hasLegacyEncodedRows) {
			return;
		}

		List<User> users = readUsers();
		writeUsers(users);
	}
}
