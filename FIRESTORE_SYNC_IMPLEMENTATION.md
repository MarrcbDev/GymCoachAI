# Sincronización con Firestore (automática)

Los datos de la cuenta se sincronizan solos; ya no hay botón de "Sincronizar ahora".

## Qué se sincroniza

- Perfil: nombre, sexo, edad, estatura, peso, objetivos y **foto** (WebP 320 px ≈ 15–30 KB, guardada en el propio documento `users/{uid}`; no hace falta Firebase Storage).
- Rutinas, recordatorios/alarmas y sesiones de entrenamiento (incluidas las que llegan del reloj).
- Condiciones de salud, solo con el consentimiento explícito de la cuenta.

## Qué NO se sincroniza

- Chat (solo Room) y API Key personal (solo Android Keystore).

## Cuándo se sincroniza

- Unos segundos después de cada cambio local (se agrupan varios cambios en una sola sincronización).
- Al abrir la app o volver a ella.
- Cuando otro dispositivo cambia algo: cada escritura actualiza `syncRev`/`syncDevice` en `users/{uid}` y los demás dispositivos escuchan **solo ese documento**.
- Si falla (sin conexión, etc.) reintenta sola con espera creciente: 15 s, 30 s, 1 min… hasta 10 min.

## Cómo se resuelven los cambios

- Cada documento guarda una huella (`lastHash` en `sync_mappings`, Room v7). Solo se sube lo que cambió aquí y solo se baja lo que cambió en otro dispositivo; si no cambió nada no hay escrituras.
- Borrados: borrar en un dispositivo lo borra en los demás.
- Si el mismo elemento cambia en dos dispositivos a la vez, gana el dispositivo que está sincronizando.
- Perfil: gana la edición más reciente (`updatedAt` solo cambia cuando el usuario edita el perfil).

## Foto de perfil

- Se recorta al centro en cuadrado de 320 px y se guarda en WebP calidad 80 (antes: 768 px JPEG 90).
- Las fotos pesadas de versiones anteriores se recomprimen una sola vez al abrir la app.
- Se sube en base64 dentro de `users/{uid}.photo` junto con `photoHash`; solo se vuelve a subir si la foto cambia. Las reglas rechazan valores de más de 150 KB.

## Estructura en Firestore

- `users/{uid}`: perfil, `healthSyncConsent`, `photo`, `photoHash`, `syncRev`, `syncDevice`.
- `users/{uid}/routines|reminders|sessions/{documentId}`
- `users/{uid}/health_conditions/{documentId}` (requiere consentimiento)

## Despliegue

Las reglas cambiaron (validación del tamaño de la foto): `firebase deploy --only firestore:rules`.
