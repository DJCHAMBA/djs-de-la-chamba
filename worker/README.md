# DJS DE LA CHAMBA — servidor privado de Roblox

Este Cloudflare Worker protege la contraseña y las API keys. La página GitHub Pages nunca recibe las claves de Roblox.

## 1. Secretos del Worker

En Cloudflare abre **Workers & Pages → djs-chamba-permissions → Settings → Variables and Secrets**.

Mantén estos secretos:

- `ADMIN_PASSWORD`: contraseña privada del panel.
- `SESSION_SECRET`: cadena aleatoria larga (32 caracteres o más).
- `ROBLOX_API_KEY_MAIN`: clave usada por la cuenta principal para conceder permisos de assets.
- `ROBLOX_API_KEY_DJS`: clave usada por la segunda cuenta para conceder permisos de assets.

Añade este secreto para subir y consultar audios:

- `ROBLOX_AUDIO_API_KEY`: API key de Roblox con permisos **Assets Read** y **Assets Write**. La misma clave se usa para subir el audio y consultar su estado de moderación.

Puedes reutilizar una clave existente solamente si tiene ambos permisos `asset:read` y `asset:write` y acceso a los assets del creador. En caso contrario, crea una clave específica para subidas de audio.

### Variables normales (no secretas)

Añade **una sola** de estas variables, según dónde quieras que Roblox cree los audios:

- `ROBLOX_AUDIO_CREATOR_USER_ID`: tu User ID numérico, si los audios serán tuyos.
- `ROBLOX_AUDIO_CREATOR_GROUP_ID`: Group ID numérico, si los audios serán del grupo.

No configures las dos al mismo tiempo. Puedes obtener el User ID desde la URL de tu perfil Roblox; el Group ID está en la URL del grupo.

Después de cambiar secretos o variables, pulsa **Deploy**.

## 2. Crear la API key de audio en Roblox

En el Creator Dashboard crea una API key que pueda acceder a los assets que administrará:

1. Añade **Assets** a Access Permissions.
2. Habilita las operaciones **Read** y **Write** para los assets del creador.
3. La clave debe cubrir los scopes `asset:read` y `asset:write`.

Guarda la clave como secreto `ROBLOX_AUDIO_API_KEY` en Cloudflare. No la pongas en `index.html`, en GitHub ni en chats.

## 3. Publicar el Worker

1. Entra en https://dash.cloudflare.com/
2. Abre **Workers & Pages → djs-chamba-permissions**.
3. Pulsa **Edit code**.
4. Sustituye todo el código por el contenido actual de `roblox-permissions.js`.
5. Pulsa **Deploy**.

La URL configurada en `index.html` es:
`https://djs-chamba-permissions.jnew123gta5.workers.dev`

## 4. Usar la pestaña de subida

1. Abre la página DJS DE LA CHAMBA.
2. Entra a **Subir audios a Roblox** e inicia sesión con la contraseña privada.
3. Selecciona uno o varios audios y pulsa **Subir a Roblox**.
4. El panel registra el Operation ID y después el Asset ID que devuelve Roblox.
5. Pulsa **Actualizar** junto a cada audio o **Actualizar estados** para consultar la moderación.

Estados que muestra la página:

- **Procesando en Roblox**: Roblox todavía no termina la operación de carga.
- **En revisión**: Roblox está moderando el audio.
- **Aceptado**: Roblox devuelve `MODERATION_STATE_APPROVED`.
- **Rechazado**: Roblox devuelve `MODERATION_STATE_REJECTED`.
- **Pendiente de revisión**: todavía no hay estado de moderación disponible.

El historial se guarda localmente en el navegador y no contiene API keys.

## 5. Requisitos de los audios

Roblox admite MP3, OGG, WAV y FLAC. El archivo debe pesar menos de 20 MB, durar como máximo 7 minutos y tener una frecuencia de muestreo de 48 kHz o menos. El audio debe ser una sola pista y debes tener los derechos para subirlo y usarlo.

Los límites de importación dependen de la verificación de la cuenta y de las cuotas vigentes de Roblox. Consulta Creator Dashboard si el servicio rechaza una carga por cuota.

## 6. Seguridad

- No publiques API keys en GitHub.
- No compartas `enviar.js` si contiene claves.
- Protege `ADMIN_PASSWORD` y `SESSION_SECRET`.
- El Worker solo acepta solicitudes del origen `https://djchamba.github.io` y requiere una sesión válida para subir audios o consultar permisos.
