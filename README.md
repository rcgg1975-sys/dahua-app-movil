# Dahua Port Tester para Android

Proyecto Android nativo (Kotlin + Jetpack Compose), preparado para abrir con Android Studio.

## Funciones incluidas
- Test TCP de dos puertos contra una IP de la LAN.
- Consulta de IP pública WAN mediante ipify.
- Test TCP desde nodos externos mediante Check-Host, para no depender del NAT Loopback.
- Escaneo de subred IPv4 configurable, detección automática del CIDR Wi-Fi, puertos habituales (80, 443, 554, 37777, 37778, 8000, 8080, 23 y 22), nombre DNS y lectura MAC cuando Android lo permite.
- Barra de progreso, contador `IP X de Y`, IP actual y direcciones pendientes.
- Límite de seguridad: CIDR entre /16 y /30, máximo 4094 hosts.

## Crear el APK
1. Instala Android Studio (versión reciente) en un ordenador.
2. Abre esta carpeta como proyecto existente.
3. Espera a que Gradle sincronice y descargue las dependencias.
4. Selecciona **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. El APK de depuración aparecerá en `app/build/outputs/apk/debug/app-debug.apk`.

## Limitaciones conocidas
- El entorno donde se preparó este proyecto no dispone de Android SDK ni Gradle; por eso se entrega el proyecto fuente y no se afirma que exista un APK ya compilado.
- Android limita la visibilidad de MAC y de ciertos datos de red. La tabla ARP puede estar vacía o incompleta.
- Algunos equipos no responden a ICMP y tampoco exponen los puertos sondeados, por lo que pueden no aparecer.
- La etiqueta Dahua es orientativa: los puertos 37777/37778 son indicios, no una identificación concluyente.
- La prueba WAN depende de la disponibilidad y política de Check-Host.

Escanea únicamente redes y equipos propios o para los que tengas autorización.

## Compilar desde un móvil
Consulta `GUIA_DESDE_MOVIL.md`. El proyecto incluye un workflow de GitHub Actions que compila `app-debug.apk` en la nube y lo publica como Artifact de la ejecución.
