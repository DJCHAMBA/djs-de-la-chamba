# DJS DE LA CHAMBA para Android

La app Android abre la versión publicada en GitHub Pages dentro de una actividad WebView nativa.

## Funciones Android
- Icono adaptativo de DJS DE LA CHAMBA y pantalla de inicio propia.
- Selector nativo de Android para cargar MP3, WAV y otros audios compatibles.
- Exportaciones MP3/ZIP guardadas en `Descargas/DJS DE LA CHAMBA` en Android 10 o posterior.
- Botón Atrás de Android navega por la página.
- Los enlaces externos se abren en el navegador.
- La app requiere conexión a internet porque carga la web publicada y sus servicios.

## Identificador
`com.djchamba.djsdelachamba`

## Compilar
Desde la raíz del repositorio, con Android SDK 36, JDK 17 y Gradle 8.11.1:

```bash
gradle --no-daemon -p android assembleDebug
```

APK de prueba:
`android/app/build/outputs/apk/debug/app-debug.apk`

El workflow de GitHub Actions compila automáticamente un APK de depuración y lo publica como artefacto descargable. Este APK está firmado con la clave de depuración de Android; no es una compilación de publicación para Google Play.
