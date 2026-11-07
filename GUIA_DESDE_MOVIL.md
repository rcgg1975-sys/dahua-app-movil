# Generar el APK desde el móvil (sin PC)

Este proyecto incluye un flujo de compilación en GitHub Actions. GitHub compila el APK en sus servidores; el móvil solo necesita navegador.

## Pasos
1. Descarga el ZIP del proyecto y descomprímelo en el móvil (Mis archivos > Descargas > ZIP > Extraer).
2. En Chrome abre https://github.com/ y crea una cuenta o inicia sesión.
3. Crea un repositorio nuevo llamado `DahuaPortTester` (puede ser privado).
4. Entra en el repositorio y usa **Add file > Upload files**. Sube el CONTENIDO de la carpeta `DahuaPortTesterAndroid` extraída, incluida la carpeta oculta `.github` y su subcarpeta `workflows`.
   - Si la web móvil no permite subir la carpeta oculta, crea manualmente `.github/workflows/build-apk.yml` y pega el contenido del archivo incluido en el ZIP.
5. Confirma con **Commit changes** en la rama `main`.
6. Abre la pestaña **Actions**, selecciona **Build APK** y, si no se ha iniciado automáticamente, pulsa **Run workflow**.
7. Espera a que el trabajo termine en verde. Abre esa ejecución y baja a **Artifacts**.
8. Descarga `Dahua-Port-Tester-debug-apk`, descomprime el ZIP descargado y abre `app-debug.apk`.
9. Android puede pedir permiso para instalar aplicaciones desde Chrome o Mis archivos. Autorízalo solo para esta instalación y continúa.

## Importante
- La compilación es de depuración (debug), suficiente para instalar y probar en tu propio móvil; no es una publicación firmada para Play Store.
- El APK aún debe probarse en el teléfono y pueden aparecer errores de compilación o ajustes pendientes. Si GitHub muestra un error, comparte una captura o el registro para corregirlo.
- Para compilar, GitHub descarga dependencias y ejecuta el workflow definido en `.github/workflows/build-apk.yml`.
- El escaneo solo debe utilizarse en redes propias o con autorización. Android puede limitar la visibilidad de MAC y algunos datos de los dispositivos.
