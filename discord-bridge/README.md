# Conector DJS DE LA CHAMBA ↔ LA CHAMBA STUDIOS

Permite que la página DJS DE LA CHAMBA y tu bot de Discord envíen comandos al mismo puente Roblox.

## Requisitos
- Windows 10/11 y Node.js 20 o posterior.
- Tu puente Roblox actual debe seguir escuchando en http://127.0.0.1:3000/comando.
- El bot Discord puede seguir usando esa misma dirección.

## Instalación
1. Abre una terminal en esta carpeta.
2. Ejecuta npm install.
3. Copia .env.example a .env.
4. Genera un token: node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"
5. Pega el resultado en WEB_BRIDGE_TOKEN dentro de .env.
6. Comprueba ROBLOX_BRIDGE_URL y ejecuta npm start.
7. Mantén abierta la terminal mientras uses el panel web.

## Conectar
1. Abre https://djchamba.github.io/djs-de-la-chamba/.
2. Entra a la pestaña «La Chamba Studios».
3. Inicia sesión con la contraseña de administrador.
4. Pega el mismo WEB_BRIDGE_TOKEN de .env y pulsa Conectar.
5. Si Chrome pide permiso para acceder a la red local, autorízalo.

## Seguridad
- El conector escucha solo en 127.0.0.1.
- Solo acepta el origen https://djchamba.github.io.
- Exige token local Bearer y valida comandos permitidos.
- No contiene el token Discord ni la API key de Roblox.
- Los comandos de permisos de Asset IDs siguen en el panel privado de la página.
- No expongas el archivo .env.

Discord y la página envían comandos al mismo puente de Roblox en localhost:3000. Este conector añade el endpoint autenticado localhost:3001 para el navegador; no sustituye el puente original.
