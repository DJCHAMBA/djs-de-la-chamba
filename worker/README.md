# Servidor privado de permisos Roblox

Este Worker permite que la página GitHub Pages de DJS DE LA CHAMBA valide una contraseña de administrador y autorice IDs de assets en el universo Roblox configurado.

## 1. Publicar el Worker

1. Entra al panel de Cloudflare: https://dash.cloudflare.com/
2. Abre **Workers & Pages** y crea un Worker nuevo.
3. Ponle un nombre, por ejemplo `djs-chamba-permissions`.
4. Abre **Edit code** y sustituye el código de ejemplo por todo el contenido de `roblox-permissions.js` de esta carpeta.
5. Pulsa **Deploy** y copia la URL `https://NOMBRE.SUBDOMINIO.workers.dev`.

## 2. Configurar secretos (no ponerlos en GitHub)

En el Worker abre **Settings → Variables and Secrets → Add → Secret**. Añade estos cuatro secretos:

- `ADMIN_PASSWORD`: contraseña privada que solo tú debes conocer. Usa una contraseña larga y única.
- `SESSION_SECRET`: una cadena aleatoria larga (al menos 32 caracteres).
- `ROBLOX_API_KEY_MAIN`: API key nueva de tu cuenta principal de Roblox.
- `ROBLOX_API_KEY_DJS`: API key nueva de tu segunda cuenta de Roblox.

Después de añadirlos, pulsa **Deploy** para publicar la configuración.

**Seguridad:** las dos API keys del archivo original `enviar.js` deben revocarse y reemplazarse por claves nuevas. No reutilices las que estaban dentro del archivo. Nunca pegues las claves ni tu contraseña en el HTML, en GitHub o en el chat.

Las API keys deben tener los permisos necesarios para actualizar permisos de assets en el universo Roblox `6475547874`.

## 3. Conectar la página

Después de desplegar el Worker, copia su URL. En `index.html`, busca:

```js
const PERMISSIONS_API_URL = "https://REEMPLAZA-CON-TU-WORKER.workers.dev";
```

Sustituye el texto de ejemplo por tu URL real, sin añadir `/login` al final. Guarda el cambio en la rama `main`.

El Worker solo acepta peticiones desde `https://djchamba.github.io`. El panel envía únicamente la lista de IDs al Worker; las API keys permanecen como secretos en Cloudflare.

## 4. Uso

1. Abre la página y pulsa **Permisos Roblox (Admin)**.
2. Introduce la contraseña configurada como `ADMIN_PASSWORD`.
3. Importa tu archivo `enviar.js` o pega las IDs.
4. Pulsa **Enviar IDs a permisos** y confirma la operación.

El servidor elimina duplicados, procesa en bloques de 25 y devuelve el resumen de ambas cuentas.
