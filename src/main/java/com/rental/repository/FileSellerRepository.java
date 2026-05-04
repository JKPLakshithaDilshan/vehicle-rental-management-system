package com.rental.repository;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.rental.model.Seller;
import com.rental.util.FileHandler;

@Repository
public class FileSellerRepository implements SellerRepository {

	private static final String DELIMITER = "|";

	private final Path sellersFilePath;
	private final FileHandler fileHandler;

	public FileSellerRepository(
			@Value("${app.data.sellers-file:src/main/resources/data/sellers.txt}") String sellersFile,
			FileHandler fileHandler) {
		this.sellersFilePath = Path.of(sellersFile);
		this.fileHandler = fileHandler;
		this.fileHandler.ensureFileExists(this.sellersFilePath);
	}

	@Override
	public synchronized Seller save(Seller seller) {
		List<Seller> all = findAll();
		if (seller.getId() == null) {
			seller.setId(nextId(all));
			all.add(seller);
		} else {
			int index = -1;
			for (int i = 0; i < all.size(); i++) {
				if (all.get(i).getId().equals(seller.getId())) {
					index = i;
					break;
				}
			}
			if (index >= 0) {
				all.set(index, seller);
			} else {
				all.add(seller);
			}
		}
		writeAll(all);
		return seller;
	}

	@Override
	public synchronized Optional<Seller> findById(Long id) {
		if (id == null) {
			return Optional.empty();
		}
		return findAll().stream().filter(s -> id.equals(s.getId())).findFirst();
	}

	@Override
	public synchronized Optional<Seller> findByEmail(String email) {
		String needle = (email == null ? "" : email.trim().toLowerCase());
		if (needle.isBlank()) {
			return Optional.empty();
		}
		return findAll().stream()
				.filter(s -> s.getEmail() != null && s.getEmail().trim().toLowerCase().equals(needle))
				.findFirst();
	}

	@Override
	public synchronized List<Seller> findAll() {
		List<Seller> sellers = new ArrayList<>();
		for (String line : fileHandler.readLines(sellersFilePath)) {
			if (line == null || line.isBlank()) {
				continue;
			}
			sellers.add(parseLine(line));
		}
		return sellers;
	}

	@Override
	public synchronized List<Seller> findByName(String name) {
		String q = (name == null ? "" : name.trim().toLowerCase());
		if (q.isBlank()) {
			return findAll();
		}
		return findAll().stream()
				.filter(s -> s.getName() != null && s.getName().toLowerCase().contains(q))
				.toList();
	}

	@Override
	public synchronized List<Seller> findByLocation(String location) {
		String q = (location == null ? "" : location.trim().toLowerCase());
		if (q.isBlank()) {
			return findAll();
		}
		return findAll().stream()
				.filter(s -> s.getLocation() != null && s.getLocation().toLowerCase().contains(q))
				.toList();
	}

	@Override
	public synchronized void deleteById(Long id) {
		if (id == null) {
			return;
		}
		List<Seller> all = findAll();
		boolean removed = all.removeIf(s -> id.equals(s.getId()));
		if (removed) {
			writeAll(all);
		}
	}

	private void writeAll(List<Seller> sellers) {
		fileHandler.writeLines(sellersFilePath, sellers.stream().map(this::toLine).toList());
	}

	private Long nextId(List<Seller> sellers) {
		return sellers.stream().map(Seller::getId).max(Comparator.naturalOrder()).orElse(0L) + 1;
	}

	// sellers.txt (10 fields)
	// id|name|contact|email|password|location|type|image|carIdsCsv|isApproved
	private String toLine(Seller seller) {
		String carIds = seller.getCarIds() == null || seller.getCarIds().isEmpty()
				? ""
				: seller.getCarIds().stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");

		return seller.getId() + DELIMITER
				+ escape(seller.getName()) + DELIMITER
				+ escape(seller.getContact()) + DELIMITER
				+ escape(seller.getEmail()) + DELIMITER
				+ escape(seller.getPassword()) + DELIMITER
				+ escape(seller.getLocation()) + DELIMITER
				+ escape(seller.getType()) + DELIMITER
				+ escape(seller.getImage()) + DELIMITER
				+ escape(carIds) + DELIMITER
				+ seller.isApproved();
	}

	private Seller parseLine(String line) {
		String[] parts = line.split("\\|", -1);
		if (parts.length != 10) {
			throw new IllegalStateException("Corrupted seller row: " + line);
		}

		Seller s = new Seller();
		s.setId(Long.parseLong(parts[0]));
		s.setName(unescape(parts[1]));
		s.setContact(unescape(parts[2]));
		s.setEmail(unescape(parts[3]));
		s.setPassword(unescape(parts[4]));
		s.setLocation(unescape(parts[5]));
		s.setType(unescape(parts[6]));
		s.setImage(unescape(parts[7]));

		String carIdsCsv = unescape(parts[8]);
		if (carIdsCsv == null || carIdsCsv.isBlank()) {
			s.setCarIds(List.of());
		} else {
			List<Long> ids = new ArrayList<>();
			for (String token : carIdsCsv.split(",")) {
				String t = token.trim();
				if (!t.isBlank()) {
					try {
						ids.add(Long.parseLong(t));
					} catch (Exception e) {
						// skip
					}
				}
			}
			s.setCarIds(ids);
		}

		s.setApproved(Boolean.parseBoolean(parts[9]));
		return s;
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

