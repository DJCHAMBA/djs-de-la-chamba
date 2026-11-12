require("dotenv").config();
const http = require("node:http");
const crypto = require("node:crypto");

const HOST = "127.0.0.1";
const PORT = Number(process.env.WEB_BRIDGE_PORT || 3001);
const ORIGIN = process.env.WEB_BRIDGE_ORIGIN || "https://djchamba.github.io";
const TOKEN = String(process.env.WEB_BRIDGE_TOKEN || "");
const TARGET = new URL(process.env.ROBLOX_BRIDGE_URL || "http://127.0.0.1:3000/comando");

if (!TOKEN || TOKEN.length < 32) {
  console.error("Falta WEB_BRIDGE_TOKEN o tiene menos de 32 caracteres. Revisa tu .env.");
  process.exit(1);
}
if (TARGET.protocol !== "http:" || !["127.0.0.1", "localhost", "::1"].includes(TARGET.hostname)) {
  console.error("ROBLOX_BRIDGE_URL debe apuntar a un servidor HTTP local (localhost/127.0.0.1).");
  process.exit(1);
}

const SIMPLE = new Set(["skip", "pause", "resume", "resetear", "mododj", "nomododj", "loop", "unloop"]);
const COMMAND_PATTERNS = [
  /^m2 [^\r\n|]{1,50}\|[^\r\n]{1,350}$/u,
  /^chamba [^\r\n]{1,350}$/u,
  /^musica \d{1,25}(?: (?:0?\.\d+|[1-3](?:\.\d+)?))?$/,
  /^force \d{1,25}(?: (?:0?\.\d+|[1-3](?:\.\d+)?))?$/,
  /^multimusica \d{1,25}(?:,\d{1,25}){0,24}$/,
  /^volumen (?:10(?:\.0+)?|(?:[0-9](?:\.\d+)?))$/,
  /^pitch (?:0?\.\d+|[1-2](?:\.\d+)?|3(?:\.0+)?)$/,
  /^realista(?: [234])?$/,
  /^norealista(?: [234])?$/,
  /^tocando [^\r\n]{1,80}$/u
];

function authorized(req) {
  const header = String(req.headers.authorization || "");
  if (!header.startsWith("Bearer ")) return false;
  const candidate = Buffer.from(header.slice(7));
  const expected = Buffer.from(TOKEN);
  return candidate.length === expected.length && crypto.timingSafeEqual(candidate, expected);
}
function setCors(req, res) {
  const origin = req.headers.origin;
  if (origin && origin !== ORIGIN) return false;
  if (origin) res.setHeader("Access-Control-Allow-Origin", ORIGIN);
  res.setHeader("Vary", "Origin");
  res.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type");
  res.setHeader("Access-Control-Allow-Private-Network", "true");
  res.setHeader("Cache-Control", "no-store");
  return true;
}
function sendJson(res, status, body) {
  res.writeHead(status, { "Content-Type": "application/json; charset=utf-8" });
  res.end(JSON.stringify(body));
}
function readJson(req, limit=16*1024) {
  return new Promise((resolve,reject)=>{
    let body="";
    req.setEncoding("utf8");
    req.on("data",chunk=>{
      body+=chunk;
      if(Buffer.byteLength(body,"utf8")>limit) reject(new Error("El cuerpo supera el límite permitido."));
    });
    req.on("end",()=>{
      try{resolve(JSON.parse(body||"{}"));}
      catch{reject(new Error("JSON inválido."));}
    });
    req.on("error",reject);
  });
}
function validCommand(value) {
  if(typeof value!=="string") return false;
  const command=value.trim();
  if(!command||command.length>500||/[\x00-\x08\x0B\x0C\x0E-\x1F]/.test(command)) return false;
  return SIMPLE.has(command)||COMMAND_PATTERNS.some(pattern=>pattern.test(command));
}
const server=http.createServer(async(req,res)=>{
  if(!setCors(req,res)) return sendJson(res,403,{ok:false,error:"Origen no autorizado."});
  if(req.method==="OPTIONS"){res.writeHead(204);return res.end();}
  const path=new URL(req.url,"http://127.0.0.1").pathname;
  if(path!=="/health"&&path!=="/comando") return sendJson(res,404,{ok:false,error:"Ruta no encontrada."});
  if(!authorized(req)) return sendJson(res,401,{ok:false,error:"Token local incorrecto."});
  if(req.method==="GET"&&path==="/health"){
    return sendJson(res,200,{ok:true,service:"LA CHAMBA STUDIOS Web Bridge",message:"Conector activo. El puente Roblox debe estar encendido en el puerto 3000."});
  }
  if(req.method!=="POST"||path!=="/comando") return sendJson(res,405,{ok:false,error:"Método no permitido."});
  try{
    const data=await readJson(req);
    const comando=typeof data.comando==="string"?data.comando.trim():"";
    if(!validCommand(comando)) return sendJson(res,400,{ok:false,error:"Comando no permitido o inválido."});
    const controller=new AbortController();
    const timer=setTimeout(()=>controller.abort(),7000);
    let response;
    try{
      response=await fetch(TARGET,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({comando}),signal:controller.signal});
    }finally{clearTimeout(timer);}
    const detail=(await response.text()).slice(0,1000);
    if(!response.ok) return sendJson(res,502,{ok:false,error:"El puente Roblox respondió HTTP "+response.status+".",details:detail});
    console.log(new Date().toISOString(),"WEB → ROBLOX:",comando);
    return sendJson(res,200,{ok:true,message:"Comando enviado al puente Roblox.",upstreamStatus:response.status});
  }catch(error){
    const message=error.name==="AbortError"?"El puente Roblox tardó demasiado en responder.":"No se pudo conectar con el puente Roblox: "+error.message;
    return sendJson(res,502,{ok:false,error:message});
  }
});
server.listen(PORT,HOST,()=>{
  console.log("LA CHAMBA STUDIOS Web Bridge activo en http://"+HOST+":"+PORT);
  console.log("Origen permitido:",ORIGIN);
  console.log("Reenvía los comandos a:",TARGET.toString());
});
