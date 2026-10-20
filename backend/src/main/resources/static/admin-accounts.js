(() => {
  const token = sessionStorage.getItem("machi-next-token");
  const user = JSON.parse(sessionStorage.getItem("machi-next-user") || "null");
  const status = document.querySelector("#accounts-status");
  const content = document.querySelector("#accounts-content");
  const providerList = document.querySelector("#provider-list");
  const transferList = document.querySelector("#transfer-list");
  const transferStatus = document.querySelector("#transfer-list-status");
  let businesses = [];
  let providers = [];

  const api = async (path, options = {}) => {
    const headers = new Headers(options.headers || {}); headers.set("Authorization", `Bearer ${token}`);
    if (options.body) headers.set("Content-Type", "application/json");
    const response = await fetch(path, { ...options, headers });
    const payload = await response.json().catch(() => null);
    if (!response.ok) throw new Error(payload?.detail || payload?.message || "No fue posible completar la operación.");
    return payload;
  };
  const element = (tag, text, className) => { const node = document.createElement(tag); if (text !== undefined && text !== null) node.textContent = text; if (className) node.className = className; return node; };
  const businessName = (id) => businesses.find((business) => business.businessId === Number(id))?.legalName || `Negocio #${id}`;
  const formatDate = (value) => value ? new Date(value).toLocaleString("es-CO") : "—";
  const stateLabel = (value) => ({ PENDING: "Pendiente de aprobación", WAITING: "En periodo de espera", REJECTED: "Rechazada", COMPLETED: "Completada" })[value] || value;

  const loadBusinesses = async () => {
    businesses = await api("/api/v1/admin/businesses");
    const active = businesses.filter((business) => business.status === "ACTIVE");
    const datalist = document.querySelector("#business-options"); datalist.replaceChildren();
    for (const business of active) { const option = document.createElement("option"); option.value = business.businessId; option.label = business.legalName; datalist.append(option); }
    const destination = document.querySelector("#transfer-form [name=toBusinessId]"); destination.replaceChildren();
    for (const business of active) { const option = element("option", `${business.legalName} (#${business.businessId})`); option.value = business.businessId; destination.append(option); }
  };

  const loadProviders = async () => {
    providers = await api("/api/v1/admin/providers");
    providerList.replaceChildren();
    const eligible = providers.filter((provider) => provider.accountStatus === "ACTIVE" && provider.businessId && provider.businessStatus === "ACTIVE");
    const select = document.querySelector("#transfer-form [name=providerUserId]"); select.replaceChildren();
    for (const provider of eligible) { const option = element("option", `${provider.displayName} · ${provider.businessName} (#${provider.userId})`); option.value = provider.userId; select.append(option); }
    if (!providers.length) providerList.append(element("p", "Todavía no hay proveedores.", "empty-state"));
    for (const provider of providers) {
      const row = element("article", null, "provider-row");
      const details = document.createElement("div");
      details.append(element("strong", `${provider.displayName} · #${provider.userId}`));
      details.append(element("p", `${provider.email} · ${provider.accountStatus}`));
      details.append(element("p", provider.businessName ? `${provider.businessName} · ${provider.businessStatus}` : "Sin negocio asignado"));
      row.append(details); providerList.append(row);
    }
    if (!eligible.length) { const option = element("option", "No hay proveedores activos asignados"); option.value = ""; select.append(option); select.disabled = true; }
    else select.disabled = false;
  };

  const loadTransfers = async () => {
    const transfers = await api("/api/v1/admin/provider-transfers");
    transferList.replaceChildren(); transferStatus.textContent = `${transfers.length} transferencias`;
    if (!transfers.length) { transferList.append(element("p", "No hay transferencias registradas.", "empty-state")); return; }
    for (const transfer of transfers) {
      const row = element("article", null, "transfer-row");
      row.append(element("h3", `#${transfer.transferId} · ${transfer.providerName} (${transfer.providerEmail})`));
      row.append(element("p", `${transfer.fromBusinessName} → ${transfer.toBusinessName} · ${stateLabel(transfer.status)}`));
      row.append(element("p", `Solicitada: ${formatDate(transfer.requestedAt)} · Vence: ${formatDate(transfer.effectiveAt)}`));
      row.append(element("p", `Origen: ${transfer.fromBusinessDecision || "Pendiente"} · Destino: ${transfer.toBusinessDecision || "Pendiente"}`));
      if (transfer.status === "WAITING" && transfer.effectiveAt && new Date(transfer.effectiveAt) <= new Date()) {
        const complete = element("button", "Completar transferencia", "button button-primary"); complete.type = "button";
        complete.addEventListener("click", async () => {
          complete.disabled = true;
          try { await api(`/api/v1/admin/provider-transfers/${transfer.transferId}/complete`, { method: "POST" }); status.textContent = "Transferencia completada."; await refresh(); }
          catch (error) { status.textContent = error.message; complete.disabled = false; }
        });
        row.append(complete);
      }
      transferList.append(row);
    }
  };

  const refresh = async () => {
    try { await Promise.all([loadBusinesses(), loadProviders(), loadTransfers()]); }
    catch (error) { status.textContent = error.message; }
  };

  const bindAccountForm = (formId, path, successMessage) => {
    const form = document.querySelector(formId); const message = form.querySelector(".form-status");
    form.addEventListener("submit", async (event) => {
      event.preventDefault();
      const data = Object.fromEntries(new FormData(form)); data.businessId = Number(data.businessId);
      message.textContent = "Guardando…";
      try {
        const account = await api(path, { method: "POST", body: JSON.stringify(data) });
        message.textContent = `${successMessage}: ${account.displayName} · ID #${account.userId}. Entrega las credenciales iniciales de forma segura.`;
        form.reset(); await refresh();
      } catch (error) { message.textContent = error.message; }
    });
  };

  if (!token || !user?.roles?.includes("ADMIN")) status.textContent = "Inicia sesión en el catálogo con una cuenta de administrador para gestionar negocios.";
  else {
    content.hidden = false;
    bindAccountForm("#provider-form", "/api/v1/admin/providers", "Proveedor creado");
    bindAccountForm("#manager-form", "/api/v1/admin/business-managers", "Representante creado");
    document.querySelector("#transfer-form").addEventListener("submit", async (event) => {
      event.preventDefault();
      const data = Object.fromEntries(new FormData(event.currentTarget)); data.providerUserId = Number(data.providerUserId); data.toBusinessId = Number(data.toBusinessId);
      const message = document.querySelector("#transfer-form-status"); message.textContent = "Enviando solicitud…";
      try { const transfer = await api("/api/v1/admin/provider-transfers", { method: "POST", body: JSON.stringify(data) }); message.textContent = `Solicitud #${transfer.transferId} creada. Los dos negocios deben aprobarla.`; await refresh(); }
      catch (error) { message.textContent = error.message; }
    });
    document.querySelector("#refresh-data").addEventListener("click", refresh);
    refresh();
  }
})();
