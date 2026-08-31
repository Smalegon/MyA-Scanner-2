# Escáner PDF

App Android sencilla para escanear hojas con la cámara, convertirlas en PDF,
guardarlas en una carpeta elegida del teléfono y compartirlas por WhatsApp,
correo, etc.

## Cómo obtener el instalador (.apk)

Este proyecto se compila solo, en la nube de GitHub, gracias al archivo
`.github/workflows/build-apk.yml`. No necesitas instalar Android Studio.

1. Sube esta carpeta completa a un repositorio nuevo en GitHub.
2. Entra a la pestaña **Actions** del repositorio.
3. Espera a que el flujo "Compilar APK" termine (unos 3-5 minutos).
4. Descarga el archivo `EscanerPDF-apk` desde esa misma página (sección
   **Artifacts** al final de la ejecución).
5. Pásalo a tu celular Android e instálalo (activa "Instalar apps de
   origen desconocido" si el sistema lo pide).

Guía detallada, sin usar la terminal, incluida por separado.

## Tecnología usada

- Kotlin + Android Views (sin dependencias raras).
- Google ML Kit Document Scanner (detección de bordes, recorte y PDF
  automáticos — no requiere clave de API).
- Storage Access Framework para guardar en la carpeta que elija el usuario.
- `FileProvider` para compartir el PDF de forma segura con otras apps.
