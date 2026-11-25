import assert from "node:assert/strict";

const [origen, puerto] = process.argv.slice(2);
const targets = await (await fetch("http://127.0.0.1:" + puerto + "/json/list")).json();
const ws = new WebSocket(targets.find((item) => item.type === "page").webSocketDebuggerUrl);
await new Promise((resolve, reject) => {
  ws.addEventListener("open", resolve, { once: true });
  ws.addEventListener("error", reject, { once: true });
});
let id = 0;
const pending = new Map();
ws.addEventListener("message", ({ data }) => {
  const packet = JSON.parse(data);
  if (packet.id == null) return;
  const wait = pending.get(packet.id);
  if (!wait) return;
  pending.delete(packet.id);
  if (packet.error) wait.reject(new Error(packet.error.message));
  else wait.resolve(packet.result);
});
function command(method, params = {}) {
  const current = ++id;
  return new Promise((resolve, reject) => {
    pending.set(current, { resolve, reject });
    ws.send(JSON.stringify({ id: current, method, params }));
  });
}
async function run(fn, ...args) {
  let result;
  try {
    result = await command("Runtime.evaluate", {
      expression: "(" + fn.toString() + ")(..." + JSON.stringify(args) + ")",
      awaitPromise: true,
      returnByValue: true,
    });
  } catch {
    return false;
  }
  if (result.exceptionDetails) return false;
  return result.result.value;
}
async function until(description, fn, ...args) {
  const stop = Date.now() + 18000;
  while (Date.now() < stop) {
    const result = await run(fn, ...args);
    if (result) return result;
    await new Promise((done) => setTimeout(done, 100));
  }
  throw new Error("Vista sin resultado esperado: " + description);
}
function setField(name, value) {
  const el = document.querySelector("[name=\"" + name + "\"]");
  if (!el) return false;
  const proto = el.tagName === "SELECT" ? HTMLSelectElement.prototype
    : el.tagName === "TEXTAREA" ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
  Object.getOwnPropertyDescriptor(proto, "value").set.call(el, value);
  el.dispatchEvent(new Event(el.tagName === "SELECT" ? "change" : "input", { bubbles: true }));
  return el.value === value;
}

