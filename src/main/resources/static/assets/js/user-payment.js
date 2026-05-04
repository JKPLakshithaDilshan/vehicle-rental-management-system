(function () {
  "use strict";

  function setFeedback(id, message, type) {
    var element = document.getElementById(id);
    if (!element) return;
    element.textContent = message || "";
    element.classList.remove("is-error", "is-success");
    if (type) {
      element.classList.add(type === "error" ? "is-error" : "is-success");
    }
  }

  function escapeHtml(value) {
    return String(value || "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  function requestJson(url, options) {
    return fetch(url, {
      credentials: "same-origin",
      headers: { "Content-Type": "application/json" },
      ...options
    }).then(function (response) {
      return response.json().catch(function () { return {}; }).then(function (body) {
        if (!response.ok) throw new Error(body.message || "Request failed.");
        return body;
      });
    });
  }

  var state = {
    auth: null,
    cards: [],
    myBookings: [],
    modalMode: null,
    modalContext: {}
  };

  function normalizeOfferAmount(raw) {
    var value = Number(raw || 0);
    return Number.isFinite(value) ? value : 0;
  }

  function showActionModal(mode, context) {
    var modal = document.getElementById("paymentActionModal");
    if (!modal) return;

    state.modalMode = mode;
    state.modalContext = context || {};

    var title = document.getElementById("paymentActionTitle");
    var saveBtn = document.getElementById("paymentActionSaveBtn");
    var bookingFields = document.getElementById("modalRequestFields");
    var paymentFields = document.getElementById("modalPaymentFields");
    var reviewFields = document.getElementById("modalReviewFields");
    var deleteFields = document.getElementById("modalDeleteFields");
    var deleteText = document.getElementById("modalDeleteText");

    [bookingFields, paymentFields, reviewFields, deleteFields].forEach(function (el) {
      if (el) el.classList.remove("is-active");
    });
    setFeedback("paymentActionFeedback", "", null);

    if (mode === "edit-booking") {
      if (title) title.textContent = "Edit Purchase Request";
      if (saveBtn) saveBtn.textContent = "Update Purchase Request";
      if (bookingFields) bookingFields.classList.add("is-active");
      document.getElementById("modalOfferAmount").value = String(context.offerAmount || "");
      document.getElementById("modalOfferMessage").value = context.offerMessage || "";
    } else if (mode === "pay-booking") {
      if (title) title.textContent = "Pay Purchase Request";
      if (saveBtn) saveBtn.textContent = "Pay Now";
      if (paymentFields) paymentFields.classList.add("is-active");
      var select = document.getElementById("modalCardSelect");
      if (select) {
        select.innerHTML = state.cards.map(function (card) {
          return "<option value='" + card.id + "'>" + card.id + " - " + escapeHtml(card.cardHolderName || "Card") + "</option>";
        }).join("");
      }
    } else if (mode === "create-review" || mode === "edit-review") {
      if (title) title.textContent = mode === "create-review" ? "Add Review" : "Update Review";
      if (saveBtn) saveBtn.textContent = mode === "create-review" ? "Post Review" : "Save Review";
      if (reviewFields) reviewFields.classList.add("is-active");
      document.getElementById("modalRating").value = context.rating || "5";
      document.getElementById("modalComment").value = context.comment || "";
    } else if (mode === "delete-card" || mode === "delete-booking" || mode === "delete-review") {
      if (title) title.textContent = "Confirm Delete";
      if (saveBtn) saveBtn.textContent = "Delete";
      if (deleteFields) deleteFields.classList.add("is-active");
      if (deleteText) deleteText.textContent = context.message || "Are you sure you want to delete this item?";
    }

    modal.classList.add("is-open");
    modal.setAttribute("aria-hidden", "false");
  }

  function closeActionModal() {
    var modal = document.getElementById("paymentActionModal");
    if (!modal) return;
    modal.classList.remove("is-open");
    modal.setAttribute("aria-hidden", "true");
    state.modalMode = null;
    state.modalContext = {};
    setFeedback("paymentActionFeedback", "", null);
  }

  function wireActionModal() {
    var modal = document.getElementById("paymentActionModal");
    var closeBtn = document.getElementById("paymentActionCloseBtn");
    var cancelBtn = document.getElementById("paymentActionCancelBtn");
    var form = document.getElementById("paymentActionForm");

    if (!modal || !form) return;

    modal.addEventListener("click", function (event) {
      if (event.target && event.target.getAttribute("data-modal-close") === "true") closeActionModal();
    });

    if (closeBtn) closeBtn.addEventListener("click", closeActionModal);
    if (cancelBtn) cancelBtn.addEventListener("click", closeActionModal);

    form.addEventListener("submit", function (event) {
      event.preventDefault();

      if (state.modalMode === "edit-booking") {
        var offerAmount = normalizeOfferAmount(document.getElementById("modalOfferAmount").value);
        if (!offerAmount || offerAmount <= 0) {
          setFeedback("paymentActionFeedback", "Offer amount must be greater than 0.", "error");
          return;
        }

        requestJson("/api/bookings/" + state.modalContext.bookingId, {
          method: "PUT",
          body: JSON.stringify({
            offerAmount: offerAmount,
            offerMessage: document.getElementById("modalOfferMessage").value
          })
        }).then(function () {
          closeActionModal();
          setFeedback("myRequestsFeedback", "Purchase request updated.", "success");
          return loadBookings();
        }).catch(function (error) {
          setFeedback("paymentActionFeedback", error.message, "error");
        });
        return;
      }

      if (state.modalMode === "pay-booking") {
        requestJson("/api/payments/bookings/" + state.modalContext.bookingId + "/pay", {
          method: "POST",
          body: JSON.stringify({ cardId: Number(document.getElementById("modalCardSelect").value) })
        }).then(function () {
          closeActionModal();
          setFeedback("myRequestsFeedback", "Payment successful.", "success");
          return loadBookings();
        }).catch(function (error) {
          setFeedback("paymentActionFeedback", error.message, "error");
        });
        return;
      }

      if (state.modalMode === "create-review") {
        requestJson("/api/reviews", {
          method: "POST",
          body: JSON.stringify({
            vehicleId: state.modalContext.vehicleId, // Pass vehicleId from modal context
            bookingId: state.modalContext.bookingId,
            rating: Number(document.getElementById("modalRating").value),
            comment: document.getElementById("modalComment").value
          })
        }).then(function () {
          closeActionModal();
          setFeedback("myRequestsFeedback", "Review submitted. Check 'My Reviews' on your profile to manage it.", "success");
        }).catch(function (error) {
          setFeedback("paymentActionFeedback", error.message, "error");
        });
        return;
      }

      if (state.modalMode === "delete-card") {
        requestJson("/api/payments/cards/" + state.modalContext.cardId, { method: "DELETE" })
          .then(function () {
            closeActionModal();
            setFeedback("cardFeedback", "Card deleted.", "success");
            return loadCards();
          })
          .catch(function (error) {
            setFeedback("paymentActionFeedback", error.message, "error");
          });
        return;
      }

      if (state.modalMode === "delete-booking") {
        requestJson("/api/bookings/" + state.modalContext.bookingId, { method: "DELETE" })
          .then(function () {
            closeActionModal();
            setFeedback("myRequestsFeedback", "Purchase request deleted.", "success");
            return loadBookings();
          })
          .catch(function (error) {
            setFeedback("paymentActionFeedback", error.message, "error");
          });
        return;
      }
    });
  }

  function offerSummary(booking) {
    var amount = Number(booking.offerAmount || booking.totalAmount || 0);
    var msg = String(booking.offerMessage || "").trim();
    return "$" + amount.toFixed(2) + (msg ? " — " + msg : "");
  }

  function renderCards() {
    var body = document.getElementById("cardsTableBody");
    if (!body) return;
    if (!state.cards.length) {
      body.innerHTML = "<tr><td colspan='5'>No cards saved.</td></tr>";
      return;
    }

    body.innerHTML = state.cards.map(function (card) {
      return "<tr>"
        + "<td>" + card.id + "</td>"
        + "<td>" + escapeHtml(card.cardHolderName) + "</td>"
        + "<td>" + escapeHtml(card.cardNumber) + "</td>"
        + "<td>" + escapeHtml(card.expiryMonth) + "/" + escapeHtml(card.expiryYear) + "</td>"
        + "<td>"
        + "<button type='button' class='account-btn-secondary card-edit-btn' data-card-id='" + card.id + "'>Edit</button> "
        + "<button type='button' class='account-btn-danger card-delete-btn' data-card-id='" + card.id + "'>Delete</button>"
        + "</td>"
        + "</tr>";
    }).join("");

    body.querySelectorAll(".card-edit-btn").forEach(function (button) {
      button.addEventListener("click", function () {
        var id = Number(button.getAttribute("data-card-id"));
        var card = state.cards.find(function (item) { return item.id === id; });
        if (!card) return;
        document.getElementById("cardIdField").value = card.id;
        document.getElementById("cardHolderField").value = card.cardHolderName || "";
        document.getElementById("cardNumberField").value = card.cardNumber || "";
        document.getElementById("cardMonthField").value = card.expiryMonth || "";
        document.getElementById("cardYearField").value = card.expiryYear || "";
        document.getElementById("cardCvvField").value = "";
        document.getElementById("cardSubmitBtn").textContent = "Update Card";
        setFeedback("cardFeedback", "Editing card " + card.id + ".", "success");
      });
    });

    body.querySelectorAll(".card-delete-btn").forEach(function (button) {
      button.addEventListener("click", function () {
        var id = Number(button.getAttribute("data-card-id"));
        showActionModal("delete-card", {
          cardId: id,
          message: "Delete card " + id + "?"
        });
      });
    });
  }

  function renderBookings() {
    var body = document.getElementById("myRequestsTableBody");
    if (!body) return;
    if (!state.myBookings.length) {
      body.innerHTML = "<tr><td colspan='6'>No purchase requests found.</td></tr>";
      return;
    }

    body.innerHTML = state.myBookings.map(function (booking) {
      var actions = [
        "<button type='button' class='account-btn-secondary booking-edit-btn' data-booking-id='" + booking.id + "'>Edit</button>",
        "<button type='button' class='account-btn-danger booking-delete-btn' data-booking-id='" + booking.id + "'>Delete</button>"
      ];

      if (booking.status === "CONFIRMED" && !booking.paid) {
        actions.push("<button type='button' class='account-btn-primary booking-pay-btn' data-booking-id='" + booking.id + "'>Pay</button>");
      }

      if (booking.paid) {
        actions.push("<button type='button' class='account-btn-secondary booking-review-btn' data-booking-id='" + booking.id + "'>Review</button>");
      }

      return "<tr>"
        + "<td>" + booking.id + "</td>"
        + "<td><a href='car-single.html?vehicleId=" + booking.vehicleId + "'>" + escapeHtml((booking.vehicleBrand || "") + " " + (booking.vehicleModel || "")) + "</a></td>"
        + "<td>" + escapeHtml(offerSummary(booking)) + "</td>"
        + "<td>" + escapeHtml(booking.status || "") + (booking.paid ? " / PAID" : "") + "</td>"
        + "<td>$" + Number(booking.totalAmount || 0).toFixed(2) + "</td>"
        + "<td class='booking-actions-cell'>" + actions.join(" ") + "</td>"
        + "</tr>";
    }).join("");

    body.querySelectorAll(".booking-edit-btn").forEach(function (button) {
      button.addEventListener("click", function () {
        var id = Number(button.getAttribute("data-booking-id"));
        var booking = state.myBookings.find(function (item) { return item.id === id; });
        if (!booking) return;
        showActionModal("edit-booking", {
          bookingId: id,
          offerAmount: booking.offerAmount || booking.totalAmount || 0,
          offerMessage: booking.offerMessage || ""
        });
      });
    });

    body.querySelectorAll(".booking-delete-btn").forEach(function (button) {
      button.addEventListener("click", function () {
        var id = Number(button.getAttribute("data-booking-id"));
        showActionModal("delete-booking", {
          bookingId: id,
          message: "Delete purchase request " + id + "?"
        });
      });
    });

    body.querySelectorAll(".booking-pay-btn").forEach(function (button) {
      button.addEventListener("click", function () {
        var id = Number(button.getAttribute("data-booking-id"));
        if (!state.cards.length) {
          setFeedback("myRequestsFeedback", "Add a payment card before paying.", "error");
          return;
        }
        showActionModal("pay-booking", { bookingId: id });
      });
    });

    body.querySelectorAll(".booking-review-btn").forEach(function (button) {
      button.addEventListener("click", function () {
        var id = Number(button.getAttribute("data-booking-id"));
        var booking = state.myBookings.find(function (item) { return item.id === id; });
        if (!booking) return;
        showActionModal("create-review", { bookingId: id, vehicleId: booking.vehicleId, rating: 5, comment: "" });
      });
    });
  }

  function loadAuth() {
    return requestJson("/api/auth/status", { method: "GET" }).then(function (auth) {
      if (!auth || !auth.authenticated) {
        window.location.href = "login.html";
        return Promise.reject(new Error("Unauthorized"));
      }
      state.auth = auth;
      var name = document.getElementById("purchaseProfileName");
      var meta = document.getElementById("purchaseProfileMeta");
      if (name) name.textContent = auth.user ? auth.user.name : "User";
      if (meta) meta.textContent = auth.user ? auth.user.email : "";
      return auth;
    });
  }

  function loadBookings() {
    return requestJson("/api/bookings/mine/customer", { method: "GET" })
      .then(function (data) {
        state.myBookings = data.bookings || [];
        renderBookings();
      })
      .catch(function (error) {
        setFeedback("myRequestsFeedback", error.message, "error");
      });
  }

  function loadCards() {
    return requestJson("/api/payments/cards", { method: "GET" })
      .then(function (data) {
        state.cards = data.cards || [];
        renderCards();
      })
      .catch(function (error) {
        setFeedback("cardFeedback", error.message, "error");
      });
  }

  function validateCardForm() {
    var holderName = document.getElementById("cardHolderField").value.trim();
    var cardNumber = document.getElementById("cardNumberField").value.trim().replace(/\s+/g, "");
    var expiryMonth = document.getElementById("cardMonthField").value.trim();
    var expiryYear = document.getElementById("cardYearField").value.trim();
    var cvv = document.getElementById("cardCvvField").value.trim();

    if (!holderName || holderName.length < 3) return "Card holder name must be at least 3 characters.";
    if (!/^\d{16}$/.test(cardNumber)) return "Card number must be 16 digits.";
    if (!/^(0[1-9]|1[0-2])$/.test(expiryMonth)) return "Expiry month must be between 01 and 12.";
    if (!/^\d{4}$/.test(expiryYear)) return "Expiry year must be 4 digits (YYYY).";

    var currentDate = new Date();
    var currentYear = currentDate.getFullYear();
    var currentMonth = currentDate.getMonth() + 1;
    var expMonth = parseInt(expiryMonth, 10);
    var expYear = parseInt(expiryYear, 10);

    if (expYear < currentYear || (expYear === currentYear && expMonth < currentMonth)) return "Card has expired.";
    if (!/^\d{3}$/.test(cvv)) return "CVV must be 3 digits.";

    return null;
  }

  function wireCardForm() {
    var form = document.getElementById("cardForm");
    var clearBtn = document.getElementById("cardClearBtn");
    if (!form) return;

    form.addEventListener("submit", function (event) {
      event.preventDefault();

      var validationError = validateCardForm();
      if (validationError) {
        setFeedback("cardFeedback", validationError, "error");
        return;
      }

      var payload = {
        cardHolderName: document.getElementById("cardHolderField").value,
        cardNumber: document.getElementById("cardNumberField").value,
        expiryMonth: document.getElementById("cardMonthField").value,
        expiryYear: document.getElementById("cardYearField").value,
        cvv: document.getElementById("cardCvvField").value
      };
      var cardId = document.getElementById("cardIdField").value;
      var url = cardId ? "/api/payments/cards/" + cardId : "/api/payments/cards";
      var method = cardId ? "PUT" : "POST";

      requestJson(url, { method: method, body: JSON.stringify(payload) })
        .then(function () {
          setFeedback("cardFeedback", cardId ? "Card updated." : "Card added.", "success");
          form.reset();
          document.getElementById("cardIdField").value = "";
          document.getElementById("cardSubmitBtn").textContent = "Save Card";
          return loadCards();
        })
        .catch(function (error) {
          setFeedback("cardFeedback", error.message, "error");
        });
    });

    if (clearBtn) {
      clearBtn.addEventListener("click", function () {
        form.reset();
        document.getElementById("cardIdField").value = "";
        document.getElementById("cardSubmitBtn").textContent = "Save Card";
      });
    }
  }

  function wireLogout() {
    var logoutBtn = document.getElementById("userPaymentLogoutBtn");
    if (!logoutBtn) return;
    logoutBtn.addEventListener("click", function () {
      requestJson("/api/auth/logout", { method: "POST", body: "{}" }).finally(function () {
        window.location.href = "login.html";
      });
    });
  }

  function init() {
    wireActionModal();
    wireCardForm();
    wireLogout();

    loadAuth()
      .then(loadBookings)
      .then(loadCards)
      .catch(function () {
        // Handled by redirects.
      });
  }

  init();
})();
