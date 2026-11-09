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

function moderationStateLabel(raw) {
  const state = String(raw || "").toUpperCase();
  if (state.includes("APPROVED")) return "APPROVED";
  if (state.includes("REJECTED")) return "REJECTED";
  if (state.includes("REVIEWING")) return "REVIEWING";
  return "PENDING";
}

function operationIdFromPath(path) {
  const value = String(path || "");
  const match = value.match(/(?:^|\/)operations\/([A-Za-z0-9._~-]+)$/);
  return match ? match[1] : "";
}

async function requireAdmin(request, env) {
  if (!env.SESSION_SECRET) return { ok: false, status: 503, error: "Falta configurar SESSION_SECRET." };
  const auth = request.headers.get("Authorization") || "";
  const token = auth.startsWith("Bearer ") ? auth.slice(7) : "";
  if (!token || !(await validSession(token, env.SESSION_SECRET))) {
    return { ok: false, status: 401, error: "Sesión vencida o no autorizada. Vuelve a ingresar la contraseña." };
  }
  return { ok: true };
}

function creatorContext(env) {
  const userId = String(env.ROBLOX_AUDIO_CREATOR_USER_ID || "").trim();
  const groupId = String(env.ROBLOX_AUDIO_CREATOR_GROUP_ID || "").trim();
  if (userId && groupId) throw new Error("Configura solo ROBLOX_AUDIO_CREATOR_USER_ID o ROBLOX_AUDIO_CREATOR_GROUP_ID, no ambos.");
  if (userId && /^\d+$/.test(userId)) return { userId };
  if (groupId && /^\d+$/.test(groupId)) return { groupId };
  throw new Error("Falta configurar ROBLOX_AUDIO_CREATOR_USER_ID o ROBLOX_AUDIO_CREATOR_GROUP_ID en Cloudflare.");
}

async function readRobloxJson(response) {
  const text = await response.text();
  try { return text ? JSON.parse(text) : {}; }
  catch { return { raw: text.slice(0, 1200) }; }
}

function robloxErrorDetail(data) {
  if (!data || typeof data !== "object") return String(data || "");
  const parts = [];
  if (data.message) parts.push(String(data.message));
  if (data.error) parts.push(String(data.error));
  if (data.detail) parts.push(String(data.detail));
  if (data.code !== undefined) parts.push("code=" + String(data.code));
  if (Array.isArray(data.errors)) {
    for (const entry of data.errors.slice(0, 4)) {
      if (entry && typeof entry === "object") {
        parts.push([entry.code !== undefined ? "code=" + entry.code : "", entry.message || entry.detail || ""].filter(Boolean).join(": "));
      } else {
        parts.push(String(entry));
      }
    }
  }
  if (data.raw) parts.push(String(data.raw).slice(0, 500));
  return [...new Set(parts.filter(Boolean))].join(" | ").slice(0, 900);
}

async function robloxErrorMessage(response, data) {
  const detail = robloxErrorDetail(data);
  return "Roblox respondió HTTP " + response.status + (detail ? ": " + detail : ": Roblox no devolvió detalles adicionales.");
}

