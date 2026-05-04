(function () {
  "use strict";

  async function requestJson(url, options) {
    const response = await fetch(url, {
      credentials: "same-origin",
      headers: {
        "Content-Type": "application/json"
      },
      ...options
    });

    let body = {};
    try {
      body = await response.json();
    } catch (_) {
      body = {};
    }

    if (!response.ok) {
      throw new Error(body.message || "Request failed.");
    }

    return body;
  }

  function setFeedback(element, message, type) {
    if (!element) {
      return;
    }
    element.textContent = message || "";
    element.classList.remove("is-error", "is-success");
    if (type) {
      element.classList.add(type === "error" ? "is-error" : "is-success");
    }
  }

  function valueById(id) {
    const element = document.getElementById(id);
    return element ? element.value.trim() : "";
  }

  function escapeHtml(value) {
    return String(value || "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/\"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  async function getAuthStatus() {
    try {
      return await requestJson("/api/auth/status", { method: "GET" });
    } catch (_) {
      return null;
    }
  }

  function redirectForRole(role) {
    const normalizedRole = (role || "").toUpperCase();
    if (normalizedRole === "ADMIN" || normalizedRole === "SUPERADMIN") {
      window.location.href = "admin-dashboard.html";
      return;
    }
    if (normalizedRole === "SELLER" || normalizedRole === "DEALER" || normalizedRole === "RENTER") {
      window.location.href = "renter-dashboard.html";
      return;
    }
    if (normalizedRole === "BUYER" || normalizedRole === "USER") {
      window.location.href = "account.html";
      return;
    }
    window.location.href = "account.html";
  }

  async function handleLoginPage() {
    const form = document.getElementById("loginForm");
    const feedback = document.getElementById("authFeedback");
    const googleBtn = document.getElementById("googleSigninBtn");

    if (googleBtn) {
      googleBtn.addEventListener("click", function () {
        setFeedback(feedback, "Google Sign-in is not configured yet.", "error");
      });
    }

    if (!form) {
      return;
    }

    const authStatus = await getAuthStatus();
    if (authStatus && authStatus.authenticated) {
      redirectForRole(authStatus.role);
      return;
    }

    form.addEventListener("submit", async function (event) {
      event.preventDefault();
      setFeedback(feedback, "", null);

      try {
        const payload = {
          email: valueById("email"),
          password: valueById("password")
        };

        const data = await requestJson("/api/auth/login", {
          method: "POST",
          body: JSON.stringify(payload)
        });

        setFeedback(feedback, "Login successful. Redirecting...", "success");
        if (data && data.redirectPage) {
          window.location.href = data.redirectPage;
        } else {
          redirectForRole(data ? data.role : "USER");
        }
      } catch (error) {
        setFeedback(feedback, error.message, "error");
      }
    });
  }

  async function handleSignupPage() {
    const form = document.getElementById("signupForm");
    const feedback = document.getElementById("signupFeedback");

    if (!form) {
      return;
    }

    const authStatus = await getAuthStatus();
    if (authStatus && authStatus.authenticated) {
      redirectForRole(authStatus.role);
      return;
    }

    form.addEventListener("submit", async function (event) {
      event.preventDefault();
      setFeedback(feedback, "", null);

      try {
        const phone = valueById("phone");
        if (!/^\d{10}$/.test(phone)) {
          setFeedback(feedback, "Phone number must contain exactly 10 digits.", "error");
          return;
        }

        const payload = {
          name: valueById("fullName"),
          phone: phone,
          address: valueById("address"),
          email: valueById("signupEmail"),
          password: valueById("signupPassword"),
          role: (document.getElementById("signupRole") ? document.getElementById("signupRole").value : "BUYER")
        };

        const data = await requestJson("/api/auth/register", {
          method: "POST",
          body: JSON.stringify(payload)
        });

        setFeedback(feedback, "Account created. Redirecting...", "success");
        if (data && data.redirectPage) {
          window.location.href = data.redirectPage;
        } else {
          window.location.href = "account.html";
        }
      } catch (error) {
        setFeedback(feedback, error.message, "error");
      }
    });
  }

  async function handleAccountPage() {
    const accountForm = document.getElementById("accountForm");
    const feedback = document.getElementById("accountFeedback");
    const saveBtn = document.getElementById("saveAccountBtn");
    const editBtn = document.getElementById("editAccountBtn");
    const deleteBtn = document.getElementById("deleteAccountBtn");
    const logoutBtn = document.getElementById("logoutBtn");

    if (!accountForm) {
      return;
    }

    const authStatus = await getAuthStatus();
    if (!authStatus || !authStatus.authenticated) {
      window.location.href = "login.html";
      return;
    }
    const normalizedRole = (authStatus.role || "").toUpperCase();
    if (normalizedRole === "ADMIN" || normalizedRole === "SUPERADMIN") {
      window.location.href = "admin-dashboard.html";
      return;
    }

    function setEditMode(enabled) {
      const editableFields = accountForm.querySelectorAll("input[data-editable='true'], textarea[data-editable='true']");
      editableFields.forEach(function (field) {
        field.disabled = !enabled;
      });
      if (saveBtn) {
        saveBtn.disabled = !enabled;
      }
      if (editBtn) {
        editBtn.textContent = enabled ? "Cancel" : "Edit Details";
      }
      accountForm.dataset.editMode = enabled ? "on" : "off";
    }

    async function loadUser() {
      try {
        const data = await requestJson("/api/account/me", { method: "GET" });
        const user = data.user || {};
        document.getElementById("accountName").textContent = user.name || "User";
        document.getElementById("accountNameField").value = user.name || "";
        document.getElementById("accountEmailField").value = user.email || "";
        document.getElementById("accountPhoneField").value = user.phone || "";
        document.getElementById("accountAddressField").value = user.address || "";
        const meta = document.getElementById("accountMeta");
        if (meta) {
          meta.textContent = user.email || "";
        }
        setEditMode(false);
      } catch (error) {
        window.location.href = "login.html";
      }
    }

    if (editBtn) {
      editBtn.addEventListener("click", function () {
        const editModeOn = accountForm.dataset.editMode === "on";
        setEditMode(!editModeOn);
        setFeedback(feedback, "", null);
      });
    }

    accountForm.addEventListener("submit", async function (event) {
      event.preventDefault();
      setFeedback(feedback, "", null);

      try {
        const payload = {
          name: valueById("accountNameField"),
          email: valueById("accountEmailField"),
          phone: valueById("accountPhoneField"),
          address: valueById("accountAddressField"),
          password: valueById("accountPasswordField")
        };

        await requestJson("/api/account/me", {
          method: "PUT",
          body: JSON.stringify(payload)
        });

        document.getElementById("accountPasswordField").value = "";
        setEditMode(false);
        setFeedback(feedback, "Account details updated successfully.", "success");
        await loadUser();
      } catch (error) {
        setFeedback(feedback, error.message, "error");
      }
    });

    if (deleteBtn) {
      deleteBtn.addEventListener("click", async function () {
        const confirmed = window.confirm("Delete your account permanently? This action cannot be undone.");
        if (!confirmed) {
          return;
        }

        try {
          await requestJson("/api/account/me", { method: "DELETE" });
          window.location.href = "signup.html";
        } catch (error) {
          setFeedback(feedback, error.message, "error");
        }
      });
    }

    if (logoutBtn) {
      logoutBtn.addEventListener("click", async function () {
        try {
          await requestJson("/api/auth/logout", { method: "POST", body: "{}" });
        } catch (_) {
          // Ignore logout errors and redirect to login page anyway.
        }
        window.location.href = "login.html";
      });
    }

    await loadUser();
  }

  async function handleAdminDashboardPage() {
    const authStatus = await getAuthStatus();
    if (!authStatus || !authStatus.authenticated) {
      window.location.href = "login.html";
      return;
    }

    const normalizedRole = (authStatus.role || "").toUpperCase();
    if (normalizedRole !== "ADMIN" && normalizedRole !== "SUPERADMIN") {
      window.location.href = "renter-dashboard.html";
      return;
    }

    const adminProfileName = document.getElementById("adminProfileName");
    const adminProfileMeta = document.getElementById("adminProfileMeta");
    const adminAccountForm = document.getElementById("adminAccountForm");
    const adminAccountFeedback = document.getElementById("adminAccountFeedback");
    const adminCreateFeedback = document.getElementById("adminCreateFeedback");
    const adminListFeedback = document.getElementById("adminListFeedback");
    const userListFeedback = document.getElementById("userListFeedback");
    const createAdminForm = document.getElementById("createAdminForm");
    const adminsTableBody = document.getElementById("adminsTableBody");
    const usersTableBody = document.getElementById("usersTableBody");
    const adminVehiclesTableBody = document.getElementById("adminVehiclesTableBody");
    const adminReviewsTableBody = document.getElementById("adminReviewsTableBody");
    const adminSearchInput = document.getElementById("adminSearchInput");
    const userSearchInput = document.getElementById("userSearchInput");
    const vehicleAdminSearchInput = document.getElementById("vehicleAdminSearchInput");
    const reviewAdminSearchInput = document.getElementById("reviewAdminSearchInput");
    const adminSearchBtn = document.getElementById("adminSearchBtn");
    const adminReloadBtn = document.getElementById("adminReloadBtn");
    const userSearchBtn = document.getElementById("userSearchBtn");
    const userReloadBtn = document.getElementById("userReloadBtn");
    const vehicleAdminSearchBtn = document.getElementById("vehicleAdminSearchBtn");
    const vehicleAdminReloadBtn = document.getElementById("vehicleAdminReloadBtn");
    const reviewAdminSearchBtn = document.getElementById("reviewAdminSearchBtn");
    const reviewAdminReloadBtn = document.getElementById("reviewAdminReloadBtn");
    const adminVehicleListFeedback = document.getElementById("adminVehicleListFeedback");
    const adminReviewListFeedback = document.getElementById("adminReviewListFeedback");
    const adminLogoutBtn = document.getElementById("adminLogoutBtn");

    if (adminProfileName) {
      adminProfileName.textContent = (authStatus.admin && authStatus.admin.name) || "Admin";
    }
    if (adminProfileMeta) {
      adminProfileMeta.textContent = (authStatus.admin && authStatus.admin.email) || authStatus.role;
    }

    async function loadAdminAccount() {
      if (!adminAccountForm) {
        return;
      }
      const data = await requestJson("/api/admin/me", { method: "GET" });
      const admin = data && data.admin ? data.admin : {};

      const nameField = document.getElementById("adminAccountNameField");
      const emailField = document.getElementById("adminAccountEmailField");
      const passwordField = document.getElementById("adminAccountPasswordField");
      const confirmField = document.getElementById("adminAccountConfirmPasswordField");

      if (nameField) {
        nameField.value = admin.name || "";
      }
      if (emailField) {
        emailField.value = admin.email || "";
      }
      if (passwordField) {
        passwordField.value = "";
      }
      if (confirmField) {
        confirmField.value = "";
      }

      if (adminProfileName) {
        adminProfileName.textContent = admin.name || "Admin";
      }
      if (adminProfileMeta) {
        adminProfileMeta.textContent = admin.email || authStatus.role;
      }
    }

    function renderAdminRows(admins) {
      if (!adminsTableBody) {
        return;
      }
      if (!admins || admins.length === 0) {
        adminsTableBody.innerHTML = "<tr><td colspan='5'>No admins found.</td></tr>";
        return;
      }

      adminsTableBody.innerHTML = admins
        .map(function (admin) {
          return (
            "<tr>" +
            "<td>" + (admin.adminId || "") + "</td>" +
            "<td>" + (admin.name || "") + "</td>" +
            "<td>" + (admin.email || "") + "</td>" +
            "<td>" + (admin.role || "") + "</td>" +
            "<td><button type='button' class='account-btn-danger admin-delete-btn' data-admin-id='" +
            (admin.adminId || "") +
            "'>Delete</button></td>" +
            "</tr>"
          );
        })
        .join("");

      adminsTableBody.querySelectorAll(".admin-delete-btn").forEach(function (button) {
        button.addEventListener("click", async function () {
          const adminId = button.getAttribute("data-admin-id");
          const currentAdminId = authStatus.admin ? authStatus.admin.adminId : "";
          if (adminId === currentAdminId) {
            setFeedback(adminListFeedback, "You cannot delete your own active admin account here.", "error");
            return;
          }
          const confirmed = window.confirm("Delete admin " + adminId + "?");
          if (!confirmed) {
            return;
          }
          try {
            await requestJson("/api/admin/admins/" + encodeURIComponent(adminId), { method: "DELETE" });
            setFeedback(adminListFeedback, "Admin deleted.", "success");
            await loadAdmins();
          } catch (error) {
            setFeedback(adminListFeedback, error.message, "error");
          }
        });
      });
    }

    function renderUserRows(users) {
      if (!usersTableBody) {
        return;
      }
      if (!users || users.length === 0) {
        usersTableBody.innerHTML = "<tr><td colspan='6'>No users found.</td></tr>";
        return;
      }

      usersTableBody.innerHTML = users
        .map(function (user) {
          const userType = String(user.userType || user.role || "CLIENT").toUpperCase();
          const userTypeClass = userType === "SELLER" ? "user-type-renter" : "user-type-client";
          return (
            "<tr>" +
            "<td>" + (user.id || "") + "</td>" +
            "<td>" + (user.name || "") + "</td>" +
            "<td>" + (user.email || "") + "</td>" +
            "<td>" + (user.phone || "") + "</td>" +
            "<td><span class='user-type-pill " + userTypeClass + "'>" + userType + "</span></td>" +
            "<td><button type='button' class='account-btn-danger user-delete-btn' data-user-id='" +
            (user.id || "") +
            "'>Delete</button></td>" +
            "</tr>"
          );
        })
        .join("");

      usersTableBody.querySelectorAll(".user-delete-btn").forEach(function (button) {
        button.addEventListener("click", async function () {
          const userId = button.getAttribute("data-user-id");
          const confirmed = window.confirm("Delete user ID " + userId + "?");
          if (!confirmed) {
            return;
          }
          try {
            await requestJson("/api/admin/users/" + encodeURIComponent(userId), { method: "DELETE" });
            setFeedback(userListFeedback, "User deleted.", "success");
            await loadUsers(userSearchInput ? userSearchInput.value.trim() : "");
          } catch (error) {
            setFeedback(userListFeedback, error.message, "error");
          }
        });
      });
    }

    function renderStatusBadge(status) {
      const normalized = (status || "PENDING").toUpperCase();
      const className = normalized === "APPROVED" ? "status-approved" : normalized === "REJECTED" ? "status-rejected" : "status-pending";
      return "<span class='vehicle-status " + className + "'>" + normalized + "</span>";
    }

    function renderVehicleRows(vehicles) {
      if (!adminVehiclesTableBody) {
        return;
      }
      if (!vehicles || vehicles.length === 0) {
        adminVehiclesTableBody.innerHTML = "<tr><td colspan='7'>No vehicles found.</td></tr>";
        return;
      }

      adminVehiclesTableBody.innerHTML = vehicles
        .map(function (vehicle) {
          return (
            "<tr>" +
            "<td>" + (vehicle.id || "") + "</td>" +
            "<td>" + (vehicle.renterId || "") + "</td>" +
            "<td>" + escapeHtml((vehicle.brand || "") + " " + (vehicle.model || "")) + "</td>" +
            "<td>" + (vehicle.year || "") + "</td>" +
            "<td>$" + Number(vehicle.pricePerDay || 0).toFixed(0) + "</td>" +
            "<td>" + renderStatusBadge(vehicle.status) + "</td>" +
            "<td><button type='button' class='account-btn-danger admin-vehicle-delete-btn' data-vehicle-id='" +
            (vehicle.id || "") +
            "'>Delete</button></td>" +
            "</tr>"
          );
        })
        .join("");

      adminVehiclesTableBody.querySelectorAll(".admin-vehicle-delete-btn").forEach(function (button) {
        button.addEventListener("click", async function () {
          const vehicleId = button.getAttribute("data-vehicle-id");
          const confirmed = window.confirm("Delete vehicle ID " + vehicleId + "? This cannot be undone.");
          if (!confirmed) {
            return;
          }
          try {
            await requestJson("/api/admin/vehicles/" + encodeURIComponent(vehicleId), { method: "DELETE" });
            setFeedback(adminVehicleListFeedback, "Vehicle removed.", "success");
            await loadAdminVehicles(vehicleAdminSearchInput ? vehicleAdminSearchInput.value.trim() : "");
          } catch (error) {
            setFeedback(adminVehicleListFeedback, error.message, "error");
          }
        });
      });
    }

    function renderReviewRows(reviews) {
      if (!adminReviewsTableBody) {
        return;
      }
      if (!reviews || reviews.length === 0) {
        adminReviewsTableBody.innerHTML = "<tr><td colspan='6'>No reviews found.</td></tr>";
        return;
      }

      adminReviewsTableBody.innerHTML = reviews
        .map(function (review) {
          return (
            "<tr>" +
            "<td>" + (review.id || "") + "</td>" +
            "<td>" + escapeHtml(review.vehicleName || ("Vehicle #" + (review.vehicleId || ""))) + "</td>" +
            "<td>" + (review.rating || "") + "/5</td>" +
            "<td>" + escapeHtml(review.comment || "") + "</td>" +
            "<td>" + (review.customerId || "") + "</td>" +
            "<td><button type='button' class='account-btn-danger admin-review-delete-btn' data-review-id='" +
            (review.id || "") +
            "'>Delete</button></td>" +
            "</tr>"
          );
        })
        .join("");

      adminReviewsTableBody.querySelectorAll(".admin-review-delete-btn").forEach(function (button) {
        button.addEventListener("click", async function () {
          const reviewId = button.getAttribute("data-review-id");
          const confirmed = window.confirm("Delete review ID " + reviewId + "? This cannot be undone.");
          if (!confirmed) {
            return;
          }
          try {
            await requestJson("/api/admin/reviews/" + encodeURIComponent(reviewId), { method: "DELETE" });
            setFeedback(adminReviewListFeedback, "Review removed.", "success");
            await loadAdminReviews(reviewAdminSearchInput ? reviewAdminSearchInput.value.trim() : "");
          } catch (error) {
            setFeedback(adminReviewListFeedback, error.message, "error");
          }
        });
      });
    }

    async function loadAdmins(adminIdQuery) {
      try {
        if (adminIdQuery) {
          const data = await requestJson("/api/admin/admins/" + encodeURIComponent(adminIdQuery), { method: "GET" });
          renderAdminRows(data.admin ? [data.admin] : []);
        } else {
          const data = await requestJson("/api/admin/admins", { method: "GET" });
          renderAdminRows(data.admins || []);
        }
        setFeedback(adminListFeedback, "", null);
      } catch (error) {
        renderAdminRows([]);
        setFeedback(adminListFeedback, error.message, "error");
      }
    }

    async function loadUsers(query) {
      try {
        const suffix = query ? "?query=" + encodeURIComponent(query) : "";
        const data = await requestJson("/api/admin/users" + suffix, { method: "GET" });
        renderUserRows(data.users || []);
        setFeedback(userListFeedback, "", null);
      } catch (error) {
        renderUserRows([]);
        setFeedback(userListFeedback, error.message, "error");
      }
    }

    async function loadAdminVehicles(query) {
      try {
        const suffix = query ? "?query=" + encodeURIComponent(query) : "";
        const data = await requestJson("/api/admin/vehicles" + suffix, { method: "GET" });
        renderVehicleRows(data.vehicles || []);
        setFeedback(adminVehicleListFeedback, "", null);
      } catch (error) {
        renderVehicleRows([]);
        setFeedback(adminVehicleListFeedback, error.message, "error");
      }
    }

    async function loadAdminReviews(query) {
      try {
        const suffix = query ? "?query=" + encodeURIComponent(query) : "";
        const data = await requestJson("/api/admin/reviews" + suffix, { method: "GET" });
        renderReviewRows(data.reviews || []);
        setFeedback(adminReviewListFeedback, "", null);
      } catch (error) {
        renderReviewRows([]);
        setFeedback(adminReviewListFeedback, error.message, "error");
      }
    }

    if (createAdminForm) {
      createAdminForm.addEventListener("submit", async function (event) {
        event.preventDefault();
        setFeedback(adminCreateFeedback, "", null);

        const name = valueById("adminNameInput");
        const email = valueById("adminEmailInput");
        const password = valueById("adminPasswordInput");
        const confirmPassword = valueById("adminConfirmPasswordInput");
        const role = valueById("adminRoleInput");

        if (name.length < 2) {
          setFeedback(adminCreateFeedback, "Admin name must be at least 2 characters.", "error");
          return;
        }
        if (!/^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/.test(email)) {
          setFeedback(adminCreateFeedback, "Enter a valid admin email address.", "error");
          return;
        }
        if (password.length < 8) {
          setFeedback(adminCreateFeedback, "Password must be at least 8 characters.", "error");
          return;
        }
        if (!/[A-Za-z]/.test(password) || !/\d/.test(password)) {
          setFeedback(adminCreateFeedback, "Password must include at least one letter and one number.", "error");
          return;
        }
        if (password !== confirmPassword) {
          setFeedback(adminCreateFeedback, "Password and confirm password do not match.", "error");
          return;
        }

        try {
          const response = await requestJson("/api/admin/admins", {
            method: "POST",
            body: JSON.stringify({
              name: name,
              email: email,
              password: password,
              role: role
            })
          });
          createAdminForm.reset();
          const newAdminId = response && response.admin ? response.admin.adminId : "";
          setFeedback(
            adminCreateFeedback,
            "Admin created successfully" + (newAdminId ? " (" + newAdminId + ")" : "") + ". They can now login with email and password.",
            "success"
          );
          await loadAdmins();
        } catch (error) {
          setFeedback(adminCreateFeedback, error.message, "error");
        }
      });
    }

    if (adminAccountForm) {
      adminAccountForm.addEventListener("submit", async function (event) {
        event.preventDefault();
        setFeedback(adminAccountFeedback, "", null);

        const name = valueById("adminAccountNameField");
        const email = valueById("adminAccountEmailField");
        const password = valueById("adminAccountPasswordField");
        const confirmPassword = valueById("adminAccountConfirmPasswordField");

        if (name.length < 2) {
          setFeedback(adminAccountFeedback, "Name must be at least 2 characters.", "error");
          return;
        }
        if (!/^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/.test(email)) {
          setFeedback(adminAccountFeedback, "Enter a valid email address.", "error");
          return;
        }
        if (password && password.length < 8) {
          setFeedback(adminAccountFeedback, "New password must be at least 8 characters.", "error");
          return;
        }
        if (password && (!/[A-Za-z]/.test(password) || !/\d/.test(password))) {
          setFeedback(adminAccountFeedback, "New password must include at least one letter and one number.", "error");
          return;
        }
        if (password !== confirmPassword) {
          setFeedback(adminAccountFeedback, "New password and confirm password do not match.", "error");
          return;
        }

        try {
          await requestJson("/api/admin/me", {
            method: "PUT",
            body: JSON.stringify({
              name: name,
              email: email,
              password: password
            })
          });
          setFeedback(adminAccountFeedback, "Admin account updated successfully.", "success");
          await loadAdminAccount();
        } catch (error) {
          setFeedback(adminAccountFeedback, error.message, "error");
        }
      });
    }

    if (adminSearchBtn) {
      adminSearchBtn.addEventListener("click", async function () {
        const query = adminSearchInput ? adminSearchInput.value.trim() : "";
        await loadAdmins(query);
      });
    }
    if (adminReloadBtn) {
      adminReloadBtn.addEventListener("click", async function () {
        if (adminSearchInput) {
          adminSearchInput.value = "";
        }
        await loadAdmins();
      });
    }
    if (userSearchBtn) {
      userSearchBtn.addEventListener("click", async function () {
        const query = userSearchInput ? userSearchInput.value.trim() : "";
        await loadUsers(query);
      });
    }
    if (userReloadBtn) {
      userReloadBtn.addEventListener("click", async function () {
        if (userSearchInput) {
          userSearchInput.value = "";
        }
        await loadUsers("");
      });
    }
    if (vehicleAdminSearchBtn) {
      vehicleAdminSearchBtn.addEventListener("click", async function () {
        const query = vehicleAdminSearchInput ? vehicleAdminSearchInput.value.trim() : "";
        await loadAdminVehicles(query);
      });
    }
    if (vehicleAdminReloadBtn) {
      vehicleAdminReloadBtn.addEventListener("click", async function () {
        if (vehicleAdminSearchInput) {
          vehicleAdminSearchInput.value = "";
        }
        await loadAdminVehicles("");
      });
    }
    if (reviewAdminSearchBtn) {
      reviewAdminSearchBtn.addEventListener("click", async function () {
        const query = reviewAdminSearchInput ? reviewAdminSearchInput.value.trim() : "";
        await loadAdminReviews(query);
      });
    }
    if (reviewAdminReloadBtn) {
      reviewAdminReloadBtn.addEventListener("click", async function () {
        if (reviewAdminSearchInput) {
          reviewAdminSearchInput.value = "";
        }
        await loadAdminReviews("");
      });
    }
    if (adminLogoutBtn) {
      adminLogoutBtn.addEventListener("click", async function () {
        try {
          await requestJson("/api/auth/logout", { method: "POST", body: "{}" });
        } catch (_) {
          // Ignore logout errors and continue.
        }
        window.location.href = "login.html";
      });
    }

    await loadAdminAccount();
    await loadAdmins();
    await loadUsers("");
    await loadAdminVehicles("");
    await loadAdminReviews("");
  }

  async function handleRenterDashboardPage() {
    const authStatus = await getAuthStatus();
    if (!authStatus || !authStatus.authenticated) {
      window.location.href = "login.html";
      return;
    }

    const normalizedRole = (authStatus.role || "").toUpperCase();
    if (normalizedRole === "ADMIN" || normalizedRole === "SUPERADMIN") {
      window.location.href = "admin-dashboard.html";
      return;
    }

    const renterProfileName = document.getElementById("renterProfileName");
    const renterProfileMeta = document.getElementById("renterProfileMeta");
    const renterLogoutBtn = document.getElementById("renterLogoutBtn");
    const vehicleForm = document.getElementById("vehicleForm");
    const vehicleFeedback = document.getElementById("vehicleFeedback");
    const vehicleListFeedback = document.getElementById("vehicleListFeedback");
    const vehicleTableBody = document.getElementById("renterVehiclesTableBody");
    const vehicleSubmitBtn = document.getElementById("vehicleSubmitBtn");
    const vehicleClearBtn = document.getElementById("vehicleClearBtn");
    const vehicleYearField = document.getElementById("vehicleYearField");
    const vehiclePriceField = document.getElementById("vehiclePriceField");
    const currentYear = new Date().getFullYear();

    if (vehicleYearField) {
      vehicleYearField.setAttribute("max", String(currentYear));
    }

    if (renterProfileName) {
      renterProfileName.textContent = (authStatus.user && authStatus.user.name) || "Seller";
    }
    if (renterProfileMeta) {
      renterProfileMeta.textContent = (authStatus.user && authStatus.user.email) || "SELLER";
    }

    function fillVehicleForm(vehicle) {
      document.getElementById("vehicleIdField").value = vehicle ? String(vehicle.id || "") : "";
      document.getElementById("vehicleBrandField").value = vehicle ? vehicle.brand || "" : "";
      document.getElementById("vehicleModelField").value = vehicle ? vehicle.model || "" : "";
      document.getElementById("vehicleYearField").value = vehicle ? vehicle.year || "" : "";
      document.getElementById("vehiclePriceField").value = vehicle ? vehicle.pricePerDay || "" : "";
      document.getElementById("vehicleImageField").value = vehicle ? vehicle.imageUrl || "" : "";
      document.getElementById("vehicleDescriptionField").value = vehicle ? vehicle.description || "" : "";
      if (vehicleSubmitBtn) {
        vehicleSubmitBtn.textContent = vehicle ? "Update Listing" : "Submit Listing";
      }
    }

    function renderStatusBadge(status) {
      const normalized = (status || "PENDING").toUpperCase();
      const className = normalized === "APPROVED" ? "status-approved" : normalized === "REJECTED" ? "status-rejected" : "status-pending";
      return "<span class='vehicle-status " + className + "'>" + normalized + "</span>";
    }

    function renderVehicleRows(vehicles) {
      if (!vehicleTableBody) {
        return;
      }
      if (!vehicles || vehicles.length === 0) {
        vehicleTableBody.innerHTML = "<tr><td colspan='7'>You have not submitted any vehicles yet.</td></tr>";
        return;
      }

      vehicleTableBody.innerHTML = vehicles
        .map(function (vehicle) {
          return (
            "<tr>" +
            "<td>" + (vehicle.brand || "") + "</td>" +
            "<td>" + (vehicle.model || "") + "</td>" +
            "<td>" + (vehicle.year || "") + "</td>" +
            "<td>$" + Number(vehicle.pricePerDay || 0).toFixed(2) + "</td>" +
            "<td>" + renderStatusBadge(vehicle.status) + "</td>" +
            "<td><button type='button' class='account-btn-secondary vehicle-edit-btn' data-id='" + (vehicle.id || "") + "'>Edit</button></td>" +
            "<td><button type='button' class='account-btn-danger vehicle-delete-btn' data-id='" + (vehicle.id || "") + "'>Delete</button></td>" +
            "</tr>"
          );
        })
        .join("");

      vehicleTableBody.querySelectorAll(".vehicle-edit-btn").forEach(function (button) {
        button.addEventListener("click", function () {
          const id = Number(button.getAttribute("data-id"));
          const target = vehicles.find(function (vehicle) {
            return Number(vehicle.id) === id;
          });
          fillVehicleForm(target || null);
          setFeedback(vehicleFeedback, "Editing car listing " + id + ". Update and submit.", "success");
        });
      });

      vehicleTableBody.querySelectorAll(".vehicle-delete-btn").forEach(function (button) {
        button.addEventListener("click", async function () {
          const id = button.getAttribute("data-id");
          const confirmed = window.confirm("Delete this vehicle listing?");
          if (!confirmed) {
            return;
          }
          try {
            await requestJson("/api/vehicles/" + encodeURIComponent(id), { method: "DELETE" });
            setFeedback(vehicleListFeedback, "Vehicle deleted successfully.", "success");
            fillVehicleForm(null);
            await loadMyVehicles();
          } catch (error) {
            setFeedback(vehicleListFeedback, error.message, "error");
          }
        });
      });
    }

    async function loadMyVehicles() {
      try {
        const data = await requestJson("/api/vehicles/mine", { method: "GET" });
        renderVehicleRows(data.vehicles || []);
        setFeedback(vehicleListFeedback, "", null);
      } catch (error) {
        renderVehicleRows([]);
        setFeedback(vehicleListFeedback, error.message, "error");
      }
    }

    if (vehicleForm) {
      vehicleForm.addEventListener("submit", async function (event) {
        event.preventDefault();
        setFeedback(vehicleFeedback, "", null);

        const vehicleId = valueById("vehicleIdField");
        const payload = {
          brand: valueById("vehicleBrandField"),
          model: valueById("vehicleModelField"),
          year: Number(valueById("vehicleYearField") || 0),
          pricePerDay: Number(valueById("vehiclePriceField") || 0),
          description: valueById("vehicleDescriptionField"),
          imageUrl: valueById("vehicleImageField")
        };

        if (!Number.isFinite(payload.year) || payload.year < 1990 || payload.year > currentYear) {
          setFeedback(vehicleFeedback, "Vehicle year cannot be in the future.", "error");
          return;
        }
        if (!Number.isFinite(payload.pricePerDay) || payload.pricePerDay <= 0) {
          setFeedback(vehicleFeedback, "Price per day must be greater than 0.", "error");
          return;
        }

        if (vehiclePriceField) {
          vehiclePriceField.value = payload.pricePerDay.toFixed(2);
        }

        try {
          if (vehicleId) {
            await requestJson("/api/vehicles/" + encodeURIComponent(vehicleId), {
              method: "PUT",
              body: JSON.stringify(payload)
            });
            setFeedback(vehicleFeedback, "Vehicle updated and sent for review.", "success");
          } else {
            await requestJson("/api/vehicles", {
              method: "POST",
              body: JSON.stringify(payload)
            });
            setFeedback(vehicleFeedback, "Vehicle submitted for approval.", "success");
          }
          fillVehicleForm(null);
          await loadMyVehicles();
        } catch (error) {
          setFeedback(vehicleFeedback, error.message, "error");
        }
      });
    }

    if (vehicleClearBtn) {
      vehicleClearBtn.addEventListener("click", function () {
        fillVehicleForm(null);
        setFeedback(vehicleFeedback, "Form cleared.", "success");
      });
    }

    if (renterLogoutBtn) {
      renterLogoutBtn.addEventListener("click", async function () {
        try {
          await requestJson("/api/auth/logout", { method: "POST", body: "{}" });
        } catch (_) {
          // Ignore logout errors.
        }
        window.location.href = "login.html";
      });
    }

    await loadMyVehicles();
  }

  async function handleAdminApprovalsPage() {
    const authStatus = await getAuthStatus();
    if (!authStatus || !authStatus.authenticated) {
      window.location.href = "login.html";
      return;
    }

    const normalizedRole = (authStatus.role || "").toUpperCase();
    if (normalizedRole !== "ADMIN" && normalizedRole !== "SUPERADMIN") {
      window.location.href = "renter-dashboard.html";
      return;
    }

    const adminProfileName = document.getElementById("adminApprovalsName");
    const adminProfileMeta = document.getElementById("adminApprovalsMeta");
    const approvalsFeedback = document.getElementById("approvalsFeedback");
    const approvalsTableBody = document.getElementById("pendingVehiclesTableBody");
    const adminLogoutBtn = document.getElementById("adminApprovalsLogoutBtn");

    if (adminProfileName) {
      adminProfileName.textContent = (authStatus.admin && authStatus.admin.name) || "Admin";
    }
    if (adminProfileMeta) {
      adminProfileMeta.textContent = (authStatus.admin && authStatus.admin.email) || authStatus.role;
    }

    function renderRows(vehicles) {
      if (!approvalsTableBody) {
        return;
      }
      if (!vehicles || vehicles.length === 0) {
        approvalsTableBody.innerHTML = "<tr><td colspan='7'>No pending vehicles for approval.</td></tr>";
        return;
      }

      approvalsTableBody.innerHTML = vehicles
        .map(function (vehicle) {
          return (
            "<tr>" +
            "<td>" + (vehicle.id || "") + "</td>" +
            "<td>" + (vehicle.renterId || "") + "</td>" +
            "<td>" + (vehicle.brand || "") + "</td>" +
            "<td>" + (vehicle.model || "") + "</td>" +
            "<td>" + (vehicle.year || "") + "</td>" +
            "<td>$" + Number(vehicle.pricePerDay || 0).toFixed(2) + "</td>" +
            "<td class='approval-actions'>" +
            "<button type='button' class='account-btn-primary approval-approve-btn' data-id='" + (vehicle.id || "") + "'>Approve</button>" +
            "<button type='button' class='account-btn-danger approval-reject-btn' data-id='" + (vehicle.id || "") + "'>Reject</button>" +
            "</td>" +
            "</tr>"
          );
        })
        .join("");

      approvalsTableBody.querySelectorAll(".approval-approve-btn").forEach(function (button) {
        button.addEventListener("click", async function () {
          const id = button.getAttribute("data-id");
          try {
            await requestJson("/api/vehicles/admin/" + encodeURIComponent(id) + "/approve", { method: "POST", body: "{}" });
            setFeedback(approvalsFeedback, "Vehicle " + id + " approved.", "success");
            await loadPendingVehicles();
          } catch (error) {
            setFeedback(approvalsFeedback, error.message, "error");
          }
        });
      });

      approvalsTableBody.querySelectorAll(".approval-reject-btn").forEach(function (button) {
        button.addEventListener("click", async function () {
          const id = button.getAttribute("data-id");
          try {
            await requestJson("/api/vehicles/admin/" + encodeURIComponent(id) + "/reject", { method: "POST", body: "{}" });
            setFeedback(approvalsFeedback, "Vehicle " + id + " rejected.", "success");
            await loadPendingVehicles();
          } catch (error) {
            setFeedback(approvalsFeedback, error.message, "error");
          }
        });
      });
    }

    async function loadPendingVehicles() {
      try {
        const data = await requestJson("/api/vehicles/admin/pending", { method: "GET" });
        renderRows(data.vehicles || []);
      } catch (error) {
        renderRows([]);
        setFeedback(approvalsFeedback, error.message, "error");
      }
    }

    if (adminLogoutBtn) {
      adminLogoutBtn.addEventListener("click", async function () {
        try {
          await requestJson("/api/auth/logout", { method: "POST", body: "{}" });
        } catch (_) {
          // Ignore logout errors.
        }
        window.location.href = "login.html";
      });
    }

    await loadPendingVehicles();
  }

  async function init() {
    if (document.body.classList.contains("auth-page-login")) {
      await handleLoginPage();
    }
    if (document.body.classList.contains("auth-page-signup")) {
      await handleSignupPage();
    }
    if (document.body.classList.contains("auth-page-account")) {
      await handleAccountPage();
    }
    if (document.body.classList.contains("auth-page-admin")) {
      await handleAdminDashboardPage();
    }
    if (document.body.classList.contains("auth-page-renter-dashboard")) {
      await handleRenterDashboardPage();
    }
    if (document.body.classList.contains("auth-page-admin-approvals")) {
      await handleAdminApprovalsPage();
    }
  }

  init();
})();
