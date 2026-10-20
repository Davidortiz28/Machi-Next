(() => {
  const token = sessionStorage.getItem("machi-next-token");
  const user = JSON.parse(sessionStorage.getItem("machi-next-user") || "null");
  const list = document.querySelector("#transfer-list");
  const status = document.querySelector("#transfer-status");

  const api = async (path, options = {}) => {
    const headers = new Headers(options.headers || {});
    headers.set("Authorization", `Bearer ${token}`);
    if (options.body) headers.set("Content-Type", "application/json");
    const response = await fetch(path, { ...options, headers });
    const payload = await response.json().catch(() => null);
    if (!response.ok) throw new Error(payload?.detail || payload?.message || "No fue posible completar la operación.");
    return payload;
  };
  const element = (tag, text, className) => { const node = document.createElement(tag); if (text !== undefined && text !== null) node.textContent = text; if (className) node.className = className; return node; };
  const dateTime = (value) => value ? new Date(value).toLocaleString("es-CO") : "—";
  const decisionLabel = (value) => value === "APPROVED" ? "Aprobada" : value === "REJECTED" ? "Rechazada" : "Pendiente";
  const statusLabel = (value) => ({ PENDING: "Pendiente de decisiones", WAITING: "En periodo de espera", REJECTED: "Rechazada", COMPLETED: "Completada" })[value] || value;

  const decide = async (id, side, decision) => {
    status.textContent = "Guardando decisión…";
    try {
      await api(`/api/v1/business/provider-transfers/${id}/${side}-decision`, { method: "POST", body: JSON.stringify({ decision }) });
      await loadTransfers();
      status.textContent = decision === "APPROVED" ? "Aprobación registrada." : "Rechazo registrado.";
    } catch (error) { status.textContent = error.message; }
  };

  const sideCard = (label, decision, canDecide, id, side) => {
    const section = element("section", null, "decision-side");
    section.append(element("h3", label));
    section.append(element("p", `Decisión: ${decisionLabel(decision)}`, "side-decision"));
    if (canDecide) {
      const actions = element("div", null, "decision-actions");
      const approve = element("button", "Aprobar", "button button-primary"); approve.type = "button";
      approve.addEventListener("click", () => decide(id, side, "APPROVED"));
      const reject = element("button", "Rechazar", "button button-outline"); reject.type = "button";
      reject.addEventListener("click", () => decide(id, side, "REJECTED"));
      actions.append(approve, reject); section.append(actions);
    }
    return section;
  };

  const loadTransfers = async () => {
    status.textContent = "Cargando solicitudes…";
    list.replaceChildren();
    try {
      const rows = await api("/api/v1/business/provider-transfers");
      status.textContent = `${rows.length} ${rows.length === 1 ? "solicitud relacionada" : "solicitudes relacionadas"}`;
      if (!rows.length) { list.append(element("p", "No hay transferencias que requieran tu atención.", "empty-transfers")); return; }
      for (const row of rows) {
        const transfer = row.transfer;
        const card = element("article", null, "transfer-card");
        card.append(element("h2", `Transferencia #${transfer.transferId} · ${transfer.providerName}`));
        card.append(element("p", `${transfer.providerEmail} · Proveedor #${transfer.providerUserId}`, "transfer-meta"));
        card.append(element("p", `${transfer.fromBusinessName} → ${transfer.toBusinessName}`, "transfer-meta"));
        card.append(element("p", `Estado: ${statusLabel(transfer.status)} · Solicitada: ${dateTime(transfer.requestedAt)}`, "transfer-meta"));
        if (transfer.status === "WAITING") card.append(element("p", `La nueva asignación entra en vigor: ${dateTime(transfer.effectiveAt)}. El proveedor sigue asignado al negocio de origen durante la espera.`, "transfer-meta"));
        if (transfer.status === "COMPLETED") card.append(element("p", `Completada: ${dateTime(transfer.completedAt)}`, "transfer-meta"));
        const decisions = element("div", null, "transfer-decisions");
        decisions.append(sideCard(`Negocio de origen · ${transfer.fromBusinessName}`, transfer.fromBusinessDecision, row.canDecideSource, transfer.transferId, "source"));
        decisions.append(sideCard(`Negocio de destino · ${transfer.toBusinessName}`, transfer.toBusinessDecision, row.canDecideDestination, transfer.transferId, "destination"));
        card.append(decisions); list.append(card);
      }
    } catch (error) { status.textContent = error.message; }
  };

  if (!token || !user?.roles?.includes("BUSINESS_ADMIN")) {
    status.textContent = "Inicia sesión en el catálogo con una cuenta representante de negocio para consultar las transferencias.";
  } else {
    document.querySelector("#refresh-transfers").addEventListener("click", loadTransfers);
    loadTransfers();
  }
})();
