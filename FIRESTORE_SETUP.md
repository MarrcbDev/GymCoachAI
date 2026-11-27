# GymCoachAI — primera integración de Cloud Firestore

Esta entrega prepara el SDK Android y las reglas iniciales de seguridad. Todavía no migra ni sincroniza datos de Room; eso se hará en la siguiente fase después de definir los IDs estables y la migración de base de datos.

## Archivos incluidos

- `app/build.gradle.kts`: añade Firebase Firestore usando el Firebase BoM ya existente.
- `firestore.rules`: limita el acceso a los documentos del UID autenticado y exige consentimiento para `health_conditions`.
- `firebase.json`: registra el archivo de reglas para desplegarlo con Firebase CLI.

## Desplegar reglas

Desde la raíz del repositorio:

```bash
firebase login
firebase use --add
```

Selecciona exactamente el mismo proyecto Firebase que ya contiene tus dos usuarios de Authentication y la base Firestore que acabas de crear. Después:

```bash
firebase deploy --only firestore:rules
```

Si ya tienes el alias del proyecto configurado, no necesitas repetir `firebase use --add`.

## Estructura que estas reglas permiten

```text
users/{uid}
users/{uid}/routines/{documentId}
users/{uid}/reminders/{documentId}
users/{uid}/sessions/{documentId}
users/{uid}/health_conditions/{documentId}
usage/{documentId}   # solo Admin SDK / Cloud Functions
```

El documento `users/{uid}` debe incluir `healthSyncConsent` como booleano (`false` por defecto). Las reglas bloquean la colección `health_conditions` si no es `true`.

No se crea una colección `chats`: las conversaciones permanecen en Room/SQLite.

## Después de desplegar

1. Añade/reemplaza `app/build.gradle.kts` en tu repositorio.
2. Sincroniza Gradle y compila Android.
3. No cambies todavía `Data.kt` ni `GymViewModel.kt`: la siguiente fase añadirá IDs estables, migración Room no destructiva y sincronización segura por cuenta.
