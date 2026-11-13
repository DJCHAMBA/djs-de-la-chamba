const {contextBridge,ipcRenderer}=require("electron");
contextBridge.exposeInMainWorld("chamba",{
 getSettings:()=>ipcRenderer.invoke("settings:get"),
 saveSettings:v=>ipcRenderer.invoke("settings:save",v),
 sendCommand:c=>ipcRenderer.invoke("bridge:send",c),
 openAudioEditor:()=>ipcRenderer.invoke("audio-editor:open"),
 ytSearch:q=>ipcRenderer.invoke("yt:search",q),
 ytDownload:u=>ipcRenderer.invoke("yt:download",u),
 ytUpdate:()=>ipcRenderer.invoke("yt:update"),
 ytOpenFolder:()=>ipcRenderer.invoke("yt:folder"),
 checkBridge:()=>ipcRenderer.invoke("bridge:check")
});