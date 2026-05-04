package com.rental.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class Booking {
	private Long id;
	private Long vehicleId;
	private Long renterId;
	private Long customerId;
	private LocalDate startDate;
	private LocalDate endDate;
	private int totalDays;
	private double totalAmount;
	// Negotiation fields for second-hand purchase requests.
	private double offerAmount;
	private String offerMessage;
	private String status;
	private boolean paid;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;

	public Booking() {
	}

	public Booking(Long id, Long vehicleId, Long renterId, Long customerId, LocalDate startDate, LocalDate endDate,
			int totalDays, double totalAmount, String status, boolean paid, LocalDateTime createdAt,
			LocalDateTime updatedAt) {
		this.id = id;
		this.vehicleId = vehicleId;
		this.renterId = renterId;
		this.customerId = customerId;
		this.startDate = startDate;
		this.endDate = endDate;
		this.totalDays = totalDays;
		this.totalAmount = totalAmount;
		this.offerAmount = totalAmount;
		this.offerMessage = "";
		this.status = status;
		this.paid = paid;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	public Booking(Long id, Long vehicleId, Long renterId, Long customerId, LocalDate startDate, LocalDate endDate,
			int totalDays, double totalAmount, double offerAmount, String offerMessage, String status, boolean paid,
			LocalDateTime createdAt, LocalDateTime updatedAt) {
		this.id = id;
		this.vehicleId = vehicleId;
		this.renterId = renterId;
		this.customerId = customerId;
		this.startDate = startDate;
		this.endDate = endDate;
		this.totalDays = totalDays;
		this.totalAmount = totalAmount;
		this.offerAmount = offerAmount;
		this.offerMessage = offerMessage;
		this.status = status;
		this.paid = paid;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getVehicleId() {
		return vehicleId;
	}

	public void setVehicleId(Long vehicleId) {
		this.vehicleId = vehicleId;
	}

	public Long getRenterId() {
		return renterId;
	}

	public void setRenterId(Long renterId) {
		this.renterId = renterId;
	}

	public Long getCustomerId() {
		return customerId;
	}

	public void setCustomerId(Long customerId) {
		this.customerId = customerId;
	}

	public LocalDate getStartDate() {
		return startDate;
	}

	public void setStartDate(LocalDate startDate) {
		this.startDate = startDate;
	}

	public LocalDate getEndDate() {
		return endDate;
	}

	public void setEndDate(LocalDate endDate) {
		this.endDate = endDate;
	}

	public int getTotalDays() {
		return totalDays;
	}

	public void setTotalDays(int totalDays) {
		this.totalDays = totalDays;
	}

	public double getTotalAmount() {
		return totalAmount;
	}

	public void setTotalAmount(double totalAmount) {
		this.totalAmount = totalAmount;
	}

	public double getOfferAmount() {
		return offerAmount;
	}

	public void setOfferAmount(double offerAmount) {
		this.offerAmount = offerAmount;
	}

	public String getOfferMessage() {
		return offerMessage;
	}

	public void setOfferMessage(String offerMessage) {
		this.offerMessage = offerMessage;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public boolean isPaid() {
		return paid;
	}

	public void setPaid(boolean paid) {
		this.paid = paid;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(LocalDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}
}
