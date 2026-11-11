# DJS DE LA CHAMBA para Windows

Aplicación de escritorio para Windows. Abre la página oficial de DJS DE LA CHAMBA en una ventana dedicada de Electron:

https://djchamba.github.io/djs-de-la-chamba/

## Funciones de escritorio

- Ventana nativa con icono DJS DE LA CHAMBA.
- Instalador NSIS con asistente, opción para elegir carpeta y accesos directos.
- Selector de archivos de audio de Chromium/Electron.
- MP3 y ZIP guardados en Descargas / DJS DE LA CHAMBA.
- Avisos de descarga terminada.
- Enlaces externos abiertos en el navegador predeterminado.
- Pantalla sin conexión con botón para reintentar.

La aplicación necesita conexión a internet porque usa la página oficial y sus servicios. La lógica de edición y las herramientas siguen en la web, así las actualizaciones del sitio se reflejan en escritorio sin reinstalar.

## Crear instalador en Windows

Requisitos: Node.js 22 o posterior.

Desde esta carpeta ejecuta npm install y después npm run dist:win.

La salida será:

dist/DJS-DE-LA-CHAMBA-Setup-1.0.0-x64.exe

El instalador generado por GitHub Actions es una compilación de prueba sin certificado de firma comercial. Windows SmartScreen puede mostrar una advertencia al ejecutarlo. Para distribución pública sin esa advertencia hace falta firmar el instalador con un certificado de firma de código.
