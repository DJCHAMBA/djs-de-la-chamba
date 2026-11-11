const { app, BrowserWindow, Menu, shell, session, Notification, dialog } = require("electron");
const path = require("node:path");
const fs = require("node:fs");

const APP_URL = "https://djchamba.github.io/djs-de-la-chamba/";
const APP_HOST = "djchamba.github.io";
const APP_TITLE = "DJS DE LA CHAMBA";
const DOWNLOAD_FOLDER = path.join(app.getPath("downloads"), APP_TITLE);
const ICON_PATH = path.join(__dirname, "..", "build", "icon.ico");

let mainWindow = null;
let isLoadingOfflinePage = false;

app.setName(APP_TITLE);
app.setAppUserModelId("com.djchamba.djsdelachamba.desktop");

const hasSingleInstance = app.requestSingleInstanceLock();
if (!hasSingleInstance) app.quit();

app.on("second-instance", () => {
  if (!mainWindow) return;
  if (mainWindow.isMinimized()) mainWindow.restore();
  mainWindow.show();
  mainWindow.focus();
});

function ensureDownloadFolder() {
  fs.mkdirSync(DOWNLOAD_FOLDER, { recursive: true });
}

function safeFileName(value) {
  const cleaned = path.basename(String(value || "Audio-DJS.mp3"))
    .replace(/[<>:"/\\|?*\x00-\x1f]/g, "_")
    .replace(/[. ]+$/g, "")
    .trim();
  return cleaned || "Audio-DJS.mp3";
}

function uniqueDownloadPath(fileName) {
  ensureDownloadFolder();
  const safeName = safeFileName(fileName);
  const parsed = path.parse(safeName);
  let candidate = path.join(DOWNLOAD_FOLDER, safeName);
  let suffix = 2;
  while (fs.existsSync(candidate)) {
    candidate = path.join(DOWNLOAD_FOLDER, parsed.name + " (" + suffix + ")" + parsed.ext);
    suffix += 1;
  }
  return candidate;
}

function showDownloadNotice(title, body) {
  if (!Notification.isSupported()) return;
  const notice = new Notification({ title, body, silent: true });
  notice.on("click", () => shell.openPath(DOWNLOAD_FOLDER));
  notice.show();
}

function openExternal(url) {
  try {
    const parsed = new URL(url);
    if (parsed.protocol === "https:" || parsed.protocol === "http:" || parsed.protocol === "mailto:") {
      shell.openExternal(parsed.toString());
    }
  } catch (error) {
    console.warn("Enlace externo no válido:", error.message);
  }
}

function isAppUrl(rawUrl) {
  try {
    const parsed = new URL(rawUrl);
    return parsed.protocol === "https:" && parsed.hostname.toLowerCase() === APP_HOST;
  } catch {
    return false;
  }
}

function showOfflinePage() {
  if (!mainWindow || mainWindow.isDestroyed() || isLoadingOfflinePage) return;
  isLoadingOfflinePage = true;
  mainWindow.loadFile(path.join(__dirname, "offline.html")).finally(() => {
    isLoadingOfflinePage = false;
  });
}

function installApplicationMenu() {
  const template = [
    {
      label: "Archivo",
      submenu: [
        {
          label: "Recargar DJS DE LA CHAMBA",
          accelerator: "Ctrl+R",
          click: () => {
            if (mainWindow && !mainWindow.isDestroyed()) mainWindow.loadURL(APP_URL);
          }
        },
        { type: "separator" },
        { role: "quit", label: "Salir" }
      ]
    },
    {
      label: "Ver",
      submenu: [
        { role: "reload", label: "Recargar" },
        { role: "resetZoom", label: "Tamaño original" },
        { role: "zoomIn", label: "Acercar" },
        { role: "zoomOut", label: "Alejar" },
        { type: "separator" },
        { role: "togglefullscreen", label: "Pantalla completa" }
      ]
    },
    {
      label: "Ayuda",
      submenu: [
        { label: "Abrir página web", click: () => openExternal(APP_URL) },
        {
          label: "Acerca de DJS DE LA CHAMBA",
          click: () => {
            dialog.showMessageBox(mainWindow, {
              type: "info",
              title: "DJS DE LA CHAMBA",
              message: APP_TITLE,
              detail: "Estudio de audio para DJs y Roblox.\nVersión de escritorio 1.0.0"
            });
          }
        }
      ]
    }
  ];
  Menu.setApplicationMenu(Menu.buildFromTemplate(template));
}

function attachDownloadHandling() {
  session.defaultSession.on("will-download", (_event, item) => {
    const filePath = uniqueDownloadPath(item.getFilename());
    item.setSavePath(filePath);
    item.once("done", (_doneEvent, state) => {
      if (state === "completed") {
        showDownloadNotice("Descarga terminada", path.basename(filePath) + "\nGuardado en Descargas / DJS DE LA CHAMBA.");
      } else if (state === "interrupted") {
        showDownloadNotice("Descarga interrumpida", path.basename(filePath) + " no se pudo completar.");
      }
    });
  });
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1380,
    height: 900,
    minWidth: 960,
    minHeight: 680,
    show: false,
    center: true,
    title: APP_TITLE,
    icon: fs.existsSync(ICON_PATH) ? ICON_PATH : undefined,
    backgroundColor: "#090a0d",
    autoHideMenuBar: true,
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      webSecurity: true,
      allowRunningInsecureContent: false,
      spellcheck: true
    }
  });

  mainWindow.once("ready-to-show", () => {
    if (mainWindow && !mainWindow.isDestroyed()) mainWindow.show();
  });

  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    openExternal(url);
    return { action: "deny" };
  });

  mainWindow.webContents.on("will-navigate", (event, url) => {
    if (isAppUrl(url) || url.startsWith("about:") || url.startsWith("blob:") || url.startsWith("data:")) return;
    event.preventDefault();
    openExternal(url);
  });

  mainWindow.webContents.on("did-fail-load", (_event, errorCode, description, _validatedURL, isMainFrame) => {
    if (!isMainFrame || errorCode === -3 || isLoadingOfflinePage) return;
    console.warn("No se pudo cargar la página:", errorCode, description);
    showOfflinePage();
  });

  mainWindow.on("closed", () => {
    mainWindow = null;
  });

  mainWindow.loadURL(APP_URL);
}

app.whenReady().then(() => {
  app.setAppUserModelId("com.djchamba.djsdelachamba.desktop");
  installApplicationMenu();
  attachDownloadHandling();
  ensureDownloadFolder();
  createWindow();
  app.on("activate", () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on("window-all-closed", () => {
  if (process.platform !== "darwin") app.quit();
});
