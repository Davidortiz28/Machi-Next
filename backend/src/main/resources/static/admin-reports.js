(() => {
  const token = sessionStorage.getItem("machi-next-token");
  const user = JSON.parse(sessionStorage.getItem("machi-next-user") || "null");
  const status = document.querySelector("#overview-status");
  const content = document.querySelector("#overview-content");
  const refreshButton = document.querySelector("#refresh-overview");

  const node = (tag, text, className) => {
    const item = document.createElement(tag);
    if (text !== undefined && text !== null) item.textContent = text;
    if (className) item.className = className;
    return item;
  };
  const money = (amount, currency = "COP") => new Intl.NumberFormat("es-CO", {
    style: "currency", currency, maximumFractionDigits: 0
  }).format(Number(amount || 0));
  const api = async () => {
    const response = await fetch("/api/v1/admin/reports/overview", {
      headers: { Authorization: `Bearer ${token}` }
    });
    const payload = await response.json().catch(() => null);
    if (!response.ok) throw new Error(payload?.detail || payload?.message || "No se pudo cargar el resumen global.");
    return payload;
  };
  const makeTable = (headers, rows) => {
    const table = document.createElement("table");
    table.className = "overview-table";
    const thead = document.createElement("thead");
    const headerRow = document.createElement("tr");
    headers.forEach((label) => headerRow.append(node("th", label)));
    thead.append(headerRow);
    const tbody = document.createElement("tbody");
    rows.forEach((values) => {
      const row = document.createElement("tr");
      values.forEach((value) => row.append(node("td", value)));
      tbody.append(row);
    });
    table.append(thead, tbody);
    return table;
  };
  const addTableSection = (container, title, headers, rows, emptyMessage) => {
    const section = node("section", null, "overview-section");
    section.append(node("h2", title));
    if (!rows.length) section.append(node("p", emptyMessage, "overview-empty"));
    else {
      const wrap = node("div", null, "overview-table-wrap");
      wrap.append(makeTable(headers, rows));
      section.append(wrap);
    }
    container.append(section);
  };

  const render = (overview) => {
    content.replaceChildren();
    const inventory = overview.inventory;
    const inventorySection = node("section", null, "overview-section");
    inventorySection.append(node("h2", "Inventario y disponibilidad"));
    const metrics = node("div", null, "overview-metrics");
    [
      [inventory.totalEquipment, "Equipos registrados"],
      [inventory.operationalAvailable, "Operativos"],
      [inventory.availableToday, "Disponibles hoy"],
      [inventory.inMaintenance, "En mantenimiento"],
      [inventory.retired, "Retirados"]
    ].forEach(([value, label]) => {
      const metric = node("div", null, "overview-metric");
      metric.append(node("strong", String(value)), node("span", label));
      metrics.append(metric);
    });
    inventorySection.append(metrics);
    content.append(inventorySection);

    const activity = node("section", null, "overview-section");
    activity.append(node("h2", "Actividad de la plataforma"));
    const activityMetrics = node("div", null, "overview-metrics");
    const statusLabels = {
      PENDING_PROVIDER: "Pendientes del proveedor", CONFIRMED: "Confirmadas",
      IN_RENTAL: "En alquiler", COMPLETED: "Finalizadas",
      REJECTED: "Rechazadas", CANCELLED: "Canceladas"
    };
    const reservationCount = overview.reservationsByStatus.reduce((sum, row) => sum + Number(row.total), 0);
    [[overview.registeredUsers, "Usuarios registrados"], [reservationCount, "Reservas totales"]]
      .concat(overview.reservationsByStatus.map((row) => [row.total, statusLabels[row.status] || row.status]))
      .forEach(([value, label]) => {
        const metric = node("div", null, "overview-metric");
        metric.append(node("strong", String(value)), node("span", label));
        activityMetrics.append(metric);
      });
    activity.append(activityMetrics);
    content.append(activity);

    const tables = node("div", null, "overview-tables");
    addTableSection(tables, "Ingresos simulados por renta", ["Moneda", "Cobrado", "Reembolsado", "Neto"],
      overview.rentIncomeByCurrency.map((row) => [row.currency, money(row.rentCharged, row.currency), money(row.rentRefunded, row.currency), money(row.netRentIncome, row.currency)]),
      "Todavía no hay operaciones de renta exitosas.");
    addTableSection(tables, "Equipos más alquilados", ["Equipo", "Alquileres"],
      overview.mostRentedEquipment.map((row) => [`${row.manufacturer} ${row.modelName}`, String(row.rentalCount)]),
      "Todavía no hay alquileres registrados.");
    addTableSection(tables, "Clientes frecuentes", ["Cliente", "Alquileres"],
      overview.frequentCustomers.map((row) => [row.displayName, String(row.rentalCount)]),
      "Todavía no hay clientes con alquileres registrados.");
    content.append(tables);
    content.append(node("p", "Los importes corresponden a pagos simulados del proyecto académico; no representan transacciones bancarias reales.", "overview-note"));
  };

  const load = async () => {
    refreshButton.disabled = true;
    status.textContent = "Cargando resumen global…";
    content.replaceChildren();
    try {
      render(await api());
      status.textContent = "Resumen actualizado.";
    } catch (error) {
      status.textContent = error.message;
      content.append(node("p", error.message, "overview-error"));
    } finally {
      refreshButton.disabled = false;
    }
  };

  if (!token || !user?.roles?.includes("ADMIN")) {
    status.textContent = "Inicia sesión con una cuenta de administrador para consultar estos reportes.";
    refreshButton.disabled = true;
  } else {
    refreshButton.addEventListener("click", load);
    load();
  }
})();