try {
  await command("Page.enable");
  await command("Emulation.setDeviceMetricsOverride", { width: 1280, height: 900, deviceScaleFactor: 1, mobile: false });
  await command("Page.navigate", { url: origen + "/catalogo" });
  await until("catálogo privado redirige a login", () => location.pathname === "/login" &&
    document.querySelector("h1")?.textContent.includes("Iniciar sesión"));
  assert(await run(setField, "correo", "clientea@example.test"));
  assert(await run(setField, "clave", process.env.SMOKE_CLIENT_PASSWORD));
  assert(await run(() => { document.querySelector("button[type=submit]").click(); return true; }));
  await until("login real a React muestra catálogo", () =>
    location.pathname === "/catalogo" && Array.from(document.querySelectorAll("article")).some((a) => a.textContent.includes("Cilindro de Gas 15 Kg")));
  console.log("OK: React protege catálogo y lo carga con login real a través de Nginx");
  for (const width of [360, 768, 1280]) {
    await command("Emulation.setDeviceMetricsOverride", { width, height: 900, deviceScaleFactor: 1, mobile: false });
    const measured = await run(() => ({ vw: innerWidth, scrollW: document.documentElement.scrollWidth }));
    assert(measured.vw === width && measured.scrollW <= width + 1, "Catálogo desborda en " + width + " px");
    console.log("OK: catálogo sin desborde horizontal a " + width + " px");
  }
  assert(await run(() => {
    const p = Array.from(document.querySelectorAll("article")).find((a) => a.textContent.includes("Cilindro de Gas 15 Kg"));
    const boton = p?.querySelector("button");
    if (!boton || boton.disabled) return false;
    boton.click();
    return true;
  }));
  await until("carrito actualizado tras agregar cilindro", () => document.body.textContent.includes("añadido al carrito"));
  await command("Page.navigate", { url: origen + "/checkout" });
  await until("formulario de entrega", () => document.querySelector("h1")?.textContent.includes("Datos de entrega"));
  const abrirMapa = await run(() => {
    const boton = [...document.querySelectorAll("button")].find((item) => item.textContent.includes("Elegir punto en el mapa"));
    if (!boton) return false;
    boton.click();
    return true;
  });
  if (abrirMapa) await until("mapa habilitado para ubicación", () => !!document.querySelector(".leaflet-container"));
  assert(await run(setField, "telefono", "+56 9 1234 5678"));
  assert(await run(setField, "direccion", "Calle Navegador 123"));
  assert(await run(setField, "comuna", "Chillán"));
  assert(await run(setField, "entrega-latitud", "-36.61"));
  assert(await run(setField, "entrega-longitud", "-72.10"));
  assert(await run(() => {
    const boton = document.querySelector("button[type=submit]");
    if (boton?.disabled) return false;
    boton?.click();
    return !!boton;
  }));
  await until("checkout confirmado por API", () =>
    location.pathname === "/compra/exito" && document.querySelector("h1")?.textContent.includes("Compra confirmada"));
  await command("Page.navigate", { url: origen + "/mis-pedidos" });
  await until("historial consulta el pedido persistido", () =>
    document.querySelector("h1")?.textContent.includes("Historial de pedidos") &&
    [...document.querySelectorAll("button")].some((button) => button.textContent.includes("Ver seguimiento")));
  const opened = await run(() => {
    const button = [...document.querySelectorAll("button")].find((item) => item.textContent.includes("Ver seguimiento"));
    if (!button) return false;
    button.click();
    return true;
  });
  assert(opened);
  await until("mapa de seguimiento del pedido persistido", () => document.querySelectorAll(".leaflet-marker-icon").length >= 1);
  console.log("OK: React compra real, historial persistido y punto de entrega en Leaflet");

  await command("Page.navigate", { url: origen + "/contacto" });
  await until("formulario de contacto del cliente", () =>
    document.querySelector("h1")?.textContent === "Contacto" && !!document.querySelector("textarea[name=mensaje]"));
  assert(await run(() => document.querySelector("input[name=correo]")?.readOnly &&
    document.querySelector("input[name=correo]").value === "clientea@example.test"));
  const mensajeContacto = "¿Hacen despacho en Chillán Viejo? Consulta enviada desde el navegador.";
  assert(await run(setField, "nombre", "María del Ensayo"));
  assert(await run(setField, "mensaje", mensajeContacto));
  assert(await run(() => {
    const enviar = [...document.querySelectorAll("button")].find((boton) => boton.textContent.trim() === "Enviar consulta");
    if (!enviar || enviar.disabled) return false;
    enviar.click();
    return true;
  }));
  await until("confirmación de consulta recibida", () => document.body.textContent.includes("Recibimos tu consulta."));

  assert(await run(() => {
    const salir = [...document.querySelectorAll("button")].find((boton) => boton.textContent.trim() === "Cerrar sesión");
    salir?.click();
    return !!salir;
  }));
  await until("salida de la sesión del cliente", () => location.pathname === "/login");
  await command("Page.navigate", { url: origen + "/admin/contacto" });
  await until("login de acceso a la bandeja", () => location.pathname === "/login" &&
    document.querySelector("h1")?.textContent.includes("Iniciar sesión"));
  assert(await run(setField, "correo", process.env.SMOKE_ADMIN_EMAIL));
  assert(await run(setField, "clave", process.env.SMOKE_ADMIN_PASSWORD));
  assert(await run(() => { document.querySelector("button[type=submit]").click(); return true; }));
  await until("bandeja administrativa muestra mensaje persistido", (texto) =>
    document.querySelector("h1")?.textContent.includes("Consultas de contacto") &&
    document.body.textContent.includes(texto) && document.body.textContent.includes("clientea@example.test"), mensajeContacto);
  console.log("OK: React envía contacto y la bandeja administrativa carga el mensaje guardado");

  assert(await run(() => {
    const salir = [...document.querySelectorAll("button")].find((boton) => boton.textContent.trim() === "Cerrar sesión");
    salir?.click();
    return !!salir;
  }));
  await until("salida de administración", () => location.pathname === "/login");
  assert(await run(setField, "correo", "vendedor@example.test"));
  assert(await run(setField, "clave", process.env.SMOKE_CLIENT_PASSWORD));
  assert(await run(() => { document.querySelector("button[type=submit]").click(); return true; }));
  await until("vendedor recibe el listado real", () => location.pathname === "/despacho" &&
    !!document.querySelector("main a[href^='/pedidos/']"));
  assert(await run(() => {
    const enlaces = [...document.querySelectorAll("#menu-principal a")].map((enlace) => enlace.textContent.trim());
    return enlaces.join(",") === "Productos,Pedidos";
  }));
  assert(await run(() => {
    document.querySelector("main a[href^='/pedidos/']").click();
    return true;
  }));
  await until("pedido real con detalle de sus productos", () => location.pathname.startsWith("/pedidos/") &&
    document.querySelector("h1")?.textContent === "Detalle del pedido" && !!document.querySelector("main li h2"));
  assert(await run(() => !document.querySelector("main").textContent.includes("Calle Navegador")));
  await command("Page.navigate", { url: origen + "/categorias" });
  await until("vendedor tiene acceso restringido a categorías", () =>
    document.querySelector("h1")?.textContent === "Acceso restringido");
  console.log("OK: React limita al vendedor a Productos y Pedidos y muestra el detalle real sin datos privados");
} finally {
  ws.close();
}
