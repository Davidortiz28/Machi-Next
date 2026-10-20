(() => {
  const tokenKey = "machi-next-token";
  const userKey = "machi-next-user";
  const form = document.querySelector("#equipment-form");
  const list = document.querySelector("#inventory-list");
  const status = document.querySelector("#inventory-status");
  const rentalList = document.querySelector("#rental-list");
  const rentalsStatus = document.querySelector("#rentals-status");
  const formMessage = document.querySelector("#form-message");
  const user = JSON.parse(sessionStorage.getItem(userKey) || "null");
  const token = sessionStorage.getItem(tokenKey);
  const userId = Number(user?.userId);

  const api = async (path, options = {}) => {
    const response = await fetch(path, { ...options, headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}`, ...(options.headers || {}) } });
    const body = await response.json().catch(() => null);
    if (!response.ok) throw new Error(body?.detail || body?.message || "No fue posible completar la operación.");
    return body;
  };
  const money = (amount, currency) => new Intl.NumberFormat("es-CO", { style: "currency", currency: currency || "COP", maximumFractionDigits: 0 }).format(Number(amount || 0));
  const node = (tag, text, className) => { const el = document.createElement(tag); el.textContent = text; if (className) el.className = className; return el; };

  const loadInventory = async () => {
    status.textContent = "Cargando inventario…";
    list.replaceChildren();
    try {
      const items = await api("/api/v1/provider/equipment");
      status.textContent = `${items.length} ${items.length === 1 ? "equipo registrado" : "equipos registrados"}`;
      for (const item of items) {
        const card = node("article", null, "inventory-card");
        const info = document.createElement("div");
        info.append(node("h3", `${item.manufacturer} ${item.modelName}`));
        info.append(node("p", `${item.categoryName} · Serie ${item.serialNumber} · ${item.businessName}`));
        info.append(node("p", `${money(item.dailyAmount, item.currency)} / día · Depósito ${money(item.depositAmount, item.currency)}`));
        const select = document.createElement("select");
        select.setAttribute("aria-label", `Estado de ${item.modelName}`);
        for (const [value, label] of [["AVAILABLE", "Disponible"], ["MAINTENANCE", "En mantenimiento"], ["RETIRED", "Retirado"]]) {
          const option = document.createElement("option"); option.value = value; option.textContent = label; option.selected = item.operationalStatus === value; select.append(option);
        }
        select.addEventListener("change", async () => {
          select.disabled = true;
          try { await api(`/api/v1/provider/equipment/${item.equipmentId}/status`, { method: "PATCH", body: JSON.stringify({ status: select.value }) }); }
          catch (error) { status.textContent = error.message; select.value = item.operationalStatus; }
          finally { select.disabled = false; }
        });
        card.append(info, select); list.append(card);
      }
    } catch (error) { status.textContent = error.message; }
  };

  const loadRentals = async () => {
    rentalsStatus.textContent = "Cargando reservas…";
    rentalList.replaceChildren();
    try {
      const rentals = await api("/api/v1/provider/rentals");
      rentalsStatus.textContent = `${rentals.length} ${rentals.length === 1 ? "reserva" : "reservas"}`;
      for (const rental of rentals) {
        const card = node("article", null, "inventory-card");
        const info = document.createElement("div");
        info.append(node("h3", `Reserva #${rental.reservationId} · ${rental.manufacturer} ${rental.modelName}`));
        info.append(node("p", `Cliente: ${rental.customerName} · Equipo #${rental.equipmentId}`));
        info.append(node("p", `${rental.startDate} → ${rental.returnDate} · ${rental.calendarDays} días · ${rental.status}`));
        info.append(node("p", `Renta ${money(rental.rentalAmount, rental.currency)} · Depósito ${money(rental.depositAmount, rental.currency)}`));
        if (rental.status === "CONFIRMED") {
          const handover = node("button", "Registrar entrega", "button button-primary");
          handover.type = "button";
          handover.addEventListener("click", async () => {
            handover.disabled = true;
            try { await api(`/api/v1/rentals/${rental.reservationId}/handover`, { method: "POST" }); await loadRentals(); }
            catch (error) { rentalsStatus.textContent = error.message; handover.disabled = false; }
          });
          card.append(info, handover);
        } else if (rental.status === "IN_RENTAL") {
          const form = document.createElement("form"); form.className = "return-form";
          const select = document.createElement("select"); select.setAttribute("aria-label", "Condición del equipo al devolverlo");
          for (const [value, label] of [["GOOD", "Sin daños"], ["PARTIAL_DAMAGE", "Daño parcial"], ["TOTAL_DAMAGE", "Daño total"]]) {
            const option = node("option", label); option.value = value; select.append(option);
          }
          const submit = node("button", "Registrar devolución", "button button-outline"); submit.type = "submit";
          form.append(select, submit);
          form.addEventListener("submit", async (event) => {
            event.preventDefault(); submit.disabled = true;
            try { await api(`/api/v1/rentals/${rental.reservationId}/return`, { method: "POST", body: JSON.stringify({ condition: select.value }) }); await loadRentals(); }
            catch (error) { rentalsStatus.textContent = error.message; submit.disabled = false; }
          });
          card.append(info, form);
        } else {
          if (rental.returnCondition && rental.returnCondition !== "GOOD") info.append(node("p", `Daño reportado: ${rental.returnCondition}. El depósito queda pendiente de revisión.`));
          if (rental.returnCondition && rental.returnCondition !== "GOOD" && rental.handedOverByUserId === userId) {
            const evidence = document.createElement("form"); evidence.className = "evidence-form";
            const file = document.createElement("input"); file.type = "file"; file.accept = "image/jpeg,image/png,video/mp4,application/pdf"; file.required = true; file.setAttribute("aria-label", "Evidencia del daño");
            const upload = node("button", "Adjuntar evidencia", "button button-outline"); upload.type = "submit";
            evidence.append(file, upload);
            evidence.addEventListener("submit", async (event) => {
              event.preventDefault(); upload.disabled = true;
              const data = new FormData(); data.append("file", file.files[0]);
              try {
                const response = await fetch(`/api/v1/rentals/${rental.reservationId}/damage-evidence`, { method: "POST", headers: { Authorization: `Bearer ${token}` }, body: data });
                const result = await response.json().catch(() => null);
                if (!response.ok) throw new Error(result?.detail || result?.message || "No se pudo adjuntar la evidencia.");
                rentalsStatus.textContent = "Evidencia adjuntada."; await loadRentals();
              } catch (error) { rentalsStatus.textContent = error.message; upload.disabled = false; }
            });
            card.append(info, evidence);
          } else card.append(info);
        }
        rentalList.append(card);
      }
    } catch (error) { rentalsStatus.textContent = error.message; }
  };

  if (!token || !user?.roles?.includes("PROVEEDOR")) {
    form.hidden = true;
    document.querySelectorAll(".provider-panel").forEach((panel) => { panel.hidden = true; });
    status.textContent = "Inicia sesión con una cuenta de proveedor asignada a un negocio para administrar su inventario.";
    rentalsStatus.textContent = status.textContent;
  } else {
    form.elements.validFrom.value = new Date().toLocaleDateString("en-CA");
    form.addEventListener("submit", async (event) => {
      event.preventDefault();
      const data = Object.fromEntries(new FormData(form));
      data.dailyAmount = Number(data.dailyAmount); data.depositAmount = Number(data.depositAmount); data.currency = "COP";
      formMessage.textContent = "Guardando…";
      try { await api("/api/v1/provider/equipment", { method: "POST", body: JSON.stringify(data) }); form.reset(); form.elements.validFrom.value = new Date().toLocaleDateString("en-CA"); formMessage.textContent = "Equipo registrado."; await loadInventory(); }
      catch (error) { formMessage.textContent = error.message; }
    });
    document.querySelector("#reload-inventory").addEventListener("click", loadInventory);
    document.querySelector("#reload-rentals").addEventListener("click", loadRentals);
    loadInventory();
    loadRentals();
  }
})();
