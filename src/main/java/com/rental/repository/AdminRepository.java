package com.rental.repository;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.rental.model.Admin;
import com.rental.util.FileHandler;

@Repository
public class AdminRepository {

	private final Path adminsFilePath;
	private final FileHandler fileHandler;

	public AdminRepository(@Value("${app.data.admins-file:src/main/resources/data/admins.txt}") String adminsFile,
			FileHandler fileHandler) {
		this.adminsFilePath = Path.of(adminsFile);
		this.fileHandler = fileHandler;
		this.fileHandler.ensureFileExists(this.adminsFilePath);
	}

	public synchronized List<Admin> findAll() {
		List<Admin> admins = new ArrayList<>();
		for (String line : fileHandler.readLines(adminsFilePath)) {
			if (line == null || line.isBlank()) {
				continue;
			}
			admins.add(parseLine(line));
		}
		return admins;
	}

	public synchronized Optional<Admin> findById(String adminId) {
		return findAll().stream()
				.filter(admin -> admin.getAdminId().equalsIgnoreCase(adminId))
				.findFirst();
	}

	public synchronized Optional<Admin> findByEmail(String email) {
		String normalizedEmail = normalize(email);
		return findAll().stream()
				.filter(admin -> normalize(admin.getEmail()).equals(normalizedEmail))
				.findFirst();
	}

	public synchronized Admin save(Admin admin) {
		List<Admin> admins = findAll();
		if (admin.getAdminId() == null || admin.getAdminId().isBlank()) {
			admin.setAdminId(nextAdminId(admins));
		}
		admins.add(admin);
		writeAll(admins);
		return admin;
	}

	public synchronized Admin update(Admin admin) {
		List<Admin> admins = findAll();
		int existingIndex = -1;
		for (int i = 0; i < admins.size(); i++) {
			if (admins.get(i).getAdminId().equalsIgnoreCase(admin.getAdminId())) {
				existingIndex = i;
				break;
			}
		}

		if (existingIndex < 0) {
			throw new IllegalArgumentException("Admin not found.");
		}

		admins.set(existingIndex, admin);
		writeAll(admins);
		return admin;
	}

	public synchronized boolean deleteById(String adminId) {
		List<Admin> admins = findAll();
		boolean removed = admins.removeIf(admin -> admin.getAdminId().equalsIgnoreCase(adminId));
		if (removed) {
			writeAll(admins);
		}
		return removed;
	}

	public synchronized long count() {
		return findAll().size();
	}

	private void writeAll(List<Admin> admins) {
		List<String> rows = admins.stream().map(this::toLine).toList();
		fileHandler.writeLines(adminsFilePath, rows);
	}

	private String toLine(Admin admin) {
		return escape(admin.getAdminId()) + ","
				+ escape(admin.getName()) + ","
				+ escape(admin.getEmail()) + ","
				+ escape(admin.getPassword()) + ","
				+ escape(admin.getRole());
	}

	private Admin parseLine(String line) {
		String[] parts = line.split(",", -1);
		if (parts.length != 5) {
			throw new IllegalStateException("Corrupted admin row: " + line);
		}
		return new Admin(
				unescape(parts[0]),
				unescape(parts[1]),
				unescape(parts[2]),
				unescape(parts[3]),
				unescape(parts[4]));
	}

	private String nextAdminId(List<Admin> admins) {
		int max = 0;
		for (Admin admin : admins) {
			String id = admin.getAdminId();
			if (id == null || id.length() < 2 || !id.toUpperCase(Locale.ROOT).startsWith("A")) {
				continue;
			}
			try {
				int value = Integer.parseInt(id.substring(1));
				if (value > max) {
					max = value;
				}
			} catch (NumberFormatException ignored) {
				// Ignore non-standard IDs.
			}
		}
		return String.format("A%03d", max + 1);
	}

	private String normalize(String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}

	private String escape(String value) {
		if (value == null) {
			return "";
		}
		return value
				.replace("\\", "\\\\")
				.replace(",", "\\,")
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
