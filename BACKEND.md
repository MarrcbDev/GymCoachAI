# Coach con tu API Key (inicio de sesión) — guía de integración

Con cuenta (Google o correo) el usuario usa **tu** API Key de OpenAI sin verla ni pegarla. La clave vive como
secreto en una Firebase Function; la app solo envía su token de Firebase. El usuario sin cuenta sigue usando su propia clave.

```
App ──(token de Firebase + conversación)──> Function "api" ──(tu API Key)──> OpenAI
                                             ├ valida el token y que el correo esté verificado
                                             ├ límite diario por usuario (Firestore)
                                             └ añade reglas fijas "solo fitness"
```

## 1. Firebase (una vez)
1. **Plan Blaze** (las Functions y los secretos lo requieren; el uso pequeño cuesta casi nada).
2. **Authentication → Sign-in method**: activa **Correo/contraseña** y **Google**.
3. **Project settings → tu app Android** (`com.marrdev.gymcoachai`): añade la huella **SHA-1** del `debug.keystore` incluido en este repo:
   `BA:14:D9:2D:27:11:DD:75:6D:38:C1:50:E6:C2:50:6D:92:28:EC:45`
   y descarga de nuevo `google-services.json` (debe incluir un cliente web de Google).
4. **Firestore Database**: créala en modo producción (el backend usa el Admin SDK y no necesita reglas abiertas).

> Si compilas con otro keystore (por ejemplo uno de release), registra también su SHA-1, o Google Sign-In fallará.

## 2. Desplegar el backend
```bash
npm install -g firebase-tools && firebase login
firebase use --add                       # elige tu proyecto
cp functions/.env.example functions/.env # ajusta ALLOWED_MODELS, DAILY_LIMIT…
firebase functions:secrets:set OPENAI_API_KEY   # pega tu clave de OpenAI
cd functions && npm install && cd ..
firebase deploy --only functions
```
El deploy imprime la **URL de la función `api`**. Esa URL (sin `/` final) es el valor de `BACKEND_URL`.

## 3. Conectar la app
- **`app/google-services.json`**: ponlo en el repo, o guárdalo como *secret* de GitHub `GOOGLE_SERVICES_JSON` (el workflow lo escribe antes de compilar).
- **`BACKEND_URL`**: en `gradle.properties` (`BACKEND_URL=https://...`) o como *variable* de GitHub Actions `BACKEND_URL`.

Sin `google-services.json` la app compila y funciona en modo local; sin `BACKEND_URL` el inicio de sesión funciona pero el coach sigue usando la API Key propia.

## 4. Antes de publicar
- Pon un **límite de gasto mensual** en tu cuenta de OpenAI.
- Valora activar **Firebase App Check** (Play Integrity) para que solo tu app pueda llamar a la función.
- El límite diario y los modelos permitidos se cambian en `functions/.env` y un nuevo `firebase deploy`.
- Los usuarios con correo/contraseña deben verificar su correo para usar el servicio incluido (evita cuentas desechables).
