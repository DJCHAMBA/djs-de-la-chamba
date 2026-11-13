const {contextBridge,ipcRenderer}=require("electron");
contextBridge.exposeInMainWorld("chamba",{
 getSettings:()=>ipcRenderer.invoke("settings:get"),
 saveSettings:v=>ipcRenderer.invoke("settings:save",v),
 sendCommand:c=>ipcRenderer.invoke("bridge:send",c),
 openAudioEditor:()=>ipcRenderer.invoke("audio-editor:open"),\n ytSearch:q=>ipcRenderer.invoke("yt:search",q),\n ytDownload:u=>ipcRenderer.invoke("yt:download",u),\n ytUpdate:()=>ipcRenderer.invoke("yt:update"),\n ytOpenFolder:()=>ipcRenderer.invoke("yt:folder"),
 checkBridge:()=>ipcRenderer.invoke("bridge:check")
});