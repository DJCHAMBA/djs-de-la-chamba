const { app, BrowserWindow, ipcMain, Menu } = require("electron");
const path = require("node:path");
const fs = require("node:fs");

const DEFAULT_ENDPOINT = "http://127.0.0.1:3000/comando";
const CONFIG_FILE = () => path.join(app.getPath("userData"), "settings.json");

function readSettings() {
  try {
    const value = JSON.parse(fs.readFileSync(CONFIG_FILE(), "utf8"));
    return { endpoint: value.endpoint || DEFAULT_ENDPOINT };
  } catch {
    return { endpoint: DEFAULT_ENDPOINT };
  }
}

function normalizeEndpoint(value) {
  let url;
  try { url = new URL(String(value || "").trim()); }
  catch { throw new Error("Escribe una dirección válida, por ejemplo http://127.0.0.1:3000/comando"); }
  if (!["http:", "https:"].includes(url.protocol)) throw new Error("Solo se permiten direcciones HTTP o HTTPS.");
  if (url.username || url.password) throw new Error("No incluyas credenciales dentro de la dirección.");
  return url.toString();
}

function saveSettings(value) {
  const endpoint = normalizeEndpoint(value?.endpoint);
  fs.mkdirSync(path.dirname(CONFIG_FILE()), { recursive: true });
  fs.writeFileSync(CONFIG_FILE(), JSON.stringify({ endpoint }, null, 2), "utf8");
  return { endpoint };
}

function validateCommand(raw) {
  const command = String(raw || "").trim().replace(/^\/+/, "");
  if (!command) throw new Error("Escribe un comando antes de enviarlo.");
  if (command.length > 500) throw new Error("El comando supera el límite de 500 caracteres.");
  if (/[\x00-\x1f]/.test(command)) throw new Error("El comando contiene caracteres no permitidos.");
  const name = command.split(/\s+/, 1)[0].toLowerCase();
  const allowed = ["chamba", "m2", "musica", "force", "multimusica", "volumen", "pitch", "realista", "norealista", "tocando", "skip", "pause", "resume", "resetear", "mododj", "nomododj", "loop", "unloop"];
  if (!allowed.includes(name)) throw new Error("Comando no reconocido. Usa uno de los accesos rápidos disponibles.");
  return command;
}

ipcMain.handle("settings:get", () => readSettings());
ipcMain.handle("settings:save", (_, value) => saveSettings(value));

ipcMain.handle("bridge:send", async (_, raw) => {
  const comando = validateCommand(raw);
  const endpoint = readSettings().endpoint;
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 8000);
  try {
    const response = await fetch(endpoint, {
      method: "POST",
      headers: { "Content-Type": "application/json", "Accept": "application/json" },
      body: JSON.stringify({ comando }),
      signal: controller.signal
    });
    const bodyText = await response.text();
    let data = {};
    try { data = bodyText ? JSON.parse(bodyText) : {}; } catch { data = { message: bodyText.slice(0, 300) }; }
    if (!response.ok) throw new Error(data.error || data.message || `El servidor respondió HTTP ${response.status}.`);
    return { command: comando, message: data.message || "El puente aceptó el comando." };
  } catch (error) {
    if (error.name === "AbortError") throw new Error("El puente tardó demasiado en responder (8 segundos).");
    if (error.message.startsWith("Failed to fetch") || error.cause?.code === "ECONNREFUSED") {
      throw new Error("No se pudo conectar. Comprueba que el puente Roblox esté encendido y que la dirección sea correcta.");
    }
    throw new Error(error.message || "No se pudo enviar el comando.");
  } finally {
    clearTimeout(timeout);
  }
});

ipcMain.handle("bridge:check", async () => {
  const endpoint = new URL(readSettings().endpoint);
  endpoint.pathname = endpoint.pathname.replace(/\/comando\/?$/i, "") + "/health";
  endpoint.search = "";
  endpoint.hash = "";
  try {
    const response = await fetch(endpoint, { signal: AbortSignal.timeout(4000) });
    if (response.status === 404 || response.status === 405) {
      return { state: "reachable", message: "Servidor alcanzable; no ofrece /health. Prueba enviando un comando." };
    }
    if (!response.ok) return { state: "warning", message: `El servidor respondió HTTP ${response.status} en /health.` };
    return { state: "online", message: "Puente disponible y respondiendo." };
  } catch {
    return { state: "offline", message: "No se pudo conectar. Verifica que el puente esté encendido y revisa la dirección." };
  }
});

function createWindow() {
  const window = new BrowserWindow({
    width: 1240,
    height: 860,
    minWidth: 900,
    minHeight: 680,
    backgroundColor: "#080d18",
    title: "Panel La Chamba",
    autoHideMenuBar: true,
    show: false,
    webPreferences: {
      preload: path.join(__dirname, "preload.cjs"),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  });
  window.once("ready-to-show", () => window.show());
  window.loadFile(path.join(__dirname, "index.html"));
}

app.whenReady().then(() => {
  app.setName("Panel La Chamba");
  Menu.setApplicationMenu(null);
  createWindow();
  app.on("activate", () => {
    if (!BrowserWindow.getAllWindows().length) createWindow();
  });
});
app.on("window-all-closed", () => {
  if (process.platform !== "darwin") app.quit();
});
