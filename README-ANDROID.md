# DJS DE LA CHAMBA Android nativo · 2.0

Esta versión es una aplicación Android nativa. Ya no usa WebView ni muestra el sitio de GitHub Pages dentro de la app.

## Funciones nativas
- Interfaz Android creada con controles nativos.
- Selector de archivos de Android (Storage Access Framework).
- Reproductor de vista previa.
- Dos métodos de procesamiento:
  - Principal: tempo 2.5x, tono 2.5x y ganancia -3 dB.
  - Audacity: tempo +8% y tono -3 semitonos.
- Intros opcionales en orden: Intro DJ, Intro secundaria, canción procesada.
- Exportación de audio a M4A/AAC con Media3 Transformer.
- Divisor de archivos en segmentos de 1 a 7 minutos y ZIP.
- Detector manual de entradas de voz y comienzos de canción con exportación CSV.
- Enlaces externos de Roblox abiertos en el navegador predeterminado.

## Límites conocidos de la versión nativa 2.0
- La salida de audio es M4A/AAC, no MP3. Media3 Transformer utiliza codificación AAC en el exportador configurado.
- El divisor genera partes M4A dentro de un ZIP.
- Detector: registra marcadores manuales; no reconoce canciones automáticamente.
- La publicación y administración de permisos de Roblox se abre en servicios oficiales externos; no se hacen dentro de la app.
- Se necesita Android 7.0/API 24 o posterior.

## Tecnología
Jetpack Media3 Transformer 1.11.1 para efectos y exportación local. Documentación: https://developer.android.com/media/media3/transformer

La app no contiene WebView, no solicita permiso INTERNET y procesa archivos seleccionados localmente.
