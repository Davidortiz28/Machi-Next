(() => {
  const tokenKey = "machi-next-token";
  const userKey = "machi-next-user";
  const form = document.querySelector("#equipment-form");
  const list = document.querySelector("#inventory-list");
  const status = document.querySelector("#inventory-status");
  const formMessage = document.querySelector("#form-message");
  const user = JSON.parse(sessionStorage.getItem(userKey) || "null");
  const token = sessionStorage.getItem(tokenKey);

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

  if (!token || !user?.roles?.includes("PROVEEDOR")) {
    form.hidden = true;
    document.querySelectorAll(".provider-panel").forEach((panel) => { panel.hidden = true; });
    status.textContent = "Inicia sesión con una cuenta de proveedor asignada a un negocio para administrar su inventario.";
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
    loadInventory();
  }
})();
