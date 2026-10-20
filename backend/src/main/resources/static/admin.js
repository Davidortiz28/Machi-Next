(() => {
  const tokenKey = "machi-next-token";
  const userKey = "machi-next-user";
  const token = sessionStorage.getItem(tokenKey);
  const user = JSON.parse(sessionStorage.getItem(userKey) || "null");
  const list = document.querySelector("#report-list");
  const status = document.querySelector("#admin-status");
  const objectUrls = new Set();

  const api = async (path, options = {}) => {
    const headers = new Headers(options.headers || {});
    headers.set("Authorization", `Bearer ${token}`);
    if (options.body) headers.set("Content-Type", "application/json");
    const response = await fetch(path, { ...options, headers });
    const payload = await response.json().catch(() => null);
    if (!response.ok) throw new Error(payload?.detail || payload?.message || "No fue posible completar la operación.");
    return payload;
  };
  const money = (amount, currency = "COP") => new Intl.NumberFormat("es-CO", { style: "currency", currency, maximumFractionDigits: 0 }).format(Number(amount || 0));
  const element = (tag, text, className) => { const node = document.createElement(tag); if (text !== undefined && text !== null) node.textContent = text; if (className) node.className = className; return node; };

  const showEvidence = async (reportId, evidence, preview) => {
    preview.replaceChildren(element("p", "Cargando evidencia…"));
    try {
      const response = await fetch(`/api/v1/admin/damage-reports/${reportId}/evidence/${evidence.evidenceId}/content`, { headers: { Authorization: `Bearer ${token}` } });
      if (!response.ok) throw new Error("No se pudo cargar este archivo.");
      const blob = await response.blob();
      const url = URL.createObjectURL(blob); objectUrls.add(url);
      preview.replaceChildren();
      let media;
      if (evidence.mediaType.startsWith("image/")) { media = document.createElement("img"); media.alt = `Evidencia ${evidence.evidenceId}`; }
      else if (evidence.mediaType === "video/mp4") { media = document.createElement("video"); media.controls = true; }
      else { media = document.createElement("iframe"); media.title = `Evidencia PDF ${evidence.evidenceId}`; }
      media.src = url; preview.append(media);
      const link = element("a", "Abrir o descargar evidencia", "evidence-button"); link.href = url; link.target = "_blank"; link.rel = "noopener"; preview.append(link);
    } catch (error) { preview.replaceChildren(element("p", error.message)); }
  };

  const loadReports = async () => {
    status.textContent = "Cargando reportes…";
    list.replaceChildren();
    for (const url of objectUrls) URL.revokeObjectURL(url);
    objectUrls.clear();
    try {
      const reports = await api("/api/v1/admin/damage-reports");
      status.textContent = `${reports.length} ${reports.length === 1 ? "reporte pendiente" : "reportes pendientes"}`;
      if (!reports.length) { list.append(element("p", "No hay reportes de daños pendientes.", "empty-reports")); return; }
      for (const report of reports) {
        const card = element("article", null, "damage-card");
        card.append(element("h2", `Reporte #${report.damageReportId} · Reserva #${report.reservationId}`));
        card.append(element("p", `Equipo #${report.equipmentId} · Reportado por ${report.reporterName}`, "damage-meta"));
        card.append(element("p", `Fecha: ${new Date(report.reportedAt).toLocaleString("es-CO")} · Depósito: ${money(report.depositAmount)}`, "damage-meta"));
        const evidenceArea = element("section", null, "evidence-area");
        evidenceArea.append(element("h3", `Evidencias (${report.evidenceCount})`));
        const evidenceList = element("div", null, "evidence-list");
        const preview = element("div", null, "evidence-preview");
        evidenceArea.append(evidenceList, preview); card.append(evidenceArea);
        let evidence = [];
        try {
          evidence = await api(`/api/v1/admin/damage-reports/${report.damageReportId}/evidence`);
          if (evidence.length) {
            for (const file of evidence) {
              const button = element("button", `${file.mediaType} · ${new Date(file.uploadedAt).toLocaleString("es-CO")}`, "evidence-button");
              button.type = "button"; button.addEventListener("click", () => showEvidence(report.damageReportId, file, preview)); evidenceList.append(button);
            }
            await showEvidence(report.damageReportId, evidence[0], preview);
          } else evidenceList.append(element("p", "Falta adjuntar evidencia para poder revisar el caso."));
        } catch (error) { evidenceList.append(element("p", error.message)); }

        const form = document.createElement("form"); form.className = "review-form";
        const decisionLabel = element("label", "Decisión");
        const decision = document.createElement("select");
        for (const [value, label] of [["APPROVE", "Aprobar retención"], ["REJECT", "Rechazar daño y devolver depósito"]]) { const option = element("option", label); option.value = value; decision.append(option); }
        decisionLabel.append(decision);
        const assessedLabel = element("label", "Daño evaluado (COP)");
        const assessed = document.createElement("input"); assessed.type = "number"; assessed.min = "0"; assessed.step = "0.01"; assessed.max = report.depositAmount; assessed.value = "0"; assessed.required = true; assessedLabel.append(assessed);
        const retainedLabel = element("label", "Valor a retener (COP)");
        const retained = document.createElement("input"); retained.type = "number"; retained.min = "0"; retained.step = "0.01"; retained.max = report.depositAmount; retained.value = "0"; retained.required = true; retainedLabel.append(retained);
        const noteLabel = element("label", "Nota de revisión"); noteLabel.className = "wide";
        const note = document.createElement("textarea"); note.maxLength = 1000; note.rows = 3; noteLabel.append(note);
        const submit = element("button", "Guardar revisión", "button button-primary"); submit.type = "submit"; submit.disabled = evidence.length === 0;
        const message = element("p", "", "review-message"); message.setAttribute("role", "status");
        decision.addEventListener("change", () => { retained.disabled = decision.value === "REJECT"; if (retained.disabled) retained.value = "0"; });
        assessed.addEventListener("input", () => { retained.max = String(Math.min(Number(assessed.value || 0), Number(report.depositAmount))); });
        form.append(decisionLabel, assessedLabel, retainedLabel, noteLabel, submit, message);
        form.addEventListener("submit", async (event) => {
          event.preventDefault(); submit.disabled = true; message.textContent = "Guardando…";
          try {
            const result = await api(`/api/v1/admin/damage-reports/${report.damageReportId}/review`, { method: "POST", body: JSON.stringify({ decision: decision.value, assessedAmount: Number(assessed.value), retainedAmount: Number(retained.value), decisionNote: note.value }) });
            status.textContent = `Reporte #${result.damageReportId} resuelto. Retención: ${money(result.retainedAmount, result.currency)} · Reembolso: ${money(result.refundAmount, result.currency)}.`;
            await loadReports();
          } catch (error) { message.textContent = error.message; submit.disabled = false; }
        });
        card.append(form); list.append(card);
      }
    } catch (error) { status.textContent = error.message; }
  };

  if (!token || !user?.roles?.includes("ADMIN")) {
    status.textContent = "Inicia sesión en el catálogo con una cuenta de administrador para revisar los reportes.";
  } else loadReports();
})();
