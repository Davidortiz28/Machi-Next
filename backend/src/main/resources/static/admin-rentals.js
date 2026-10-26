(() => {
  const token = sessionStorage.getItem("machi-next-token");
  const user = JSON.parse(sessionStorage.getItem("machi-next-user") || "null");
  const status = document.querySelector("#rentals-status");
  const list = document.querySelector("#rental-list");
  const search = document.querySelector("#rental-search");
  const statusFilter = document.querySelector("#rental-status-filter");
  const refresh = document.querySelector("#refresh-rentals");
  let rentals = [];
  const labels = {
    PENDING_PROVIDER: "Pendiente del proveedor", CONFIRMED: "Confirmada",
    IN_RENTAL: "En alquiler", COMPLETED: "Finalizada", REJECTED: "Rechazada", CANCELLED: "Cancelada"
  };
  const node = (tag, text, className) => {
    const item = document.createElement(tag);
    if (text !== undefined && text !== null) item.textContent = text;
    if (className) item.className = className;
    return item;
  };
  const money = (amount, currency) => new Intl.NumberFormat("es-CO", {
    style: "currency", currency: currency || "COP", maximumFractionDigits: 0
  }).format(Number(amount || 0));
  const date = (value) => value ? new Date(value).toLocaleString("es-CO") : "Sin registrar";
  const api = async () => {
    const response = await fetch("/api/v1/admin/rentals", { headers: { Authorization: `Bearer ${token}` } });
    const payload = await response.json().catch(() => null);
    if (!response.ok) throw new Error(payload?.detail || payload?.message || "No se pudo cargar el historial.");
    return payload;
  };
  const detail = (container, label, value) => {
    const item = node("div", null, "rental-detail");
    item.append(node("span", label), node("strong", value));
    container.append(item);
  };
  const render = () => {
    const query = search.value.trim().toLocaleLowerCase("es-CO");
    const filtered = rentals.filter((rental) => {
      const matchesStatus = !statusFilter.value || rental.status === statusFilter.value;
      const haystack = [rental.reservationId, rental.businessName, rental.customerName, rental.equipmentId,
        rental.manufacturer, rental.modelName, rental.returnCondition].join(" ").toLocaleLowerCase("es-CO");
      return matchesStatus && haystack.includes(query);
    });
    status.textContent = `${filtered.length} de ${rentals.length} ${rentals.length === 1 ? "registro" : "registros"}`;
    list.replaceChildren();
    if (!filtered.length) {
      list.append(node("p", rentals.length ? "No hay alquileres que coincidan con los filtros." : "Todavía no hay alquileres registrados.", "rentals-empty"));
      return;
    }
    filtered.forEach((rental) => {
      const card = node("article", null, "rental-card");
      const header = node("div", null, "rental-card-header");
      const title = node("div");
      title.append(node("h2", `Reserva #${rental.reservationId} · ${rental.manufacturer} ${rental.modelName}`));
      title.append(node("p", `${rental.businessName} · Equipo #${rental.equipmentId} · Cliente: ${rental.customerName}`));
      header.append(title, node("span", labels[rental.status] || rental.status, "rental-status"));
      const details = node("div", null, "rental-details");
      detail(details, "Período reservado", `${rental.startDate} a ${rental.returnDate}`);
      detail(details, "Renta · depósito", `${money(rental.rentalAmount, rental.currency)} · ${money(rental.depositAmount, rental.currency)}`);
      detail(details, "Entrega registrada", date(rental.handedOverAt));
      detail(details, "Devuelto", date(rental.returnedAt));
      detail(details, "Condición al devolver", rental.returnCondition || "Sin registrar");
      detail(details, "Entregó", rental.handedOverBy || "Sin registrar");
      detail(details, "Recibió devolución", rental.returnedTo || "Sin registrar");
      card.append(header, details);
      list.append(card);
    });
  };
  const load = async () => {
    refresh.disabled = true;
    status.textContent = "Cargando historial…";
    list.replaceChildren();
    try { rentals = await api(); render(); }
    catch (error) { rentals = []; status.textContent = error.message; list.append(node("p", error.message, "rentals-error")); }
    finally { refresh.disabled = false; }
  };
  if (!token || !user?.roles?.includes("ADMIN")) {
    status.textContent = "Inicia sesión en el catálogo con una cuenta de administrador para consultar el historial.";
    refresh.disabled = true;
  } else {
    search.addEventListener("input", render);
    statusFilter.addEventListener("change", render);
    refresh.addEventListener("click", load);
    load();
  }
})();
