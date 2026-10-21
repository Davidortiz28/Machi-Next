(() => {
  const token = sessionStorage.getItem("machi-next-token");
  const user = JSON.parse(sessionStorage.getItem("machi-next-user") || "null");
  const status = document.querySelector("#reports-status");
  const container = document.querySelector("#business-reports");
  const money = (amount, currency) => new Intl.NumberFormat("es-CO", { style: "currency", currency, maximumFractionDigits: 0 }).format(Number(amount || 0));
  const node = (tag, text, className) => { const item = document.createElement(tag); if (text !== undefined && text !== null) item.textContent = text; if (className) item.className = className; return item; };
  const api = async () => {
    const response = await fetch("/api/v1/business/reports", { headers: { Authorization: `Bearer ${token}` } });
    const data = await response.json().catch(() => null);
    if (!response.ok) throw new Error(data?.detail || data?.message || "No se pudieron cargar los reportes.");
    return data;
  };

  const table = (headers, rows) => {
    const result = document.createElement("table"); result.className = "money-table";
    const thead = document.createElement("thead"); const hr = document.createElement("tr");
    for (const text of headers) hr.append(node("th", text)); thead.append(hr);
    const tbody = document.createElement("tbody");
    for (const values of rows) { const row = document.createElement("tr"); for (const value of values) row.append(node("td", value)); tbody.append(row); }
    result.append(thead, tbody); return result;
  };

  const render = (reports) => {
    container.replaceChildren();
    if (!reports.length) { container.append(node("p", "Tu cuenta no tiene negocios asignados para mostrar.", "empty-report")); return; }
    for (const report of reports) {
      const card = node("article", null, "business-report");
      const title = node("div", null, "report-title");
      const heading = document.createElement("div"); heading.append(node("h2", report.businessName), node("p", `Negocio #${report.businessId}`));
      title.append(heading, node("span", report.businessStatus === "ACTIVE" ? "Activo" : report.businessStatus, `business-status${report.businessStatus === "ACTIVE" ? "" : " inactive"}`));
      card.append(title);
      const metrics = node("div", null, "metric-grid");
      const values = [[report.equipmentCount, "Equipos"], [report.availableEquipment, "Disponibles"], [report.maintenanceEquipment, "En mantenimiento"], [report.retiredEquipment, "Retirados"], [report.reservationCount, "Reservas totales"], [report.confirmedReservations, "Confirmadas"], [report.activeRentals, "Alquileres activos"], [report.completedRentals, "Alquileres finalizados"]];
      for (const [value, label] of values) { const metric = node("div", null, "metric"); metric.append(node("strong", String(value)), node("span", label)); metrics.append(metric); }
      card.append(metrics);
      const income = node("section", null, "money-section"); income.append(node("h3", "Ingresos de renta simulados"));
      if (report.rentIncomeByCurrency.length) {
        income.append(table(["Moneda", "Cobrado", "Reembolsado", "Neto"], report.rentIncomeByCurrency.map((row) => [row.currency, money(row.rentCharged, row.currency), money(row.rentRefunded, row.currency), money(row.netRentIncome, row.currency)])));
      } else income.append(node("p", "Aún no hay cobros de renta registrados."));
      card.append(income);
      const deposits = node("section", null, "money-section"); deposits.append(node("h3", "Depósitos pendientes"));
      if (report.pendingDepositsByCurrency.length) deposits.append(table(["Moneda", "Monto pendiente"], report.pendingDepositsByCurrency.map((row) => [row.currency, money(row.pendingDeposits, row.currency)])));
      else deposits.append(node("p", "No hay depósitos pendientes de liquidación."));
      card.append(deposits);
      const rankings = node("div", null, "ranking-grid");
      const equipment = node("section", null, "money-section"); equipment.append(node("h3", "Equipos más alquilados"));
      if (report.mostRentedEquipment.length) equipment.append(table(["Equipo", "Alquileres"], report.mostRentedEquipment.map((row) => [`${row.manufacturer} ${row.modelName} (#${row.equipmentId})`, String(row.rentalCount)])));
      else equipment.append(node("p", "Aún no hay equipos alquilados."));
      const customers = node("section", null, "money-section"); customers.append(node("h3", "Clientes frecuentes"));
      if (report.frequentCustomers.length) customers.append(table(["Cliente", "Alquileres"], report.frequentCustomers.map((row) => [row.displayName, String(row.rentalCount)])));
      else customers.append(node("p", "Aún no hay clientes con alquileres registrados."));
      rankings.append(equipment, customers); card.append(rankings);
      container.append(card);
    }
  };

  const load = async () => {
    status.textContent = "Cargando reportes…";
    try { render(await api()); status.textContent = "Importes académicos simulados; los ingresos son de renta y no incluyen depósitos."; }
    catch (error) { status.textContent = error.message; }
  };
  if (!token || !user?.roles?.includes("BUSINESS_ADMIN")) status.textContent = "Inicia sesión con una cuenta representante de negocio para consultar reportes.";
  else { document.querySelector("#refresh-reports").addEventListener("click", load); load(); }
})();
