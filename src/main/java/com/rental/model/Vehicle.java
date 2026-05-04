package com.rental.model;

import java.time.LocalDateTime;
import java.util.List;

public class Vehicle {
	private Long id;
	private Long sellerId;
	// Stored as separate fields, but we keep `getTitle()` for compatibility.
	private String brand;
	private String model;
	private double price;
	private int year;
	private List<String> images;
	private String description;
	private String status; // available, sold
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;

	public Vehicle() {
	}

	public Vehicle(Long id, Long sellerId, String brand, String model, double price, int year, List<String> images,
			String description,
			String status, LocalDateTime createdAt, LocalDateTime updatedAt) {
		this.id = id;
		this.sellerId = sellerId;
		this.brand = brand;
		this.model = model;
		this.price = price;
		this.year = year;
		this.images = images;
		this.description = description;
		this.status = status;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getSellerId() {
		return sellerId;
	}

	public void setSellerId(Long sellerId) {
		this.sellerId = sellerId;
	}

	public String getBrand() {
		return brand;
	}

	public void setBrand(String brand) {
		this.brand = brand;
	}

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public double getPrice() {
		return price;
	}

	public void setPrice(double price) {
		this.price = price;
	}

	public int getYear() {
		return year;
	}

	public void setYear(int year) {
		this.year = year;
	}

	public List<String> getImages() {
		return images;
	}

	public void setImages(List<String> images) {
		this.images = images;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
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

	/**
	 * Compatibility helper. Some services/controllers still refer to a "title".
	 */
	public String getTitle() {
		String b = brand == null ? "" : brand.trim();
		String m = model == null ? "" : model.trim();
		return (b + " " + m).trim();
	}
}
