(() => {
  const tokenKey = "machi-next-token";
  const userKey = "machi-next-user";
  const authDialog = document.querySelector("#auth-dialog");
  const bookingDialog = document.querySelector("#booking-dialog");
  const grid = document.querySelector("#equipment-grid");
  const statusLine = document.querySelector("#catalog-status");
  const dateForm = document.querySelector("#availability-form");
  const startInput = document.querySelector("#start-date");
  const returnInput = document.querySelector("#return-date");
  const searchInput = document.querySelector("#equipment-search");
  const categoryInput = document.querySelector("#equipment-category");
  const authMessage = document.querySelector("#auth-message");
  const bookingMessage = document.querySelector("#booking-message");
  const toast = document.querySelector("#toast");
  let currentEquipment = [];
  let selectedEquipment = null;
  let toastTimer;

  const localDate = (date) => {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const day = String(date.getDate()).padStart(2, "0");
    return `${year}-${month}-${day}`;
  };

  const addDays = (date, days) => {
    const next = new Date(date);
    next.setDate(next.getDate() + days);
    return next;
  };

  const setDefaultDates = () => {
    const today = new Date();
    startInput.min = localDate(today);
    startInput.value = localDate(addDays(today, 1));
    returnInput.min = startInput.value;
    returnInput.value = localDate(addDays(today, 3));
  };

  const api = async (path, options = {}) => {
    const headers = new Headers(options.headers || {});
    if (options.body) headers.set("Content-Type", "application/json");
    const token = sessionStorage.getItem(tokenKey);
    if (token) headers.set("Authorization", `Bearer ${token}`);
    const response = await fetch(path, { ...options, headers });
    if (response.status === 204) return null;
    const payload = await response.json().catch(() => null);
    if (!response.ok) {
      if (response.status === 401 && token) clearSession();
      throw new Error(payload?.detail || payload?.message || "Ocurrió un error. Intenta de nuevo.");
    }
    return payload;
  };

  const money = (amount, currency = "COP") => new Intl.NumberFormat("es-CO", {
    style: "currency", currency, maximumFractionDigits: 0
  }).format(Number(amount || 0));

  const element = (tag, className, text) => {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined && text !== null) node.textContent = text;
    return node;
  };

  const setText = (selector, value) => { document.querySelector(selector).textContent = value; };

  const equipmentIcon = (equipment) => {
    const terms = `${equipment.categoryName} ${equipment.modelName}`.toLowerCase();
    if (/taladro|atornillador|rotomartillo/.test(terms)) return "🔩";
    if (/sierra|cortadora|pulidora/.test(terms)) return "🪚";
    if (/escalera|elevaci/.test(terms)) return "🪜";
    if (/medici|nivel|láser|laser/.test(terms)) return "📐";
    if (/compact|vibr/.test(terms)) return "🧰";
    return "🛠️";
  };

  const renderEquipment = (items) => {
    currentEquipment = items;
    grid.replaceChildren();
    document.querySelector("#catalog-count").textContent = `${items.length} ${items.length === 1 ? "equipo" : "equipos"}`;
    document.querySelector("#empty-state").hidden = items.length !== 0;
    if (items.length === 0) return;

    for (const item of items) {
      const equipment = item.equipment || item;
      const quote = item.quote || null;
      const currency = quote?.currency || equipment.currency || "COP";
      const card = element("article", "equipment-card");
      const art = element("div", "equipment-art");
      art.append(element("span", "equipment-id", `EQUIPO #${equipment.equipmentId}`));
      art.append(element("span", "equipment-glyph", equipmentIcon(equipment)));
      const info = element("div", "equipment-info");
      info.append(element("p", "equipment-category", equipment.categoryName));
      info.append(element("h3", "", equipment.modelName));
      info.append(element("p", "equipment-maker", `${equipment.manufacturer} · ${equipment.businessName}`));
      const priceRow = element("div", "price-row");
      const price = element("div", "price-main");
      price.append(document.createTextNode(money(quote ? quote.rentalAmount : equipment.dailyAmount, currency)));
      price.append(element("small", "", quote ? ` / ${quote.calendarDays} días` : " / día"));
      const deposit = element("div", "deposit");
      deposit.append(document.createTextNode("Depósito"));
      deposit.append(element("strong", "", money(quote ? quote.depositAmount : equipment.depositAmount, currency)));
      priceRow.append(price, deposit);
      info.append(priceRow);
      if (equipment.description) info.append(element("p", "equipment-maker", equipment.description));
      const reserve = element("button", "button button-primary", "Reservar equipo");
      reserve.type = "button";
      reserve.addEventListener("click", () => beginBooking(item));
      info.append(reserve);
      card.append(art, info);
      grid.append(card);
    }
  };

  const loadAvailability = async () => {
    grid.replaceChildren();
    document.querySelector("#empty-state").hidden = true;
    setText("#catalog-status", "Buscando equipos disponibles…");
    const params = new URLSearchParams({ startDate: startInput.value, returnDate: returnInput.value });
    if (searchInput.value.trim()) params.set("search", searchInput.value.trim());
    if (categoryInput.value.trim()) params.set("category", categoryInput.value.trim());
    try {
      const results = await api(`/api/v1/catalog/equipment/availability?${params}`);
      renderEquipment(results.equipment || []);
      setText("#catalog-status", `${results.calendarDays} días calendario · depósito separado del precio de renta`);
    } catch (error) {
      setText("#catalog-status", error.message);
      document.querySelector("#catalog-count").textContent = "";
    }
  };

  const showToast = (message, isError = false) => {
    toast.textContent = message;
    toast.classList.toggle("is-error", isError);
    toast.classList.add("is-visible");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => toast.classList.remove("is-visible"), 3400);
  };

  const showAuth = (mode = "login") => {
    authMessage.textContent = "";
    setAuthMode(mode);
    authDialog.showModal();
  };

  const setAuthMode = (mode) => {
    const isLogin = mode === "login";
    document.querySelector("#login-form").hidden = !isLogin;
    document.querySelector("#register-form").hidden = isLogin;
    document.querySelector("#login-tab").classList.toggle("is-active", isLogin);
    document.querySelector("#register-tab").classList.toggle("is-active", !isLogin);
    document.querySelector("#login-tab").setAttribute("aria-selected", String(isLogin));
    document.querySelector("#register-tab").setAttribute("aria-selected", String(!isLogin));
    document.querySelector("#auth-title").textContent = isLogin ? "Tu proyecto te espera" : "Crea tu cuenta gratis";
    authMessage.textContent = "";
  };

  const updateSessionUI = () => {
    const user = JSON.parse(sessionStorage.getItem(userKey) || "null");
    const loggedIn = Boolean(sessionStorage.getItem(tokenKey) && user);
    document.querySelector("#auth-open").hidden = loggedIn;
    document.querySelector("#sign-out").hidden = !loggedIn;
    const isCustomer = loggedIn && user.roles?.includes("CLIENTE");
    document.querySelector("#my-reservations-link").hidden = !isCustomer;
    document.querySelector("#my-reservations").hidden = !isCustomer;
    document.querySelector("#provider-dashboard-link").hidden = !loggedIn || !user.roles?.includes("PROVEEDOR");
    document.querySelector("#admin-dashboard-link").hidden = !loggedIn || !user.roles?.includes("ADMIN");
    document.querySelector("#business-dashboard-link").hidden = !loggedIn || !user.roles?.includes("BUSINESS_ADMIN");
    if (loggedIn) {
      document.querySelector("#sign-out").textContent = user.displayName || user.email;
    }
  };

  const saveSession = (auth) => {
    sessionStorage.setItem(tokenKey, auth.accessToken);
    sessionStorage.setItem(userKey, JSON.stringify({ userId: auth.userId, displayName: auth.displayName, email: auth.email, roles: auth.roles || [] }));
    updateSessionUI();
    authDialog.close();
    showToast(`¡Hola, ${auth.displayName}! Ya puedes reservar.`);
    loadReservations();
    if (selectedEquipment) openBooking(selectedEquipment);
  };

  const clearSession = () => {
    sessionStorage.removeItem(tokenKey);
    sessionStorage.removeItem(userKey);
    updateSessionUI();
    document.querySelector("#reservation-list").replaceChildren();
  };

  const beginBooking = (item) => {
    selectedEquipment = item;
    const user = JSON.parse(sessionStorage.getItem(userKey) || "null");
    if (!sessionStorage.getItem(tokenKey)) {
      showAuth("login");
      return;
    }
    if (!user?.roles?.includes("CLIENTE")) {
      showToast("Las reservas requieren una cuenta de cliente.", true);
      return;
    }
    openBooking(item);
  };

  const openBooking = (item) => {
    const equipment = item.equipment || item;
    const quote = item.quote || {};
    const summary = document.querySelector("#booking-summary");
    summary.replaceChildren();
    summary.append(element("h3", "", `${equipment.manufacturer} ${equipment.modelName}`));
    const addRow = (label, value, extraClass = "") => {
      const row = element("p", extraClass);
      row.append(element("span", "", label), element("strong", "", value));
      summary.append(row);
    };
    addRow("Negocio", equipment.businessName);
    addRow("Periodo", `${startInput.value} al ${returnInput.value}`);
    addRow(`Renta · ${quote.calendarDays || 0} días`, money(quote.rentalAmount, quote.currency));
    addRow("Depósito reembolsable", money(quote.depositAmount, quote.currency));
    addRow("Total al reservar", money(Number(quote.rentalAmount || 0) + Number(quote.depositAmount || 0), quote.currency), "booking-total");
    bookingMessage.textContent = "";
    bookingDialog.showModal();
  };

  const loadReservations = async () => {
    const user = JSON.parse(sessionStorage.getItem(userKey) || "null");
    if (!sessionStorage.getItem(tokenKey) || !user?.roles?.includes("CLIENTE")) return;
    const list = document.querySelector("#reservation-list");
    try {
      const reservations = await api("/api/v1/reservations/history");
      list.replaceChildren();
      if (!reservations.length) {
        list.append(element("p", "empty-reservations", "Aún no tienes reservas. Explora el catálogo para empezar."));
        return;
      }
      for (const reservation of reservations) {
        const item = element("article", "reservation-item");
        const details = element("div");
        details.append(element("h3", "", `Reserva #${reservation.reservationId} · ${reservation.manufacturer} ${reservation.modelName}`));
        details.append(element("p", "", `${reservation.businessName} · Equipo #${reservation.equipmentId}`));
        details.append(element("p", "", `${reservation.startDate} → ${reservation.returnDate} · ${reservation.status}`));
        if (reservation.handedOverAt) details.append(element("p", "", `Entregado: ${reservation.handedOverAt} · ${reservation.handedOverBy || "Proveedor"}`));
        if (reservation.returnedAt) details.append(element("p", "", `Devuelto: ${reservation.returnedAt} · ${reservation.returnCondition || "Condición sin datos"}`));
        const amount = element("div", "reservation-amount");
        amount.append(document.createTextNode(money(reservation.rentalAmount, reservation.currency)));
        amount.append(element("small", "", `Depósito: ${money(reservation.depositAmount, reservation.currency)}`));
        if (["CANCELLED", "REJECTED"].includes(reservation.status) && reservation.refundAmount !== null) {
          amount.append(element("small", "", `Reembolso simulado (${reservation.refundPercent}%): ${money(reservation.refundAmount, reservation.currency)}`));
        }
        item.append(details, amount);
        if (["PENDING_PROVIDER", "CONFIRMED"].includes(reservation.status)) {
          const cancelButton = element("button", "button button-outline reservation-cancel", "Cancelar reserva");
          cancelButton.type = "button";
          cancelButton.addEventListener("click", async () => {
            if (!window.confirm("¿Quieres cancelar esta reserva? El sistema calculará el reembolso según la anticipación.")) return;
            cancelButton.disabled = true;
            try {
              const cancellation = await api(`/api/v1/reservations/${reservation.reservationId}/cancel`, {
                method: "POST", body: JSON.stringify({ reason: "Cancelada por el cliente" })
              });
              showToast(`Solicitud cancelada. Reembolso simulado: ${money(cancellation.refundAmount, cancellation.currency)} (${cancellation.refundPercent}%).`);
              await loadReservations();
              await loadAvailability();
            } catch (error) { showToast(error.message, true); cancelButton.disabled = false; }
          });
          item.append(cancelButton);
        }
        const invoiceLink = element("a", "button button-outline reservation-cancel", "Ver comprobante");
        invoiceLink.href = `/invoice.html?reservationId=${reservation.reservationId}`;
        item.append(invoiceLink);
        list.append(item);
      }
    } catch (error) {
      list.replaceChildren(element("p", "empty-reservations", error.message));
    }
  };

  dateForm.addEventListener("submit", (event) => {
    event.preventDefault();
    if (returnInput.value < startInput.value) {
      setText("#catalog-status", "La fecha de devolución debe ser igual o posterior al inicio.");
      return;
    }
    returnInput.min = startInput.value;
    loadAvailability();
  });

  startInput.addEventListener("change", () => {
    returnInput.min = startInput.value;
    if (returnInput.value < startInput.value) returnInput.value = startInput.value;
  });

  document.querySelector("#auth-open").addEventListener("click", () => showAuth("login"));
  document.querySelector("#auth-close").addEventListener("click", () => authDialog.close());
  document.querySelector("#booking-close").addEventListener("click", () => bookingDialog.close());
  document.querySelector("#login-tab").addEventListener("click", () => setAuthMode("login"));
  document.querySelector("#register-tab").addEventListener("click", () => setAuthMode("register"));
  document.querySelector("#my-reservations-link").addEventListener("click", () => {
    document.querySelector("#my-reservations").scrollIntoView({ behavior: "smooth" });
    loadReservations();
  });
  document.querySelector("#sign-out").addEventListener("click", () => {
    clearSession();
    showToast("Sesión cerrada.");
  });

  document.querySelector("#login-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    authMessage.textContent = "Ingresando…";
    try {
      const auth = await api("/api/v1/auth/login", {
        method: "POST",
        body: JSON.stringify({ email: form.get("email"), password: form.get("password") })
      });
      saveSession(auth);
      event.currentTarget.reset();
    } catch (error) {
      authMessage.textContent = error.message;
    }
  });

  document.querySelector("#register-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    authMessage.textContent = "Creando tu cuenta…";
    try {
      const auth = await api("/api/v1/auth/register", {
        method: "POST",
        body: JSON.stringify({ email: form.get("email"), password: form.get("password"), displayName: form.get("displayName") })
      });
      saveSession(auth);
      event.currentTarget.reset();
    } catch (error) {
      authMessage.textContent = error.message;
    }
  });

  document.querySelector("#booking-confirm").addEventListener("click", async () => {
    if (!selectedEquipment) return;
    const equipment = selectedEquipment.equipment || selectedEquipment;
    const button = document.querySelector("#booking-confirm");
    button.disabled = true;
    bookingMessage.textContent = "Confirmando reserva…";
    try {
      const reservation = await api("/api/v1/reservations", {
        method: "POST",
        body: JSON.stringify({
          equipmentId: equipment.equipmentId,
          startDate: startInput.value,
          returnDate: returnInput.value
        })
      });
      bookingDialog.close();
      showToast(`Solicitud #${reservation.reservationId} enviada al proveedor.`);
      document.querySelector("#my-reservations").hidden = false;
      await loadAvailability();
      await loadReservations();
      document.querySelector("#my-reservations").scrollIntoView({ behavior: "smooth" });
    } catch (error) {
      bookingMessage.textContent = error.message;
    } finally {
      button.disabled = false;
    }
  });

  setDefaultDates();
  updateSessionUI();
  loadAvailability();
  loadReservations();
})();
