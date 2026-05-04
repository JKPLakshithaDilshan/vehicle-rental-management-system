package com.rental.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.rental.model.Vehicle;
import com.rental.repository.VehicleRepository;

@Service
public class VehicleService {

	private final VehicleRepository vehicleRepository;

	public VehicleService(VehicleRepository vehicleRepository) {
		this.vehicleRepository = vehicleRepository;
	}

	public Vehicle createVehicle(Long sellerId, String brand, String model, int year, double pricePerDay, List<String> images,
			String description) {
		validateVehicleInput(brand, model, year, pricePerDay, description);

		Vehicle vehicle = new Vehicle();
		vehicle.setSellerId(sellerId);
		vehicle.setBrand(clean(brand));
		vehicle.setModel(clean(model));
		vehicle.setYear(year);
		vehicle.setPrice(pricePerDay);
		vehicle.setImages(images);
		vehicle.setDescription(clean(description));
		// Vehicle must be approved by admin before it appears in public listings.
		vehicle.setStatus("PENDING");
		vehicle.setCreatedAt(LocalDateTime.now());
		vehicle.setUpdatedAt(LocalDateTime.now());

		return vehicleRepository.save(vehicle);
	}

	public List<Vehicle> getAvailableVehicles(String query) {
		List<Vehicle> available = vehicleRepository.findAll().stream()
				.filter(vehicle -> "APPROVED".equalsIgnoreCase(vehicle.getStatus()))
				.toList();

		if (query == null || query.isBlank()) {
			return available;
		}

		String normalizedQuery = clean(query).toLowerCase(Locale.ROOT);
		return available.stream()
				.filter(vehicle -> contains(vehicle.getTitle(), normalizedQuery)
						|| contains(vehicle.getDescription(), normalizedQuery)
						|| String.valueOf(vehicle.getYear()).contains(normalizedQuery))
				.toList();
	}

	public List<Vehicle> getVehiclesBySeller(Long sellerId) {
		return vehicleRepository.findAll().stream()
				.filter(vehicle -> vehicle.getSellerId().equals(sellerId))
				.toList();
	}

	public Vehicle markVehicleSold(Long vehicleId) {
		Optional<Vehicle> opt = vehicleRepository.findById(vehicleId);
		if (opt.isPresent()) {
			Vehicle vehicle = opt.get();
			vehicle.setStatus("SOLD");
			vehicle.setUpdatedAt(LocalDateTime.now());
			return vehicleRepository.save(vehicle);
		}
		throw new IllegalArgumentException("Vehicle not found");
	}

	public void deleteVehicleBySeller(Long sellerId, Long vehicleId) {
		Vehicle existing = findVehicle(vehicleId)
				.orElseThrow(() -> new IllegalArgumentException("Vehicle not found."));

		if (!existing.getSellerId().equals(sellerId)) {
			throw new IllegalArgumentException("You can delete only your own vehicles.");
		}

		vehicleRepository.deleteById(vehicleId);
	}

	public void deleteVehicleByAdmin(Long vehicleId) {
		if (!vehicleRepository.deleteById(vehicleId)) {
			throw new IllegalArgumentException("Vehicle not found.");
		}
	}

	public List<Vehicle> getPendingVehiclesForApproval() {
		return vehicleRepository.findAll().stream()
				.filter(vehicle -> "PENDING".equalsIgnoreCase(vehicle.getStatus()))
				.toList();
	}

	public Vehicle approveVehicle(Long vehicleId) {
		Vehicle vehicle = findVehicle(vehicleId).orElseThrow(() -> new IllegalArgumentException("Vehicle not found."));
		vehicle.setStatus("APPROVED");
		vehicle.setUpdatedAt(LocalDateTime.now());
		return vehicleRepository.update(vehicle);
	}

	public Vehicle rejectVehicle(Long vehicleId) {
		Vehicle vehicle = findVehicle(vehicleId).orElseThrow(() -> new IllegalArgumentException("Vehicle not found."));
		vehicle.setStatus("REJECTED");
		vehicle.setUpdatedAt(LocalDateTime.now());
		return vehicleRepository.update(vehicle);
	}

	public Vehicle updateVehicleBySeller(Long sellerId, Long vehicleId, String brand, String model, int year,
			double pricePerDay, List<String> images, String description) {
		Vehicle existing = findVehicle(vehicleId)
				.orElseThrow(() -> new IllegalArgumentException("Vehicle not found."));

		if (!existing.getSellerId().equals(sellerId)) {
			throw new IllegalArgumentException("You can update only your own vehicle listings.");
		}

		validateVehicleInput(brand, model, year, pricePerDay, description);

		existing.setBrand(clean(brand));
		existing.setModel(clean(model));
		existing.setYear(year);
		existing.setPrice(pricePerDay);
		existing.setImages(images);
		existing.setDescription(clean(description));

		// Re-submit for admin review after an update.
		existing.setStatus("PENDING");
		existing.setUpdatedAt(LocalDateTime.now());

		return vehicleRepository.update(existing);
	}

	public Optional<Vehicle> findVehicle(Long vehicleId) {
		return vehicleRepository.findById(vehicleId);
	}

	private void validateVehicleInput(String brand, String model, int year, double pricePerDay, String description) {
		String cleanedBrand = clean(brand);
		String cleanedModel = clean(model);
		String cleanedDescription = clean(description);
		int currentYear = LocalDateTime.now().getYear();

		if (cleanedBrand.isBlank() || cleanedBrand.length() < 2 || cleanedBrand.length() > 60) {
			throw new IllegalArgumentException("Brand must be between 2 and 60 characters.");
		}
		if (cleanedModel.isBlank() || cleanedModel.length() < 1 || cleanedModel.length() > 60) {
			throw new IllegalArgumentException("Model must be between 1 and 60 characters.");
		}
		if (cleanedDescription.isBlank() || cleanedDescription.length() < 10 || cleanedDescription.length() > 500) {
			throw new IllegalArgumentException("Description must be between 10 and 500 characters.");
		}
		if (year < 1990 || year > currentYear) {
			throw new IllegalArgumentException("Vehicle year cannot be in the future.");
		}
		if (pricePerDay <= 0 || pricePerDay > 100000) {
			throw new IllegalArgumentException("Price must be greater than 0 and less than 100000.");
		}
	}

	private String clean(String value) {
		if (value == null) {
			return "";
		}
		return value.trim().replaceAll("\\s+", " ");
	}

	private boolean contains(String value, String query) {
		if (value == null) {
			return false;
		}
		return value.toLowerCase(Locale.ROOT).contains(query);
	}
}
