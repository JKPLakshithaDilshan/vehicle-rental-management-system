package com.rental.model;

public class Admin {
	private String adminId;
	private String name;
	private String email;
	private String password;
	private String role;

	public Admin() {
	}

	public Admin(String adminId, String name, String email, String password, String role) {
		this.adminId = adminId;
		this.name = name;
		this.email = email;
		this.password = password;
		this.role = role;
	}

	public String getAdminId() {
		return adminId;
	}

	public void setAdminId(String adminId) {
		this.adminId = adminId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getRole() {
		return role;
	}

	public void setRole(String role) {
		this.role = role;
	}
}