function upstreamErrorStatus(response) {
  // Mantiene 401 reservado para una sesión inválida del panel.
  return response.status === 401 ? 502 : response.status;
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
    successAssetIds: [...new Set(successAssetIds.map(String))],
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

    if (url.pathname === "/audio/upload") {
      const auth = await requireAdmin(request, env);
      if (!auth.ok) return json({ error: auth.error }, auth.status, origin);
      if (!env.ROBLOX_AUDIO_API_KEY) {
        return json({ error: "Falta configurar ROBLOX_AUDIO_API_KEY como Secret en Cloudflare." }, 503, origin);
      }

      let incoming;
      try { incoming = await request.formData(); }
      catch { return json({ error: "No pude leer el archivo enviado." }, 400, origin); }

      const audio = incoming.get("file");
      if (!audio || typeof audio.arrayBuffer !== "function") {
        return json({ error: "Selecciona un archivo de audio." }, 400, origin);
      }
      if (audio.size <= 0 || audio.size >= 20 * 1024 * 1024) {
        return json({ error: "El audio debe pesar menos de 20 MB." }, 400, origin);
      }

      const fileName = String(audio.name || "audio").trim();
      const extension = (fileName.split(".").pop() || "").toLowerCase();
      const contentTypes = {
        mp3: "audio/mpeg",
        ogg: "audio/ogg",
        wav: "audio/wav",
        flac: "audio/flac"
      };
      if (!contentTypes[extension]) {
        return json({ error: "Formato no admitido. Usa MP3, OGG, WAV o FLAC." }, 400, origin);
      }

      const durationSeconds = Number(incoming.get("durationSeconds") || 0);
      if (Number.isFinite(durationSeconds) && durationSeconds > 420) {
        return json({ error: "Roblox admite audios de hasta 7 minutos." }, 400, origin);
      }

      const displayName = String(incoming.get("displayName") || fileName.replace(/\.[^.]+$/, "")).trim().slice(0, 50);
      const description = String(incoming.get("description") || "Audio subido mediante DJS DE LA CHAMBA.").trim().slice(0, 1000);
      if (!displayName || !description) {
        return json({ error: "El nombre y la descripción del audio son obligatorios." }, 400, origin);
      }

      let creator;
      try { creator = creatorContext(env); }
      catch (error) { return json({ error: error.message }, 503, origin); }

      // Replica el multipart del bot original: request se envía como JSON,
      // y fileContent lleva el nombre de archivo y su MIME real.
      const metadata = {
        assetType: "Audio",
        displayName,
        description,
        creationContext: { creator }
      };

      const robloxForm = new FormData();
      const requestPart = new Blob(
        [JSON.stringify(metadata)],
        { type: "application/json" }
      );
      robloxForm.set("request", requestPart, "");
      const uploadFile = new File([audio], fileName, { type: contentTypes[extension] });
      robloxForm.set("fileContent", uploadFile, fileName);

      let robloxResponse;
      try {
        robloxResponse = await fetch("https://apis.roblox.com/assets/v1/assets", {
          method: "POST",
          headers: { "x-api-key": env.ROBLOX_AUDIO_API_KEY },
          body: robloxForm
        });
      } catch (error) {
        return json({ error: "No se pudo conectar con Roblox: " + String(error.message || error) }, 502, origin);
      }

      const data = await readRobloxJson(robloxResponse);
      if (!robloxResponse.ok) {
        const diagnosticText = typeof data.raw === "string"
          ? data.raw
          : JSON.stringify(data);
        const isCloudflare1015 = /1015/.test(diagnosticText) || String(data.code || "") === "1015";
        const retryAfterHeader = robloxResponse.headers.get("retry-after");
        let retryAfterSeconds = null;
        if (retryAfterHeader) {
          const numericRetry = Number(retryAfterHeader);
          retryAfterSeconds = Number.isFinite(numericRetry)
            ? Math.max(1, Math.ceil(numericRetry))
            : Math.max(1, Math.ceil((Date.parse(retryAfterHeader) - Date.now()) / 1000));
        } else {
          const resetHeader = robloxResponse.headers.get("x-ratelimit-reset");
          const resetSeconds = Number(resetHeader);
          if (Number.isFinite(resetSeconds) && resetSeconds > 0) retryAfterSeconds = Math.ceil(resetSeconds);
        }

        const errorMessage = robloxResponse.status === 429 && isCloudflare1015
          ? "Roblox/Cloudflare devolvió 1015: limitó temporalmente las subidas. No vuelvas a pulsar varias veces; espera antes de intentar de nuevo."
          : robloxResponse.status === 429
            ? "Roblox limitó temporalmente las subidas (HTTP 429). Espera a que se restablezca el límite antes de reintentar."
            : await robloxErrorMessage(robloxResponse, data);

        return json({
          error: errorMessage,
          upstreamStatus: robloxResponse.status,
          upstreamCode: isCloudflare1015 ? "1015" : (data.code || null),
          upstreamDetails: robloxErrorDetail(data),
          retryAfterSeconds,
          rateLimit: {
            limit: robloxResponse.headers.get("x-ratelimit-limit"),
            remaining: robloxResponse.headers.get("x-ratelimit-remaining"),
            reset: robloxResponse.headers.get("x-ratelimit-reset")
          },
          cfRay: robloxResponse.headers.get("cf-ray"),
          roblox: data
        }, upstreamErrorStatus(robloxResponse), origin);
      }

      const operationId = String(
        data.operationId || operationIdFromPath(data.path)
      );
      if (!operationId || operationId === "undefined") {
        return json({ error: "Roblox recibió la solicitud, pero no devolvió un Operation ID reconocible.", roblox: data }, 502, origin);
      }

      return json({
        ok: true,
        operationId,
        operationPath: data.path || ("operations/" + operationId),
        displayName,
        fileName,
        contentType: contentTypes[extension],
        creator
      }, 200, origin);
    }

    if (url.pathname === "/audio/operation") {
      const auth = await requireAdmin(request, env);
      if (!auth.ok) return json({ error: auth.error }, auth.status, origin);
      if (!env.ROBLOX_AUDIO_API_KEY) return json({ error: "Falta ROBLOX_AUDIO_API_KEY en Cloudflare." }, 503, origin);

      let body;
      try { body = await request.json(); }
      catch { return json({ error: "Solicitud inválida." }, 400, origin); }
      const operationId = String(body.operationId || "");
      if (!/^[A-Za-z0-9._~-]+$/.test(operationId)) {
        return json({ error: "Operation ID inválido." }, 400, origin);
      }

      const response = await fetch("https://apis.roblox.com/assets/v1/operations/" + encodeURIComponent(operationId), {
        headers: { "x-api-key": env.ROBLOX_AUDIO_API_KEY }
      });
      const data = await readRobloxJson(response);
      if (!response.ok) {
        return json({
          error: await robloxErrorMessage(response, data),
          upstreamStatus: response.status,
          upstreamDetails: robloxErrorDetail(data),
          cfRay: response.headers.get("cf-ray"),
          roblox: data
        }, upstreamErrorStatus(response), origin);
      }

      const asset = data.response || {};
      const assetId = asset.assetId ? String(asset.assetId) : String(asset.path || "").match(/assets\/(\d+)/)?.[1] || "";
      return json({
        done: Boolean(data.done),
        assetId,
        displayName: asset.displayName || "",
        moderationState: moderationStateLabel(asset.moderationResult && asset.moderationResult.moderationState),
        rawModerationState: asset.moderationResult && asset.moderationResult.moderationState || "",
        error: data.error || null
      }, 200, origin);
    }

    if (url.pathname === "/audio/status") {
      const auth = await requireAdmin(request, env);
      if (!auth.ok) return json({ error: auth.error }, auth.status, origin);
      if (!env.ROBLOX_AUDIO_API_KEY) return json({ error: "Falta ROBLOX_AUDIO_API_KEY en Cloudflare." }, 503, origin);

      let body;
      try { body = await request.json(); }
      catch { return json({ error: "Solicitud inválida." }, 400, origin); }
      const assetId = String(body.assetId || "");
      if (!/^\d+$/.test(assetId)) return json({ error: "Asset ID inválido." }, 400, origin);

      const readMask = encodeURIComponent("moderationResult,displayName,description");
      const response = await fetch("https://apis.roblox.com/assets/v1/assets/" + encodeURIComponent(assetId) + "?readMask=" + readMask, {
        headers: { "x-api-key": env.ROBLOX_AUDIO_API_KEY }
      });
      const data = await readRobloxJson(response);
      if (!response.ok) {
        return json({
          error: await robloxErrorMessage(response, data),
          upstreamStatus: response.status,
          upstreamDetails: robloxErrorDetail(data),
          cfRay: response.headers.get("cf-ray"),
          roblox: data
        }, upstreamErrorStatus(response), origin);
      }

      const rawState = data.moderationResult && data.moderationResult.moderationState || "";
      return json({
        assetId,
        displayName: data.displayName || "",
        moderationState: moderationStateLabel(rawState),
        rawModerationState: rawState,
        state: data.state || "Unspecified"
      }, 200, origin);
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

      // Una ID queda autorizada si cualquiera de las dos cuentas consigue conceder Use.
      // CannotManageAsset de la otra cuenta se considera un rechazo esperado, no un fallo real.
      const successSets = accounts.map(account => new Set(account.successAssetIds));
      const authorizedAnywhere = new Set(accounts.flatMap(account => account.successAssetIds));
      const failedIds = ids.filter(id => !authorizedAnywhere.has(id));

      function collectAssetIds(value, output = new Set(), seen = new Set()) {
        if (!value || typeof value !== "object" || seen.has(value)) return output;
        seen.add(value);
        if (Array.isArray(value)) {
          for (const item of value) collectAssetIds(item, output, seen);
          return output;
        }
        if (value.assetId !== undefined && value.assetId !== null) output.add(String(value.assetId));
        for (const [key, nested] of Object.entries(value)) {
          if (key !== "assetId" && nested && typeof nested === "object") {
            collectAssetIds(nested, output, seen);
          }
        }
        return output;
      }

      const resultados = accounts.map((account, index) => {
        const successSet = successSets[index];
        const noGestiona = ids.filter(id => !successSet.has(id) && authorizedAnywhere.has(id)).length;
        const accountErrors = account.errores.filter(error => {
          const relatedIds = [...collectAssetIds(error)];
          // Si el error solo afecta IDs autorizadas por la otra cuenta, no es un fallo del envío.
          return relatedIds.length === 0 || relatedIds.some(id => failedIds.includes(id));
        });

        return {
          nombre: account.nombre,
          autorizadas: successSet.size,
          noGestiona,
          fallidasReales: failedIds.length,
          totalBloques: account.totalBloques,
          errores: accountErrors.slice(0, 60)
        };
      });

      const detalleSinAcceso = failedIds.map(assetId => ({
        assetId,
        errores: accounts.map(account => ({
          cuenta: account.nombre,
          detalles: account.errores.filter(error => collectAssetIds(error).has(assetId))
        }))
      }));

      return json({
        universeId: UNIVERSE_ID,
        totalIds: ids.length,
        autorizadasTotal: authorizedAnywhere.size,
        fallidasTotal: failedIds.length,
        idsSinAcceso: failedIds,
        detalleSinAcceso: detalleSinAcceso.slice(0, 100),
        resultados
      }, 200, origin);
    }

    return json({ error: "Ruta no encontrada." }, 404, origin);
  }
};
