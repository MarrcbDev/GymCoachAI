# GymCoachAI Native — Android + Wear OS

Migración inicial de Flutter/Dart a Kotlin nativo. Proyecto Android Studio con dos módulos:
- `:app`: aplicación principal Android con Jetpack Compose.
- `:wear`: companion Wear OS con Compose for Wear OS.

## Abrir
1. Extrae el ZIP.
2. Abre la carpeta `GymCoachAI-Native` desde Android Studio.
3. Usa JDK 17 y deja que Gradle sincronice las dependencias.
4. Ejecuta `app` en un teléfono/emulador Android.
5. Ejecuta `wear` en un emulador Wear OS o reloj compatible.

## Qué incluye esta primera base
- UI oscura azul/negro en Jetpack Compose.
- Navegación Inicio / Coach IA / Rutinas / Más.
- Perfil con los campos existentes: nombre, edad, estatura, peso y objetivos.
- Importación de lectura de la fila del usuario desde la antigua base Flutter `fitness_app.db` (si sigue disponible en el sandbox de datos de la app y no se ha desinstalado).
- Persistencia local del historial de chat con Room.
- Chat con endpoint Chat Completions y modelo `gpt-4o-mini`.
- Campo para introducir la API Key; se cifra con AES/GCM y Android Keystore.
- Aplicación Wear OS de inicio/fin de sesión y envío del resumen al teléfono con Data Layer.

## API Key
En la app: Más → OpenAI → pega tu clave `sk-...` → Guardar.
No escribas la clave en el código fuente ni la subas a Git. El cifrado local evita guardarla en texto plano, pero una clave cliente sigue siendo extraíble por un atacante con control del dispositivo. Para publicar la app, conviene interponer un backend propio.

## Importante: estado de esta entrega
Es un **andamiaje de migración funcional inicial**, no una versión final lista para Play Store. El flujo de chat y el envío básico de sesiones están implementados, pero todavía faltan:
- Persistencia completa y CRUD de recordatorios y alarmas.
- Permisos y captura real de pasos/frecuencia cardíaca/energía con Health Services.
- Guardar en Room las sesiones recibidas desde Wear OS (ahora se guardan como último resumen recibido en preferencias).
- Gestión robusta de permisos de notificaciones, alarmas exactas y ejecución en segundo plano.
- Pruebas de migración de la base SQLite en un dispositivo con la versión Flutter instalada.
- Historial con selector de conversaciones, streaming, cancelación y manejo detallado de errores de API.

## Compatibilidad de datos
La app mantiene el `applicationId` anterior `com.example.my_fitness_app` para facilitar una actualización sobre la instalación Flutter existente. No desinstales la app anterior antes de probar la migración: desinstalarla puede borrar `fitness_app.db`. Haz copia de seguridad antes de instalar esta versión de prueba.
