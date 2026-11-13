const { contextBridge, ipcRenderer } = require("electron");
contextBridge.exposeInMainWorld("pocoyitoMusic", {
  search: query => ipcRenderer.invoke("music:search", query),
  download: url => ipcRenderer.invoke("music:download", url),
  update: () => ipcRenderer.invoke("music:update"),
  openFolder: () => ipcRenderer.invoke("music:open-folder"),
  onProgress: callback => ipcRenderer.on("music:progress", (_event, line) => callback(line))
});
