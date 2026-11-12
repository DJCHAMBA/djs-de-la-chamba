const {contextBridge,ipcRenderer}=require("electron");
contextBridge.exposeInMainWorld("chamba",{
 getSettings:()=>ipcRenderer.invoke("settings:get"),
 saveSettings:v=>ipcRenderer.invoke("settings:save",v),
 sendCommand:c=>ipcRenderer.invoke("bridge:send",c),
 openAudioEditor:()=>ipcRenderer.invoke("audio-editor:open"),
 checkBridge:()=>ipcRenderer.invoke("bridge:check")
});