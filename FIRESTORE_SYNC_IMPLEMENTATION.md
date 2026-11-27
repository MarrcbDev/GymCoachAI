# Firestore sync implementation

This revision adds phone-side Firestore synchronization for authenticated accounts.

## Synced data

- User profile fields: name, sex, age, height, weight and goals.
- Routines.
- Reminders and alarms.
- Workout sessions, including sessions received from Wear OS by the phone.
- Health conditions only when the account's explicit health-sync consent is enabled.

## Kept local

- Chat messages and chat history (Room only).
- Personal OpenAI API key (Android Keystore only).
- Profile image file path. The image itself is not uploaded in this revision.

## Firestore layout

- `users/{uid}`: profile and `healthSyncConsent`.
- `users/{uid}/routines/{documentId}`
- `users/{uid}/reminders/{documentId}`
- `users/{uid}/sessions/{documentId}`
- `users/{uid}/health_conditions/{documentId}` (consent required)

Room database version 6 adds `sync_mappings`, preserving existing local data and mapping Room IDs to stable Firestore document IDs. No destructive Room migration is used.

## How it runs

- Sync starts after an authenticated account is loaded.
- Profile, routine, reminder and workout changes trigger sync.
- A manual **Sincronizar ahora** action is available under Settings.
- Phone syncs workout sessions received from the watch. The watch does not need its own Firebase login.
- If health-sync consent is revoked, the app attempts to delete the cloud health-condition documents before recording the revoked consent.

## Important notes

- `share_health` (whether the AI coach may use health context) is separate from `health_sync_consent` (whether health records may be stored in the user's Firebase account).
- Health synchronization defaults to off for a new account/device. A previously recorded account consent is respected on another device unless the user explicitly changes it there.
- Profile image upload to Firebase Storage is not included yet; image paths remain device-local.
- Sync requires Firebase Authentication, the app's `google-services.json` (provided to GitHub Actions through `GOOGLE_SERVICES_JSON`), and the deployed Firestore rules.
- This first sync layer does not provide a full offline conflict-resolution queue. If a deletion happens while offline, sync may need to be run again after reconnecting; review the result shown in Settings.
