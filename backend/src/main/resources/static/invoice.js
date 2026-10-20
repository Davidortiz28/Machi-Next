(() => {
  const token = sessionStorage.getItem("machi-next-token");
  const user = JSON.parse(sessionStorage.getItem("machi-next-user") || "null");
  const status = document.querySelector("#invoice-status");
  const sheet = document.querySelector("#invoice-sheet");
  const printButton = document.querySelector("#print-invoice");
  const labels = { RENT_CHARGE: "Cobro de renta", DEPOSIT_CHARGE: "Cobro de depósito", RENT_REFUND: "Reembolso de renta", DEPOSIT_REFUND: "Reembolso de depósito", DEPOSIT_RETENTION: "Retención de depósito" };
  const money = (amount, currency) => new Intl.NumberFormat("es-CO", { style: "currency", currency: currency || "COP", minimumFractionDigits: 0, maximumFractionDigits: 2 }).format(Number(amount || 0));
  const node = (tag, text, className) => { const item = document.createElement(tag); if (text !== undefined && text !== null) item.textContent = text; if (className) item.className = className; return item; };
  const addSection = (parent, className, title) => { const section = node("section", null, className); section.append(node("h2", title)); parent.append(section); return section; };

  const load = async () => {
    if (!token || !user?.roles?.includes("CLIENTE")) { status.textContent = "Inicia sesión con la cuenta cliente que hizo la reserva para ver el comprobante."; return; }
    const reservationId = new URLSearchParams(window.location.search).get("reservationId");
    if (!reservationId || !/^\d+$/.test(reservationId)) { status.textContent = "No se indicó una reserva válida."; return; }
    try {
      const response = await fetch(`/api/v1/reservations/${reservationId}/invoice`, { headers: { Authorization: `Bearer ${token}` } });
      const invoice = await response.json().catch(() => null);
      if (!response.ok) throw new Error(invoice?.detail || invoice?.message || "No se pudo cargar el comprobante.");
      sheet.replaceChildren();
      const top = node("div", null, "invoice-top");
      const brand = document.createElement("div"); brand.append(node("p", "COMPROBANTE ACADÉMICO", "eyebrow"), node("h1", invoice.businessName), node("p", `Comprobante ${invoice.invoiceNumber}`, "invoice-number"));
      const state = node("div", invoice.status === "CANCELLED" ? "ANULADO" : "EMITIDO", `invoice-status${invoice.status === "CANCELLED" ? " cancelled" : ""}`);
      top.append(brand, state); sheet.append(top);
      const columns = node("div", null, "invoice-columns");
      const customer = addSection(columns, "", "Cliente"); customer.append(node("p", invoice.customerName), node("p", invoice.customerEmail));
      const reservation = addSection(columns, "", "Reserva"); reservation.append(node("p", `#${invoice.reservationId} · ${invoice.reservationStatus}`), node("p", `Emitido: ${new Date(invoice.issuedAt).toLocaleString("es-CO")}`));
      sheet.append(columns);
      const lines = addSection(sheet, "invoice-lines", "Detalle");
      lines.append(node("p", `${invoice.manufacturer} ${invoice.modelName} · Serie ${invoice.serialNumber}`));
      lines.append(node("p", `Alquiler: ${invoice.startDate} al ${invoice.returnDate} · ${invoice.calendarDays} días calendario`));
      const table = document.createElement("table"); table.className = "invoice-table";
      const head = document.createElement("thead"); const headerRow = document.createElement("tr");
      for (const title of ["Concepto", "Importe"]) headerRow.append(node("th", title)); head.append(headerRow);
      const body = document.createElement("tbody");
      for (const [label, amount] of [[`Renta · ${invoice.calendarDays} días`, invoice.rentalAmount], ["Depósito reembolsable", invoice.depositAmount]]) {
        const row = document.createElement("tr"); row.append(node("td", label), node("td", money(amount, invoice.currency))); body.append(row);
      }
      table.append(head, body); lines.append(table);
      const total = node("div", null, "invoice-total"); total.append(node("span", "Total cobrado (simulado)"), node("span", money(invoice.totalAmount, invoice.currency))); lines.append(total);
      if (invoice.status === "CANCELLED") lines.append(node("p", "Esta reserva fue cancelada. Consulta el reembolso registrado en el historial de reservas.", "invoice-note"));
      const payments = addSection(sheet, "invoice-payments", "Operaciones simuladas");
      if (!invoice.payments.length) payments.append(node("p", "No hay operaciones registradas."));
      for (const payment of invoice.payments) {
        const line = node("div", null, "payment-line");
        line.append(node("span", `${labels[payment.operationType] || payment.operationType} · ${payment.status}`), node("strong", money(payment.amount, payment.currency)));
        payments.append(line);
      }
      sheet.hidden = false; status.textContent = ""; printButton.hidden = false;
    } catch (error) { status.textContent = error.message; }
  };

  printButton.addEventListener("click", () => window.print());
  load();
})();
