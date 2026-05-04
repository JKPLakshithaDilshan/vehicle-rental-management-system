(function () {
  "use strict";

  function requestJson(url, options) {
    return fetch(url, {
      credentials: "same-origin",
      headers: { "Content-Type": "application/json" },
      ...options
    }).then(function (response) {
      return response.json().catch(function () { return {}; }).then(function (body) {
        if (!response.ok) {
          throw new Error(body.message || "Request failed.");
        }
        return body;
      });
    });
  }

  function setFeedback(id, message, type) {
    var element = document.getElementById(id);
    if (!element) {
      return;
    }
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

  var reviewModalState = {
    mode: null,
    reviewId: null
  };

  function openReviewModal(mode, review) {
    var modal = document.getElementById("accountReviewModal");
    if (!modal) {
      return;
    }

    var title = document.getElementById("accountReviewModalTitle");
    var saveBtn = document.getElementById("accountReviewModalSaveBtn");
    var editFields = document.getElementById("accountReviewFields");
    var deleteFields = document.getElementById("accountReviewDeleteFields");
    var ratingInput = document.getElementById("accountModalRating");
    var commentInput = document.getElementById("accountModalComment");

    reviewModalState.mode = mode;
    reviewModalState.reviewId = review ? review.id : null;

    setFeedback("accountReviewModalFeedback", "", null);
    if (editFields) {
      editFields.classList.toggle("is-active", mode === "edit");
    }
    if (deleteFields) {
      deleteFields.classList.toggle("is-active", mode === "delete");
    }

    if (mode === "edit") {
      if (title) {
        title.textContent = "Update Review";
      }
      if (saveBtn) {
        saveBtn.textContent = "Save Review";
      }
      if (ratingInput) {
        ratingInput.value = review ? String(review.rating || 5) : "5";
      }
      if (commentInput) {
        commentInput.value = review ? (review.comment || "") : "";
      }
    } else {
      if (title) {
        title.textContent = "Delete Review";
      }
      if (saveBtn) {
        saveBtn.textContent = "Delete Review";
      }
    }

    modal.classList.add("is-open");
    modal.setAttribute("aria-hidden", "false");
  }

  function closeReviewModal() {
    var modal = document.getElementById("accountReviewModal");
    if (!modal) {
      return;
    }
    modal.classList.remove("is-open");
    modal.setAttribute("aria-hidden", "true");
    reviewModalState.mode = null;
    reviewModalState.reviewId = null;
    setFeedback("accountReviewModalFeedback", "", null);
  }

  function wireReviewModal() {
    var modal = document.getElementById("accountReviewModal");
    var form = document.getElementById("accountReviewModalForm");
    var closeBtn = document.getElementById("accountReviewModalCloseBtn");
    var cancelBtn = document.getElementById("accountReviewModalCancelBtn");

    if (!modal || !form) {
      return;
    }

    modal.addEventListener("click", function (event) {
      if (event.target && event.target.getAttribute("data-modal-close") === "true") {
        closeReviewModal();
      }
    });

    if (closeBtn) {
      closeBtn.addEventListener("click", closeReviewModal);
    }
    if (cancelBtn) {
      cancelBtn.addEventListener("click", closeReviewModal);
    }

    form.addEventListener("submit", function (event) {
      event.preventDefault();

      if (!reviewModalState.reviewId) {
        setFeedback("accountReviewModalFeedback", "Invalid review selected.", "error");
        return;
      }

      if (reviewModalState.mode === "edit") {
        requestJson("/api/reviews/" + reviewModalState.reviewId, {
          method: "PUT",
          body: JSON.stringify({
            rating: Number(document.getElementById("accountModalRating").value),
            comment: document.getElementById("accountModalComment").value
          })
        }).then(function () {
          closeReviewModal();
          setFeedback("myReviewsFeedback", "Review updated.", "success");
          return loadMyReviews();
        }).catch(function (error) {
          setFeedback("accountReviewModalFeedback", error.message, "error");
        });
        return;
      }

      if (reviewModalState.mode === "delete") {
        requestJson("/api/reviews/" + reviewModalState.reviewId, { method: "DELETE" })
          .then(function () {
            closeReviewModal();
            setFeedback("myReviewsFeedback", "Review deleted.", "success");
            return loadMyReviews();
          })
          .catch(function (error) {
            setFeedback("accountReviewModalFeedback", error.message, "error");
          });
      }
    });
  }

  function renderRenterBookings(bookings) {
    var body = document.getElementById("renterBookingsBody");
    if (!body) {
      return;
    }
    if (!bookings.length) {
      body.innerHTML = "<tr><td colspan='6'>No booking requests for your vehicles yet.</td></tr>";
      return;
    }

    body.innerHTML = bookings.map(function (booking) {
      return "<tr>"
        + "<td>" + booking.id + "</td>"
        + "<td>" + escapeHtml((booking.vehicleBrand || "") + " " + (booking.vehicleModel || "")) + "</td>"
        + "<td>" + booking.customerId + "</td>"
        + "<td>" + escapeHtml(booking.startDate + " to " + booking.endDate) + "</td>"
        + "<td>" + escapeHtml(booking.status || "") + "</td>"
        + "<td>"
        + "<button type='button' class='account-btn-primary booking-renter-action' data-action='confirm' data-id='" + booking.id + "'>Confirm</button> "
        + "<button type='button' class='account-btn-secondary booking-renter-action' data-action='reject' data-id='" + booking.id + "'>Reject</button> "
        + "<button type='button' class='account-btn-danger booking-renter-action' data-action='rented' data-id='" + booking.id + "'>Already Rented</button>"
        + "</td>"
        + "</tr>";
    }).join("");

    body.querySelectorAll(".booking-renter-action").forEach(function (button) {
      button.addEventListener("click", function () {
        var id = Number(button.getAttribute("data-id"));
        var action = button.getAttribute("data-action");
        requestJson("/api/bookings/" + id + "/" + action, { method: "POST", body: "{}" })
          .then(function () {
            setFeedback("renterBookingsFeedback", "Booking status updated.", "success");
            return loadRenterBookings();
          })
          .catch(function (error) {
            setFeedback("renterBookingsFeedback", error.message, "error");
          });
      });
    });
  }

  function renderReviews(id, reviews, emptyText) {
    var body = document.getElementById(id);
    if (!body) {
      return;
    }
    if (!reviews.length) {
      body.innerHTML = "<tr><td colspan='5'>" + emptyText + "</td></tr>";
      return;
    }

    body.innerHTML = reviews.map(function (review) {
      return "<tr>"
        + "<td>" + review.vehicleId + "</td>"
        + "<td>" + review.rating + "/5</td>"
        + "<td>" + escapeHtml(review.comment || "") + "</td>"
        + "<td>" + review.customerId + "</td>"
        + "<td>" + (id === "myReviewsBody"
          ? "<button type='button' class='account-btn-secondary review-edit' data-id='" + review.id + "'>Edit</button> <button type='button' class='account-btn-danger review-delete' data-id='" + review.id + "'>Delete</button>"
          : "-")
        + "</td>"
        + "</tr>";
    }).join("");

    if (id === "myReviewsBody") {
      body.querySelectorAll(".review-edit").forEach(function (button) {
        button.addEventListener("click", function () {
          var reviewId = Number(button.getAttribute("data-id"));
          var review = reviews.find(function (item) { return item.id === reviewId; });
          if (!review) {
            return;
          }
          openReviewModal("edit", review);
        });
      });

      body.querySelectorAll(".review-delete").forEach(function (button) {
        button.addEventListener("click", function () {
          var reviewId = Number(button.getAttribute("data-id"));
          var review = reviews.find(function (item) { return item.id === reviewId; });
          if (!review) {
            return;
          }
          openReviewModal("delete", review);
        });
      });
    }
  }

  function loadRenterBookings() {
    return requestJson("/api/bookings/mine/renter", { method: "GET" })
      .then(function (data) {
        renderRenterBookings(data.bookings || []);
      })
      .catch(function (error) {
        setFeedback("renterBookingsFeedback", error.message, "error");
      });
  }

  function loadMyReviews() {
    return requestJson("/api/reviews/mine/customer", { method: "GET" })
      .then(function (data) {
        renderReviews("myReviewsBody", data.reviews || [], "You did not post reviews yet.");
      })
      .catch(function (error) {
        setFeedback("myReviewsFeedback", error.message, "error");
      });
  }

  function loadReviewsOnMyVehicles() {
    return requestJson("/api/reviews/mine/renter", { method: "GET" })
      .then(function (data) {
        renderReviews("reviewsOnMyVehiclesBody", data.reviews || [], "No reviews posted on your vehicles yet.");
      })
      .catch(function (error) {
        setFeedback("renterReviewsFeedback", error.message, "error");
      });
  }

  function init() {
    if (!document.body.classList.contains("auth-page-account")) {
      return;
    }
    wireReviewModal();
    loadRenterBookings();
    loadMyReviews();
    loadReviewsOnMyVehicles();
  }

  init();
})();
