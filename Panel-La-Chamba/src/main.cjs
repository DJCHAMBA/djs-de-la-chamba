const { app, BrowserWindow, ipcMain, Menu, session, shell } = require("electron");
const path = require("node:path");
const fs = require("node:fs");\nconst { spawn } = require("node:child_process");

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
  if (command.length > 1000000) throw new Error("La solicitud supera el tamaño máximo seguro de 1 MB.");
  if (/[\x00-\x1f]/.test(command)) throw new Error("El comando contiene caracteres no permitidos.");
  const name = command.split(/\s+/, 1)[0].toLowerCase();
  const allowed = ["chamba", "m2", "musica", "force", "multimusica", "volumen", "pitch", "realista", "norealista", "tocando", "skip", "pause", "resume", "resetear", "mododj", "nomododj", "loop", "unloop", "pinata", "fuegos", "mexico", "lazer", "espuma", "burbujas", "dinero", "temblor", "resetmapa", "explotar", "mapa1", "mapa", "clima", "tematica", "diseno", "efecto", "efectos"];
  if (!allowed.includes(name)) throw new Error("Comando no reconocido. Usa uno de los accesos rápidos disponibles.");
  if (name === "efecto" && !/^efecto .+ (on|off)$/i.test(command)) throw new Error("Formato de efecto inválido.");
  if (name === "efectos" && command !== "efectos apagar") throw new Error("Acción de efectos inválida.");
  return command;
}


const MUSIC_DIR = path.join(app.getPath("downloads"), "Panel La Chamba", "YT Música");
const BIN_DIR = app.isPackaged ? path.join(process.resourcesPath, "yt-tools") : path.join(__dirname, "..", "bin");
const YTDLP = path.join(BIN_DIR, "yt-dlp.exe");
const FFMPEG = path.join(BIN_DIR, "ffmpeg.exe");
function runYtDlp(args) {
  return new Promise((resolve,reject)=>{const child=spawn(YTDLP,args,{windowsHide:true});let out="",err="";child.stdout.on("data",d=>out+=d);child.stderr.on("data",d=>err+=d);child.on("error",reject);child.on("close",code=>code===0?resolve(out||err):reject(new Error((err||out||"yt-dlp terminó con error").slice(-900))));});
}
ipcMain.handle("yt:search",async(_,query)=>{const q=String(query||"").trim();if(!q||q.length>300)throw new Error("Escribe una búsqueda válida.");const target=/^https?:\/\//i.test(q)?q:"ytsearch10:"+q;const raw=await runYtDlp(["--dump-single-json","--flat-playlist","--no-warnings",target]);const data=JSON.parse(raw);const entries=data.entries||[data];return entries.filter(x=>x&& (x.id||x.url)).slice(0,10).map(x=>({id:x.id,title:x.title||"Sin título",uploader:x.uploader||x.channel||"",duration:x.duration||0,thumbnail:x.thumbnail||((x.thumbnails||[]).slice(-1)[0]||{}).url||"",webpage_url:x.webpage_url||x.url||(x.id?"https://www.youtube.com/watch?v="+x.id:"")}));});
ipcMain.handle("yt:download",async(_,url)=>{const u=String(url||"");if(!/^https?:\/\//i.test(u))throw new Error("Enlace de descarga no válido.");fs.mkdirSync(MUSIC_DIR,{recursive:true});await runYtDlp(["--no-warnings","--no-playlist","-f","bestaudio/best","--extract-audio","--audio-format","mp3","--audio-quality","0","--ffmpeg-location",FFMPEG,"-o",path.join(MUSIC_DIR,"%(title).180B [%(id)s].%(ext)s"),u]);return {file:"Carpeta YT Música"};});
ipcMain.handle("yt:update",async()=>{await runYtDlp(["--update-to","nightly"]);return {message:"yt-dlp nightly actualizado."};});
ipcMain.handle("yt:folder",()=>{fs.mkdirSync(MUSIC_DIR,{recursive:true});return shell.openPath(MUSIC_DIR);});
\nipcMain.handle("audio-editor:open", () => { createAudioEditorWindow(); return { ok: true }; });
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


let audioEditorWindow = null;
function createAudioEditorWindow() {
  if (audioEditorWindow && !audioEditorWindow.isDestroyed()) {
    audioEditorWindow.focus();
    return audioEditorWindow;
  }
  audioEditorWindow = new BrowserWindow({
    width: 1100, height: 850, minWidth: 720, minHeight: 600,
    backgroundColor: "#07050d", title: "Editor Roblox rápido · DJS DE LA CHAMBA",
    autoHideMenuBar: true, show: false,
    webPreferences: { contextIsolation: true, nodeIntegration: false, sandbox: true }
  });
  audioEditorWindow.once("ready-to-show", () => audioEditorWindow.show());
  audioEditorWindow.on("closed", () => { audioEditorWindow = null; });
  audioEditorWindow.loadFile(path.join(__dirname, "audio-editor.html"));
  return audioEditorWindow;
}

let audioToolsCorsConfigured = false;
function configureAudioToolsCors() {
  if (audioToolsCorsConfigured) return;
  // El Worker privado conserva su origen oficial; las claves permanecen en Cloudflare.
  const workerFilter = { urls: ["https://djs-chamba-permissions.jnew123gta5.workers.dev/*"] };
  session.defaultSession.webRequest.onBeforeSendHeaders(workerFilter, (details, callback) => {
    details.requestHeaders.Origin = "https://djchamba.github.io";
    callback({ requestHeaders: details.requestHeaders });
  });
  session.defaultSession.webRequest.onHeadersReceived(workerFilter, (details, callback) => {
    const headers = details.responseHeaders || {};
    headers["access-control-allow-origin"] = ["null"];
    callback({ responseHeaders: headers });
  });
  audioToolsCorsConfigured = true;
}

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
  configureAudioToolsCors();
  Menu.setApplicationMenu(null);
  createWindow();
  app.on("activate", () => {
    if (!BrowserWindow.getAllWindows().length) createWindow();
  });
});
app.on("window-all-closed", () => {
  if (process.platform !== "darwin") app.quit();
});
