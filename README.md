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

## Cambios de la versión 2.3.0
- **Paquete nuevo: `com.marrdev.gymcoachai`** (app y reloj). Es una app distinta a la anterior: se instala aparte y empieza sin datos.
- Corregido el cierre al crear alarmas o notas con hora: en Android 14+ las alarmas exactas necesitan un permiso; ahora la app lo declara, avisa en Agenda y, si falta, usa una alarma aproximada en vez de cerrarse.
- Si la app se cierra por un error, al volver a abrirla muestra el informe para copiarlo.
- Lesiones y condiciones médicas guardadas localmente (Perfil). Si no hay ninguna anotada, se pregunta una sola vez. El coach las usa para adaptar ejercicios (se puede desactivar).
- Inicio de sesión opcional con Google o correo (Firebase). Con cuenta, el coach usa tu API Key a través de un backend (ver `BACKEND.md`); sin cuenta, registro local con la API Key propia. Se puede cambiar de fuente en Ajustes.
- `debug.keystore` fijo en el repo para que el SHA-1 de Google Sign-In no cambie entre builds.

## Cambios de la versión 2.3.0
- **Nuevo paquete:** `com.marrdev.gymcoachai` (app y reloj). Es una app distinta de la anterior: los datos guardados en `com.example.my_fitness_app` no pasan solos.
- **Corrección:** al guardar una nota o alarma con hora la app se cerraba. Android 12+ exige el permiso "Alarmas y recordatorios" para alarmas exactas; ahora se declara, se pide desde Agenda y, si no está permitido, la alarma se programa aproximada en vez de cerrar la app. Cualquier cierre inesperado se muestra al abrir de nuevo, con botón para copiar el informe.
- **Lesiones y condiciones médicas** (Perfil): lista local; se pregunta una sola vez al entrar si hay alguna. El coach las usa para adaptar ejercicios (se puede desactivar).
- **Cuenta opcional:** Google o correo, o registro local sin cuenta. Con cuenta el coach usa tu API Key del servidor (ver `BACKEND.md`); sin cuenta, cada usuario puede seguir usando la suya.
- Firma de depuración fija (`debug.keystore`) para que el SHA-1 de Firebase no cambie entre builds.

## Cambios de la versión 2.2.0
- Coach IA solo responde sobre fitness (entrenamiento, técnica, recuperación, hábitos). Fuera de ese ámbito lo rechaza y ofrece un tema relacionado.
- El coach puede crear rutinas, iniciar entrenamientos y planificar notas o alarmas. Si el usuario lo pide, lo hace; si no, lo propone con botones.
- Selector de modelo en Ajustes (lista, texto libre y carga de los modelos disponibles para tu API Key).
- Foto de perfil: elegir, cambiar y quitar. Se guarda reducida en el almacenamiento privado de la app.
- Rutinas y modos de entrenamiento con temporizador (trabajo/descanso/rondas), historial y sesiones recibidas del reloj.
- Agenda: notas planificadas y alarmas de entrenamiento con notificación real (AlarmManager).
- Base de datos v4 con migración (no se pierde perfil ni chat).

## Cambios de la versión 2.1.0
- Registro al abrir la app por primera vez (incluye selección de sexo). Si ya hay perfil, entra directo.
- Estilo Material You + AMOLED: colores dinámicos del sistema con fondo negro puro; se pueden apagar en Más → Apariencia para usar el azul y negro.
- Scroll en Rutinas, Más y Perfil; el teclado ya no tapa los campos.
- Iconos redondeados (rellenos al seleccionar, de contorno al no seleccionar) e icono de la app con capa monocromática.
- Wear OS: arreglado el cierre al abrir (la actividad estaba en un paquete distinto al namespace) y el `applicationId` ahora coincide con el del teléfono, requisito para que el Data Layer sincronice.
- Base de datos v3 con migración: añade `sex` sin borrar perfil ni chat.
- Guardar una API Key inválida ya no cierra la app: muestra el error.

## Qué incluye la base
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
La app mantiene el `applicationId` anterior `com.marrdev.gymcoachai` para facilitar una actualización sobre la instalación Flutter existente. No desinstales la app anterior antes de probar la migración: desinstalarla puede borrar `fitness_app.db`. Haz copia de seguridad antes de instalar esta versión de prueba.
