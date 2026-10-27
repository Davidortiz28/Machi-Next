(() => {
  const status = document.querySelector("#launch-status");
  const code = new URLSearchParams(window.location.hash.slice(1)).get("code");
  window.history.replaceState(null, "", window.location.pathname);

  const fail = (message) => { status.textContent = message; };
  if (!code) { fail("No se recibió un código de acceso. Vuelve a abrir el lanzador administrativo."); return; }

  fetch("/api/v1/auth/admin-launch/consume", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ code })
  }).then(async (response) => {
    const session = await response.json().catch(() => null);
    if (!response.ok || !session?.accessToken || !session.roles?.includes("ADMIN")) {
      throw new Error(session?.detail || "El código venció o ya fue utilizado. Vuelve a abrir el lanzador.");
    }
    sessionStorage.setItem("machi-next-token", session.accessToken);
    sessionStorage.setItem("machi-next-user", JSON.stringify({
      userId: session.userId, displayName: session.displayName, email: session.email, roles: session.roles
    }));
    window.location.replace("/admin-accounts.html");
  }).catch((error) => fail(error.message));
})();
