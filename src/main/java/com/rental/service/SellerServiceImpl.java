package com.rental.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.rental.model.Seller;
import com.rental.repository.SellerRepository;

@Service
public class SellerServiceImpl implements SellerService {

	private final SellerRepository sellerRepository;

	public SellerServiceImpl(SellerRepository sellerRepository) {
		this.sellerRepository = sellerRepository;
	}

	@Override
	public Seller registerSeller(Seller seller) {
		if (seller == null) {
			throw new IllegalArgumentException("Seller is required.");
		}
		String email = clean(seller.getEmail()).toLowerCase();
		if (email.isBlank()) {
			throw new IllegalArgumentException("Email is required.");
		}
		if (sellerRepository.findByEmail(email).isPresent()) {
			throw new IllegalArgumentException("Seller with this email already exists.");
		}

		seller.setEmail(email);
		seller.setName(clean(seller.getName()));
		seller.setContact(clean(seller.getContact()));
		seller.setLocation(clean(seller.getLocation()));
		seller.setType(normalizeType(seller.getType()));
		seller.setImage(clean(seller.getImage()));
		if (seller.getCarIds() == null) {
			seller.setCarIds(List.of());
		}
		seller.setApproved(true);

		return sellerRepository.save(seller);
	}

	@Override
	public Optional<Seller> getSellerById(Long id) {
		return sellerRepository.findById(id);
	}

	@Override
	public Optional<Seller> getSellerByEmail(String email) {
		return sellerRepository.findByEmail(email);
	}

	@Override
	public List<Seller> searchSellers(String name, String location) {
		List<Seller> base = sellerRepository.findAll();

		String n = clean(name).toLowerCase();
		String l = clean(location).toLowerCase();

		return base.stream()
				.filter(s -> n.isBlank() || (s.getName() != null && s.getName().toLowerCase().contains(n)))
				.filter(s -> l.isBlank() || (s.getLocation() != null && s.getLocation().toLowerCase().contains(l)))
				.toList();
	}

	@Override
	public Seller updateSeller(Seller seller) {
		if (seller == null || seller.getId() == null) {
			throw new IllegalArgumentException("Seller id is required.");
		}

		Seller existing = sellerRepository.findById(seller.getId())
				.orElseThrow(() -> new IllegalArgumentException("Seller not found."));

		// Keep approval flag unless explicitly set.
		boolean approved = existing.isApproved();

		existing.setName(clean(seller.getName()));
		existing.setContact(clean(seller.getContact()));
		existing.setLocation(clean(seller.getLocation()));
		existing.setType(normalizeType(seller.getType()));
		existing.setImage(clean(seller.getImage()));
		if (seller.getCarIds() != null) {
			existing.setCarIds(seller.getCarIds());
		}
		existing.setApproved(approved);

		return sellerRepository.save(existing);
	}

	@Override
	public void deleteSeller(Long id) {
		sellerRepository.deleteById(id);
	}

	@Override
	public Seller approveSeller(Long id) {
		Seller existing = sellerRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Seller not found."));
		existing.setApproved(true);
		return sellerRepository.save(existing);
	}

	@Override
	public Seller rejectSeller(Long id) {
		Seller existing = sellerRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Seller not found."));
		existing.setApproved(false);
		return sellerRepository.save(existing);
	}

	@Override
	public List<Seller> getPendingSellers() {
		return sellerRepository.findAll().stream()
				.filter(s -> !s.isApproved())
				.toList();
	}

	private String clean(String value) {
		if (value == null) {
			return "";
		}
		return value.trim().replaceAll("\\s+", " ");
	}

	private String normalizeType(String raw) {
		String t = clean(raw).toLowerCase();
		if ("dealer".equals(t)) {
			return "dealer";
		}
		return "individual";
	}
}

