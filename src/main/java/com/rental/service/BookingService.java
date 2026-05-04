package com.rental.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.rental.model.Booking;
import com.rental.model.Vehicle;
import com.rental.repository.BookingRepository;

@Service
public class BookingService {

	private final BookingRepository bookingRepository;
	private final VehicleService vehicleService;

	public BookingService(BookingRepository bookingRepository, VehicleService vehicleService) {
		this.bookingRepository = bookingRepository;
		this.vehicleService = vehicleService;
	}

	public Booking createBooking(Long customerId, Long vehicleId, double offerAmount, String offerMessage) {
		Vehicle vehicle = vehicleService.findVehicle(vehicleId)
				.orElseThrow(() -> new IllegalArgumentException("Vehicle not found."));

		if (!"APPROVED".equalsIgnoreCase(vehicle.getStatus())) {
			throw new IllegalArgumentException("Only approved vehicles can be booked.");
		}
		if (vehicle.getSellerId().equals(customerId)) {
			throw new IllegalArgumentException("You cannot book your own vehicle.");
		}

		ensureVehicleNotBooked(vehicleId, null);
		validateOffer(vehicle.getPrice(), offerAmount);

		Booking booking = new Booking();
		booking.setVehicleId(vehicleId);
		booking.setRenterId(vehicle.getSellerId());
		booking.setCustomerId(customerId);
		// Date range is no longer used for second-hand purchases; keep placeholders for legacy storage.
		LocalDate today = LocalDate.now();
		booking.setStartDate(today);
		booking.setEndDate(today);
		booking.setTotalDays(1);
		booking.setOfferAmount(offerAmount);
		booking.setOfferMessage(cleanOfferMessage(offerMessage));
		booking.setTotalAmount(offerAmount);
		booking.setStatus("PENDING");
		booking.setPaid(false);
		booking.setCreatedAt(LocalDateTime.now());
		booking.setUpdatedAt(LocalDateTime.now());

		return bookingRepository.save(booking);
	}

	public List<Booking> getBookingsByCustomer(Long customerId) {
		return bookingRepository.findAll().stream()
				.filter(booking -> booking.getCustomerId().equals(customerId))
				.toList();
	}

	public List<Booking> getBookingsForRenter(Long renterId) {
		return bookingRepository.findAll().stream()
				.filter(booking -> booking.getRenterId().equals(renterId))
				.toList();
	}

	public Booking updateBookingByCustomer(Long customerId, Long bookingId, double offerAmount, String offerMessage) {
		Booking booking = findById(bookingId).orElseThrow(() -> new IllegalArgumentException("Booking not found."));

		if (!booking.getCustomerId().equals(customerId)) {
			throw new IllegalArgumentException("You can edit only your own bookings.");
		}
		if ("RENTED".equalsIgnoreCase(booking.getStatus())) {
			throw new IllegalArgumentException("Already rented booking cannot be edited.");
		}

		ensureVehicleNotBooked(booking.getVehicleId(), booking.getId());

		Vehicle vehicle = vehicleService.findVehicle(booking.getVehicleId())
				.orElseThrow(() -> new IllegalArgumentException("Vehicle not found."));

		validateOffer(vehicle.getPrice(), offerAmount);

		booking.setOfferAmount(offerAmount);
		booking.setOfferMessage(cleanOfferMessage(offerMessage));
		booking.setTotalDays(1);
		booking.setTotalAmount(offerAmount);
		if (!"REJECTED".equalsIgnoreCase(booking.getStatus())) {
			booking.setStatus("PENDING");
		}
		booking.setUpdatedAt(LocalDateTime.now());

		return bookingRepository.update(booking);
	}

	public void deleteBookingByCustomer(Long customerId, Long bookingId) {
		Booking booking = findById(bookingId).orElseThrow(() -> new IllegalArgumentException("Booking not found."));
		if (!booking.getCustomerId().equals(customerId)) {
			throw new IllegalArgumentException("You can delete only your own bookings.");
		}
		if ("RENTED".equalsIgnoreCase(booking.getStatus())) {
			throw new IllegalArgumentException("Already rented booking cannot be deleted.");
		}
		bookingRepository.deleteById(bookingId);
	}

