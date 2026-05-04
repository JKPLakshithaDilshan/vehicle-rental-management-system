(function () {
  "use strict";

  const HOURLY_REFRESH_MS = 60 * 60 * 1000;

  async function requestJson(url) {
    const response = await fetch(url, {
      method: "GET",
      credentials: "same-origin"
    });

    let body = {};
    try {
      body = await response.json();
    } catch (_) {
      body = {};
    }

    if (!response.ok) {
      throw new Error(body.message || "Unable to load vehicles.");
    }

    return body;
  }

  function escapeHtml(value) {
    return String(value || "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/\"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  function resolveImage(url) {
    return (url || "").trim();
  }

  function imageSrc(url) {
    const candidate = resolveImage(url);
    if (!candidate) {
      return "";
    }
    try {
      return new URL(candidate, window.location.origin).href;
    } catch (_) {
      return "";
    }
  }

  function inPagesDirectory() {
    return window.location.pathname.indexOf("/pages/") !== -1;
  }

  function pageLink(fileName) {
    return inPagesDirectory() ? fileName : "pages/" + fileName;
  }

  function bookingLink(vehicleId) {
    return pageLink("booking.html") + "?vehicleId=" + encodeURIComponent(vehicleId);
  }

  function isRegisteredBookingUser(authStatus) {
    if (!authStatus || !authStatus.authenticated) {
      return false;
    }
    const role = String(authStatus.role || "").toUpperCase();
    return role === "BUYER" || role === "SELLER" || role === "DEALER" || role === "RENTER" || role === "USER";
  }

  async function getAuthStatus() {
    try {
      return await requestJson("/api/auth/status");
    } catch (_) {
      return null;
    }
  }

  function resolveBookingEntryLink(vehicleId, authStatus) {
    if (isRegisteredBookingUser(authStatus)) {
      return bookingLink(vehicleId);
    }
    return pageLink("login.html");
  }

  function detailsLink(vehicleId) {
    return pageLink("car-single.html") + "?vehicleId=" + encodeURIComponent(vehicleId);
  }

  function getRates(pricePerDay) {
    const dayRate = Number(pricePerDay || 0);
    return {
      dayRate: dayRate,
      hourRate: dayRate,
      monthRate: dayRate
    };
  }

  function formatCurrency(value) {
    return String(Math.round(Number(value || 0)));
  }

  function getVehicleIdFromUrl() {
    const params = new URLSearchParams(window.location.search);
    const raw = params.get("vehicleId");
    if (!raw) {
      return null;
    }
    const parsed = Number(raw);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
  }

  function lastUpdatedText() {
    return "Listings refresh every hour. Last refresh: " + new Date().toLocaleTimeString([], {
      hour: "2-digit",
      minute: "2-digit"
    });
  }

  function renderReviewStars(rating) {
    const safeRating = Math.max(1, Math.min(5, Number(rating || 0)));
    let stars = "";
    for (let i = 0; i < safeRating; i += 1) {
      stars += "&#9733;";
    }
    return stars;
  }

  async function initHomeFeaturedVehicles() {
    const grid = document.getElementById("featuredVehiclesGrid");
    const feedback = document.getElementById("featuredVehiclesFeedback");
    if (!grid) {
      return;
    }

    async function loadFeaturedVehicles() {
      try {
        const authStatus = await getAuthStatus();
        const data = await requestJson("/api/vehicles");
        const vehicles = (data.vehicles || []).slice(0, 6);

        if (!vehicles.length) {
          grid.innerHTML = "<div class='col-12'><p class='vehicle-empty text-center'>No featured vehicles are available right now.</p></div>";
          if (feedback) {
            feedback.textContent = "";
          }
          return;
        }

        grid.innerHTML = vehicles
          .map(function (vehicle) {
            const src = imageSrc(vehicle.imageUrl);
            const rates = getRates(vehicle.pricePerDay);
            const vehicleBookingLink = resolveBookingEntryLink(vehicle.id, authStatus);
            const imageMarkup = src
              ? "<img class='vehicle-public-image-tag' src='" + escapeHtml(src) + "' alt='" + escapeHtml((vehicle.brand || "") + " " + (vehicle.model || "")) + "'>"
              : "<div class='vehicle-public-image'></div>";

            return (
              "<div class='col-md-6 col-lg-4 mb-4'>" +
              "<article class='vehicle-public-card vehicle-featured-card'>" +
              imageMarkup +
              "<div class='vehicle-public-body'>" +
              "<h3>" + escapeHtml(vehicle.brand) + " " + escapeHtml(vehicle.model) + "</h3>" +
              "<p class='vehicle-public-meta'>Year: " + escapeHtml(vehicle.year) + "</p>" +
              "<div class='vehicle-rate-stack'>" +
              "<span class='vehicle-rate-chip'>$" + formatCurrency(rates.hourRate) + " / car</span>" +
              "<span class='vehicle-rate-chip is-strong'>$" + formatCurrency(rates.dayRate) + " / car</span>" +
              "<span class='vehicle-rate-chip'>$" + formatCurrency(rates.monthRate) + " / car</span>" +
              "</div>" +
              "<p class='vehicle-public-desc'>" + escapeHtml(vehicle.description || "Ready to drive.") + "</p>" +
              "<div class='vehicle-card-actions'>" +
              "<a class='btn btn-primary py-2 px-3' href='" + vehicleBookingLink + "'>Buy Now</a>" +
              "<a class='btn btn-secondary py-2 px-3' href='" + detailsLink(vehicle.id) + "'>Details</a>" +
              "</div>" +
              "</div>" +
              "</article>" +
              "</div>"
            );
          })
          .join("");

        if (feedback) {
          feedback.textContent = lastUpdatedText();
        }
      } catch (error) {
        grid.innerHTML = "<div class='col-12'><p class='vehicle-empty text-center'>Unable to load featured vehicles right now.</p></div>";
        if (feedback) {
          feedback.textContent = error.message;
        }
      }
    }

    await loadFeaturedVehicles();
    window.setInterval(loadFeaturedVehicles, HOURLY_REFRESH_MS);
  }

  function wireSearch(loadFn) {
    const input = document.getElementById("vehicleSearchInput");
    const button = document.getElementById("vehicleSearchBtn");

    if (input) {
      input.addEventListener("keydown", function (event) {
        if (event.key === "Enter") {
          event.preventDefault();
          loadFn(input.value.trim());
        }
      });
    }

    if (button) {
      button.addEventListener("click", function () {
        loadFn(input ? input.value.trim() : "");
      });
    }
  }

  async function initCarsPage() {
    const grid = document.getElementById("publicVehiclesGrid");
    const feedback = document.getElementById("publicVehiclesFeedback");
    if (!grid) {
      return;
    }

    async function loadVehicles(query) {
      try {
        const authStatus = await getAuthStatus();
        const suffix = query ? "?query=" + encodeURIComponent(query) : "";
        const data = await requestJson("/api/vehicles" + suffix);
        const vehicles = data.vehicles || [];

        if (vehicles.length === 0) {
          grid.innerHTML = "<div class='col-12'><p class='vehicle-empty'>No approved vehicles available right now.</p></div>";
          if (feedback) {
            feedback.textContent = "";
          }
          return;
        }

        grid.innerHTML = vehicles
          .map(function (vehicle) {
            const rates = getRates(vehicle.pricePerDay);
            const src = imageSrc(vehicle.imageUrl);
            const vehicleBookingLink = resolveBookingEntryLink(vehicle.id, authStatus);
            const vehicleDetailsLink = detailsLink(vehicle.id);
            const imageMarkup = src
              ? "<img class='vehicle-public-image-tag' src='" + escapeHtml(src) + "' alt='" + escapeHtml((vehicle.brand || "") + " " + (vehicle.model || "")) + "'>"
              : "<div class='vehicle-public-image'></div>";
            return (
              "<div class='col-md-6 col-lg-4 mb-4'>" +
              "<article class='vehicle-public-card'>" +
              imageMarkup +
              "<div class='vehicle-public-body'>" +
              "<h3>" + escapeHtml(vehicle.brand) + " " + escapeHtml(vehicle.model) + "</h3>" +
              "<p class='vehicle-public-meta'>Year: " + escapeHtml(vehicle.year) + "</p>" +
              "<div class='vehicle-rate-stack'>" +
              "<span class='vehicle-rate-chip'>$" + formatCurrency(rates.hourRate) + " / car</span>" +
              "<span class='vehicle-rate-chip is-strong'>$" + formatCurrency(rates.dayRate) + " / car</span>" +
              "<span class='vehicle-rate-chip'>$" + formatCurrency(rates.monthRate) + " / car</span>" +
              "</div>" +
              "<p class='vehicle-public-desc'>" + escapeHtml(vehicle.description || "No description provided.") + "</p>" +
              "<div class='vehicle-card-actions'>" +
              "<a class='btn btn-primary py-2 px-3 mt-2' href='" + vehicleBookingLink + "'>Buy Now</a>" +
              "<a class='btn btn-secondary py-2 px-3 mt-2' href='" + vehicleDetailsLink + "'>Details</a>" +
              "</div>" +
              "</div>" +
              "</article>" +
              "</div>"
            );
          })
          .join("");

        if (feedback) {
          feedback.textContent = "Showing " + vehicles.length + " approved vehicles. " + lastUpdatedText();
        }
      } catch (error) {
        grid.innerHTML = "<div class='col-12'><p class='vehicle-empty'>Unable to load vehicles at the moment.</p></div>";
        if (feedback) {
          feedback.textContent = error.message;
        }
      }
    }

    wireSearch(loadVehicles);
    await loadVehicles("");
    window.setInterval(function () {
      const input = document.getElementById("vehicleSearchInput");
      loadVehicles(input ? input.value.trim() : "");
    }, HOURLY_REFRESH_MS);
  }

  async function initPricingPage() {
    const tableBody = document.getElementById("pricingVehiclesBody");
    const feedback = document.getElementById("pricingVehiclesFeedback");
    if (!tableBody) {
      return;
    }

    async function loadVehicles(query) {
      try {
        const authStatus = await getAuthStatus();
        const suffix = query ? "?query=" + encodeURIComponent(query) : "";
        const data = await requestJson("/api/vehicles" + suffix);
        const vehicles = data.vehicles || [];

        if (vehicles.length === 0) {
          tableBody.innerHTML = "<tr><td colspan='5' class='text-center'>No approved vehicles available.</td></tr>";
          if (feedback) {
            feedback.textContent = "";
          }
          return;
        }

        tableBody.innerHTML = vehicles
          .map(function (vehicle) {
            const rates = getRates(vehicle.pricePerDay);
            const src = imageSrc(vehicle.imageUrl);
            const vehicleBookingLink = resolveBookingEntryLink(vehicle.id, authStatus);
            const pricingImageMarkup = src
              ? "<img class='pricing-vehicle-image' src='" + escapeHtml(src) + "' alt='" + escapeHtml((vehicle.brand || "") + " " + (vehicle.model || "")) + "'>"
              : "<span class='pricing-no-image'>No image</span>";
            return (
              "<tr>" +
              "<td class='car-image'>" + pricingImageMarkup + "</td>" +
              "<td class='product-name'>" +
              "<h3>" + escapeHtml(vehicle.brand) + " " + escapeHtml(vehicle.model) + "</h3>" +
              "<p class='mb-0'>" + escapeHtml(vehicle.description || "Approved and ready for sale.") + "</p>" +
              "<p class='mb-0 mt-2'><a class='btn btn-primary py-1 px-3' href='" + vehicleBookingLink + "'>Buy Now</a></p>" +
              "</td>" +
              "</tr>"
            );
          })
          .join("");

        if (feedback) {
          feedback.textContent = "Showing " + vehicles.length + " approved vehicles. " + lastUpdatedText();
        }
      } catch (error) {
        tableBody.innerHTML = "<tr><td colspan='5' class='text-center'>Unable to load pricing data.</td></tr>";
        if (feedback) {
          feedback.textContent = error.message;
        }
      }
    }

    wireSearch(loadVehicles);
    await loadVehicles("");
    window.setInterval(function () {
      const input = document.getElementById("vehicleSearchInput");
      loadVehicles(input ? input.value.trim() : "");
    }, HOURLY_REFRESH_MS);
  }

  async function initVehicleDetails() {
    const detailSection = document.getElementById("vehicleDetailSection");
    const detailCard = document.getElementById("vehicleDetailCard");
    const reviewBody = document.getElementById("vehicleDetailReviewsBody");
    const feedback = document.getElementById("vehicleDetailFeedback");
    if (!detailSection || !detailCard || !reviewBody) {
      return;
    }

    const vehicleId = getVehicleIdFromUrl();
    if (!vehicleId) {
      detailSection.classList.add("d-none");
      return;
    }

    detailSection.classList.remove("d-none");

    async function loadDetails() {
      try {
        const authStatus = await getAuthStatus();
        const [vehicleData, reviewData] = await Promise.all([
          requestJson("/api/vehicles/" + vehicleId),
          requestJson("/api/reviews/vehicle/" + vehicleId)
        ]);

        const vehicle = vehicleData.vehicle;
        const reviews = reviewData.reviews || [];
        const rates = getRates(vehicle ? vehicle.pricePerDay : 0);
        const src = imageSrc(vehicle ? vehicle.imageUrl : "");
        const imageMarkup = src
          ? "<img class='vehicle-public-image-tag' src='" + escapeHtml(src) + "' alt='" + escapeHtml(((vehicle && vehicle.brand) || "") + " " + ((vehicle && vehicle.model) || "")) + "'>"
          : "<div class='vehicle-public-image'></div>";

        detailCard.innerHTML =
          "<article class='vehicle-public-card vehicle-detail-card'>" +
          imageMarkup +
          "<div class='vehicle-public-body'>" +
          "<h3>" + escapeHtml(vehicle.brand) + " " + escapeHtml(vehicle.model) + "</h3>" +
          "<p class='vehicle-public-meta'>Year: " + escapeHtml(vehicle.year) + "</p>" +
          "<div class='vehicle-rate-stack'>" +
          "<span class='vehicle-rate-chip'>$" + formatCurrency(rates.hourRate) + " / car</span>" +
          "<span class='vehicle-rate-chip is-strong'>$" + formatCurrency(rates.dayRate) + " / car</span>" +
          "<span class='vehicle-rate-chip'>$" + formatCurrency(rates.monthRate) + " / car</span>" +
          "</div>" +
          "<p class='vehicle-public-desc'>" + escapeHtml(vehicle.description || "No description provided.") + "</p>" +
          "<div class='vehicle-card-actions'>" +
          "<a class='btn btn-primary py-2 px-3' href='" + resolveBookingEntryLink(vehicle.id, authStatus) + "'>Buy Now</a>" +
          "</div>" +
          "</div>" +
          "</article>";

        if (!reviews.length) {
          reviewBody.innerHTML = "<tr><td colspan='4'>No reviews yet for this vehicle.</td></tr>";
        } else {
          reviewBody.innerHTML = reviews
            .map(function (review) {
              const updated = review.updatedAt ? String(review.updatedAt).replace("T", " ") : "-";
              return (
                "<tr>" +
                "<td><span class='vehicle-review-stars'>" + renderReviewStars(review.rating) + "</span> <span class='vehicle-review-rating'>" + Number(review.rating || 0) + "/5</span></td>" +
                "<td>" + escapeHtml(review.comment || "") + "</td>" +
                "<td>#" + Number(review.customerId || 0) + "</td>" +
                "<td>" + escapeHtml(updated) + "</td>" +
                "</tr>"
              );
            })
            .join("");
        }

        if (feedback) {
          feedback.textContent = "Showing " + reviews.length + " review(s). " + lastUpdatedText();
        }
      } catch (error) {
        detailCard.innerHTML = "<p class='vehicle-empty'>Unable to load vehicle details.</p>";
        reviewBody.innerHTML = "<tr><td colspan='4'>Unable to load reviews.</td></tr>";
        if (feedback) {
          feedback.textContent = error.message;
        }
      }
    }

    await loadDetails();
    window.setInterval(loadDetails, HOURLY_REFRESH_MS);
  }

  async function init() {
    await initHomeFeaturedVehicles();
    await initVehicleDetails();
    await initCarsPage();
    await initPricingPage();
  }

  init();
})();
