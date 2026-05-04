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
    var el = document.getElementById(id);
    if (!el) return;
    el.textContent = message || "";
    el.classList.remove("is-error", "is-success");
    if (type) el.classList.add(type === "error" ? "is-error" : "is-success");
  }

  function value(id) {
    var el = document.getElementById(id);
    return el ? el.value.trim() : "";
  }

  function escapeHtml(v) {
    return String(v || "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  function normalizeType(raw) {
    var t = String(raw || "").toLowerCase();
    return t === "dealer" ? "dealer" : "individual";
  }

  function wireSellerRegister() {
    var form = document.getElementById("sellerRegisterForm");
    if (!form) return;

    form.addEventListener("submit", function (e) {
      e.preventDefault();
      setFeedback("sellerRegisterFeedback", "", null);

      var payload = {
        name: value("sellerName"),
        contact: value("sellerContact"),
        email: value("sellerEmail"),
        location: value("sellerLocation"),
        type: normalizeType(value("sellerType")),
        image: value("sellerImage")
      };

      if (!payload.name || !payload.email) {
        setFeedback("sellerRegisterFeedback", "Name and email are required.", "error");
        return;
      }

      requestJson("/api/sellers/register", {
        method: "POST",
        body: JSON.stringify(payload)
      }).then(function () {
        setFeedback("sellerRegisterFeedback", "Registration successful! You can log in immediately.", "success");
        form.reset();
      }).catch(function (err) {
        setFeedback("sellerRegisterFeedback", err.message, "error");
      });
    });
  }

  function wireSellerList() {
    var tableBody = document.getElementById("sellerListBody");
    var searchBtn = document.getElementById("sellerSearchBtn");
    var nameInput = document.getElementById("sellerSearchName");
    var locationInput = document.getElementById("sellerSearchLocation");
    if (!tableBody) return;

    function load() {
      setFeedback("sellerListFeedback", "", null);
      var params = [];
      var n = nameInput ? nameInput.value.trim() : "";
      var l = locationInput ? locationInput.value.trim() : "";
      if (n) params.push("name=" + encodeURIComponent(n));
      if (l) params.push("location=" + encodeURIComponent(l));

      var url = "/api/sellers/search" + (params.length ? "?" + params.join("&") : "");
      requestJson(url, { method: "GET" })
        .then(function (data) {
          var sellers = data.sellers || [];
          if (!sellers.length) {
            tableBody.innerHTML = "<tr><td colspan='6'>No sellers found.</td></tr>";
            return;
          }
          tableBody.innerHTML = sellers.map(function (s) {
            var badge = s.isApproved ? "<span class='vehicle-status status-approved'>APPROVED</span>" : "<span class='vehicle-status status-pending'>PENDING</span>";
            return "<tr>"
              + "<td>" + (s.id || "") + "</td>"
              + "<td>" + escapeHtml(s.name || "") + "</td>"
              + "<td>" + escapeHtml(s.type || "") + "</td>"
              + "<td>" + escapeHtml(s.location || "") + "</td>"
              + "<td>" + escapeHtml(s.contact || "") + "</td>"
              + "<td>" + badge + "</td>"
              + "</tr>";
          }).join("");
        })
        .catch(function (err) {
          tableBody.innerHTML = "<tr><td colspan='6'>Unable to load sellers.</td></tr>";
          setFeedback("sellerListFeedback", err.message, "error");
        });
    }

    if (searchBtn) {
      searchBtn.addEventListener("click", load);
    }

    load();
  }

  function getSellerIdFromUrl() {
    var params = new URLSearchParams(window.location.search);
    var raw = params.get("sellerId");
    if (!raw) return null;
    var id = Number(raw);
    return Number.isFinite(id) && id > 0 ? id : null;
  }

  function wireSellerEdit() {
    var form = document.getElementById("sellerEditForm");
    if (!form) return;

    var sellerId = getSellerIdFromUrl();
    if (!sellerId) {
      setFeedback("sellerEditFeedback", "Missing sellerId in URL (example: seller-edit.html?sellerId=1).", "error");
      return;
    }

    document.getElementById("sellerId").value = String(sellerId);

    function loadSeller() {
      setFeedback("sellerEditFeedback", "", null);
      requestJson("/api/sellers/" + encodeURIComponent(sellerId), { method: "GET" })
        .then(function (data) {
          var s = data.seller || {};
          document.getElementById("sellerEditName").value = s.name || "";
          document.getElementById("sellerEditContact").value = s.contact || "";
          document.getElementById("sellerEditLocation").value = s.location || "";
          document.getElementById("sellerEditType").value = normalizeType(s.type);
          document.getElementById("sellerEditImage").value = s.image || "";
        })
        .catch(function (err) {
          setFeedback("sellerEditFeedback", err.message, "error");
        });
    }

    loadSeller();

    form.addEventListener("submit", function (e) {
      e.preventDefault();
      setFeedback("sellerEditFeedback", "", null);

      var payload = {
        name: value("sellerEditName"),
        contact: value("sellerEditContact"),
        location: value("sellerEditLocation"),
        type: normalizeType(value("sellerEditType")),
        image: value("sellerEditImage")
      };

      if (!payload.name) {
        setFeedback("sellerEditFeedback", "Name is required.", "error");
        return;
      }

      requestJson("/api/sellers/" + encodeURIComponent(sellerId), {
        method: "PUT",
        body: JSON.stringify(payload)
      }).then(function () {
        setFeedback("sellerEditFeedback", "Seller updated.", "success");
        loadSeller();
      }).catch(function (err) {
        setFeedback("sellerEditFeedback", err.message, "error");
      });
    });
  }

  wireSellerRegister();
  wireSellerList();
  wireSellerEdit();
})();

