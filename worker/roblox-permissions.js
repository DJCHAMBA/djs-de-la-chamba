const ALLOWED_ORIGIN = "https://djchamba.github.io";
const UNIVERSE_ID = "6475547874";
const CHUNK_SIZE = 25;
const MAX_IDS = 500;
const PAUSE_MS = 1200;
const encoder = new TextEncoder();
const decoder = new TextDecoder();

function cors(origin) {
  return {
    "Access-Control-Allow-Origin": origin === ALLOWED_ORIGIN ? ALLOWED_ORIGIN : "null",
    "Access-Control-Allow-Methods": "POST, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type, Authorization",
    "Access-Control-Max-Age": "86400",
    "Vary": "Origin"
  };
}

function json(data, status, origin) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { ...cors(origin), "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" }
  });
}

function base64url(bytes) {
  const input = bytes instanceof ArrayBuffer ? new Uint8Array(bytes) : bytes;
  let binary = "";
  for (let i = 0; i < input.length; i++) binary += String.fromCharCode(input[i]);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function fromBase64url(value) {
  const b64 = value.replace(/-/g, "+").replace(/_/g, "/") + "=".repeat((4 - value.length % 4) % 4);
  const binary = atob(b64);
  return Uint8Array.from(binary, ch => ch.charCodeAt(0));
}

async function sha256(value) {
  return new Uint8Array(await crypto.subtle.digest("SHA-256", encoder.encode(value)));
}

function constantTimeEqual(a, b) {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a[i] ^ b[i];
  return diff === 0;
}

async function hmac(value, secret) {
  const key = await crypto.subtle.importKey(
    "raw", encoder.encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]
  );
  return base64url(await crypto.subtle.sign("HMAC", key, encoder.encode(value)));
}

async function makeSession(secret) {
  const payload = base64url(encoder.encode(JSON.stringify({
    scope: "roblox-permissions",
    exp: Date.now() + 30 * 60 * 1000
  })));
  return payload + "." + await hmac(payload, secret);
}

async function validSession(token, secret) {
  try {
    const [payload, signature, extra] = token.split(".");
    if (!payload || !signature || extra) return false;
    const expected = await hmac(payload, secret);
    if (!constantTimeEqual(encoder.encode(signature), encoder.encode(expected))) return false;
    const data = JSON.parse(decoder.decode(fromBase64url(payload)));
    return data.scope === "roblox-permissions" && Number(data.exp) > Date.now();
  } catch {
    return false;
  }
}

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

async function grantForAccount(name, apiKey, ids) {
  const successAssetIds = [];
  const errors = [];
  const chunks = [];
  for (let i = 0; i < ids.length; i += CHUNK_SIZE) chunks.push(ids.slice(i, i + CHUNK_SIZE));

  for (let index = 0; index < chunks.length; index++) {
    const chunk = chunks[index];
    let completed = false;

    for (let attempt = 0; attempt < 3 && !completed; attempt++) {
      try {
        const response = await fetch("https://apis.roblox.com/asset-permissions-api/v1/assets/permissions", {
          method: "PATCH",
          headers: {
            "x-api-key": apiKey,
            "Content-Type": "application/json"
          },
          body: JSON.stringify({
            subjectType: "Universe",
            subjectId: UNIVERSE_ID,
            action: "Use",
            requests: chunk.map(id => ({
              assetId: Number(id),
              grantToDependencies: false
            }))
          })
        });

        const data = await response.json().catch(() => ({}));
        if (response.ok) {
          if (Array.isArray(data.successAssetIds)) successAssetIds.push(...data.successAssetIds);
          if (Array.isArray(data.errors)) errors.push(...data.errors);
          else if (data.errors) errors.push(data.errors);
          completed = true;
        } else {
          const detail = {
            bloque: index + 1,
            estado: response.status,
            detalle: data
          };
          if ((response.status === 429 || response.status >= 500) && attempt < 2) {
            await sleep(1500 * (attempt + 1));
          } else {
            errors.push(detail);
            completed = true;
          }
        }
      } catch (error) {
        if (attempt < 2) await sleep(1500 * (attempt + 1));
        else {
          errors.push({
            bloque: index + 1,
            detalle: String(error && error.message ? error.message : error)
          });
          completed = true;
        }
      }
    }

    if (index < chunks.length - 1) await sleep(PAUSE_MS);
  }

  return {
    nombre: name,
    autorizadas: successAssetIds.length,
    fallidas: errors.length,
    totalBloques: chunks.length,
    errores: errors.slice(0, 60)
  };
}

export default {
  async fetch(request, env) {
    const origin = request.headers.get("Origin") || "";
    if (request.method === "OPTIONS") {
      if (origin !== ALLOWED_ORIGIN) return new Response(null, { status: 403 });
      return new Response(null, { status: 204, headers: cors(origin) });
    }

    if (origin !== ALLOWED_ORIGIN) return new Response("Origin not allowed", { status: 403 });
    if (request.method !== "POST") return json({ error: "Method not allowed" }, 405, origin);

    const url = new URL(request.url);
    if (url.pathname === "/login") {
      if (!env.ADMIN_PASSWORD || !env.SESSION_SECRET) {
        return json({ error: "El servidor no tiene configurada la contraseña de administrador." }, 503, origin);
      }

      let body;
      try { body = await request.json(); }
      catch { return json({ error: "Solicitud inválida." }, 400, origin); }

      const candidate = typeof body.password === "string" ? body.password : "";
      const matches = constantTimeEqual(await sha256(candidate), await sha256(env.ADMIN_PASSWORD));
      if (!candidate || !matches) return json({ error: "Contraseña incorrecta." }, 401, origin);

      return json({ token: await makeSession(env.SESSION_SECRET), expiresIn: 1800 }, 200, origin);
    }

    if (url.pathname === "/permissions") {
      if (!env.SESSION_SECRET) return json({ error: "Falta configurar SESSION_SECRET." }, 503, origin);

      const auth = request.headers.get("Authorization") || "";
      const token = auth.startsWith("Bearer ") ? auth.slice(7) : "";
      if (!token || !(await validSession(token, env.SESSION_SECRET))) {
        return json({ error: "Sesión vencida o no autorizada. Vuelve a ingresar la contraseña." }, 401, origin);
      }

      if (!env.ROBLOX_API_KEY_MAIN || !env.ROBLOX_API_KEY_DJS) {
        return json({ error: "Faltan las dos API keys de Roblox en los secretos del servidor." }, 503, origin);
      }

      let body;
      try { body = await request.json(); }
      catch { return json({ error: "Solicitud inválida." }, 400, origin); }

      const rawIds = Array.isArray(body.ids) ? body.ids : [];
      const ids = [...new Set(rawIds.map(value => String(value).trim()).filter(value => /^\d+$/.test(value)))];
      if (!ids.length) return json({ error: "No se recibieron IDs válidos." }, 400, origin);
      if (ids.length > MAX_IDS) return json({ error: "Máximo " + MAX_IDS + " IDs por envío." }, 400, origin);

      const accounts = await Promise.all([
        grantForAccount("Cuenta Principal", env.ROBLOX_API_KEY_MAIN, ids),
        grantForAccount("Cuenta Secundaria (Otros DJs)", env.ROBLOX_API_KEY_DJS, ids)
      ]);

      return json({
        universeId: UNIVERSE_ID,
        totalIds: ids.length,
        resultados: accounts
      }, 200, origin);
    }

    return json({ error: "Ruta no encontrada." }, 404, origin);
  }
};