	public Booking confirmBookingByRenter(Long renterId, Long bookingId) {
		Booking booking = getRenterOwnedBooking(renterId, bookingId);
		booking.setStatus("CONFIRMED");
		booking.setUpdatedAt(LocalDateTime.now());
		return bookingRepository.update(booking);
	}

	public Booking rejectBookingByRenter(Long renterId, Long bookingId) {
		Booking booking = getRenterOwnedBooking(renterId, bookingId);
		booking.setStatus("REJECTED");
		booking.setPaid(false);
		booking.setUpdatedAt(LocalDateTime.now());
		return bookingRepository.update(booking);
	}

	public Booking markBookingRentedByRenter(Long renterId, Long bookingId) {
		Booking booking = getRenterOwnedBooking(renterId, bookingId);
		if (!"CONFIRMED".equalsIgnoreCase(booking.getStatus())) {
			throw new IllegalArgumentException("Only confirmed bookings can be marked as rented.");
		}
		booking.setStatus("RENTED");
		booking.setUpdatedAt(LocalDateTime.now());
		Booking updated = bookingRepository.update(booking);

		// Once the buyer has paid and the seller marks it complete, the listing should no longer appear.
		vehicleService.markVehicleSold(booking.getVehicleId());
		return updated;
	}

	public Booking markPaid(Long customerId, Long bookingId) {
		Booking booking = findById(bookingId).orElseThrow(() -> new IllegalArgumentException("Booking not found."));
		if (!booking.getCustomerId().equals(customerId)) {
			throw new IllegalArgumentException("You can pay only your own booking.");
		}
		if (!"CONFIRMED".equalsIgnoreCase(booking.getStatus())) {
			throw new IllegalArgumentException("Only confirmed bookings can be paid.");
		}
		booking.setPaid(true);
		booking.setUpdatedAt(LocalDateTime.now());
		return bookingRepository.update(booking);
	}

	public Optional<Booking> findById(Long bookingId) {
		return bookingRepository.findById(bookingId);
	}

	private Booking getRenterOwnedBooking(Long renterId, Long bookingId) {
		Booking booking = findById(bookingId).orElseThrow(() -> new IllegalArgumentException("Booking not found."));
		if (!booking.getRenterId().equals(renterId)) {
			throw new IllegalArgumentException("You can manage only bookings for your vehicles.");
		}
		return booking;
	}

	private void validateOffer(double listingPrice, double offerAmount) {
		if (offerAmount <= 0) {
			throw new IllegalArgumentException("Offer amount must be greater than 0.");
		}
		// Simple safety cap to prevent clearly invalid data.
		if (offerAmount > Math.max(100000, listingPrice * 2)) {
			throw new IllegalArgumentException("Offer amount looks too high.");
		}
	}

	private String cleanOfferMessage(String value) {
		if (value == null) {
			return "";
		}
		String cleaned = value.trim().replaceAll("\\s+", " ");
		if (cleaned.length() > 500) {
			return cleaned.substring(0, 500);
		}
		return cleaned;
	}

	private void ensureVehicleNotBooked(Long vehicleId, Long ignoreBookingId) {
		boolean alreadyBooked = bookingRepository.findAll().stream()
				.filter(booking -> booking.getVehicleId().equals(vehicleId))
				.filter(booking -> ignoreBookingId == null || !booking.getId().equals(ignoreBookingId))
				.anyMatch(booking -> isActiveBookingStatus(booking.getStatus()));

		if (alreadyBooked) {
			throw new IllegalArgumentException("This vehicle is already booked and cannot be booked again right now.");
		}
	}

	private boolean isActiveBookingStatus(String status) {
		if (status == null) {
			return false;
		}
		return "PENDING".equalsIgnoreCase(status)
				|| "CONFIRMED".equalsIgnoreCase(status)
				|| "RENTED".equalsIgnoreCase(status);
	}
}
