// seller-approvals.js
(function () {
  "use strict";

  async function requestJson(url, options) {
    const response = await fetch(url, {
      credentials: "same-origin",
      headers: { "Content-Type": "application/json" },
      ...options
    });
    let body = {};
    try { body = await response.json(); } catch (_) { body = {}; }
    if (!response.ok) throw new Error(body.message || "Request failed.");
    return body;
  }

  async function loadBookingRequests() {
    const tableBody = document.getElementById("sellerApprovalsTableBody");
    const feedback = document.getElementById("sellerApprovalsFeedback");
    tableBody.innerHTML = "<tr><td colspan='7'>Loading...</td></tr>";
    try {
      const data = await requestJson("/api/bookings/mine/renter", { method: "GET" });
      const bookings = data.bookings || [];
      if (!bookings.length) {
        tableBody.innerHTML = "<tr><td colspan='7'>No pending requests.</td></tr>";
        return;
      }
      tableBody.innerHTML = bookings.map(function (b) {
        let actions = '';
        if (b.status === 'PENDING') {
          actions += `<button class="account-btn-primary approve-btn" data-id="${b.id}">Approve</button> `;
          actions += `<button class="account-btn-danger reject-btn" data-id="${b.id}">Reject</button> `;
        }
        if (b.status === 'CONFIRMED' && !b.paid) {
          actions += `<button class="account-btn-secondary mark-sold-btn" data-id="${b.id}">Mark as Sold</button> `;
        }
        if (b.status === 'SOLD') {
          actions += `<button class="account-btn-secondary accept-return-btn" data-id="${b.id}">Accept Return</button> `;
        }
        actions += `<button class="account-btn-secondary update-booking-btn" data-id="${b.id}">Update</button> `;
        actions += `<button class="account-btn-danger delete-booking-btn" data-id="${b.id}">Delete</button>`;
        return `<tr>
          <td>${b.id}</td>
          <td>${b.vehicleBrand} ${b.vehicleModel}</td>
          <td>${b.customerId}</td>
          <td>${b.startDate || ''} - ${b.endDate || ''}</td>
          <td>$${b.offerAmount || b.totalAmount}</td>
          <td>${b.status}${b.paid ? ' / PAID' : ''}</td>
          <td>${actions}</td>
        </tr>`;
      }).join("");

      tableBody.querySelectorAll(".approve-btn").forEach(function (btn) {
        btn.addEventListener("click", async function () {
          await handleAction(btn, "confirm");
        });
      });
      tableBody.querySelectorAll(".reject-btn").forEach(function (btn) {
        btn.addEventListener("click", async function () {
          await handleAction(btn, "reject");
        });
      });
      tableBody.querySelectorAll(".mark-sold-btn").forEach(function (btn) {
        btn.addEventListener("click", async function () {
          await handleAction(btn, "rented");
        });
      });
      tableBody.querySelectorAll(".accept-return-btn").forEach(function (btn) {
        btn.addEventListener("click", async function () {
          // Implement accept return logic here (custom endpoint if needed)
          alert('Accept Return: Not yet implemented.');
        });
      });
      tableBody.querySelectorAll(".update-booking-btn").forEach(function (btn) {
        btn.addEventListener("click", async function () {
          alert('Update Booking: Not yet implemented.');
        });
      });
      tableBody.querySelectorAll(".delete-booking-btn").forEach(function (btn) {
        btn.addEventListener("click", async function () {
          if (confirm('Delete this booking?')) {
            await handleDeleteBooking(btn.getAttribute('data-id'));
          }
        });
      });
      async function handleDeleteBooking(id) {
        const feedback = document.getElementById("sellerApprovalsFeedback");
        feedback.textContent = "";
        try {
          await requestJson(`/api/bookings/${id}`, { method: "DELETE" });
          feedback.textContent = `Booking ${id} deleted.`;
          await loadBookingRequests();
        } catch (e) {
          feedback.textContent = e.message;
        }
      }
    } catch (e) {
      tableBody.innerHTML = "<tr><td colspan='7'>Error loading requests.</td></tr>";
      feedback.textContent = e.message;
    }
  }

  async function handleAction(btn, action) {
    const id = btn.getAttribute("data-id");
    const feedback = document.getElementById("sellerApprovalsFeedback");
    feedback.textContent = "";
    btn.disabled = true;
    try {
      await requestJson(`/api/bookings/${id}/${action}`, { method: "POST", body: "{}" });
      feedback.textContent = `Booking ${id} ${action === 'confirm' ? 'approved' : 'rejected'}.`;
      await loadBookingRequests();
    } catch (e) {
      feedback.textContent = e.message;
      btn.disabled = false;
    }
  }

  document.addEventListener("DOMContentLoaded", loadBookingRequests);
})();
