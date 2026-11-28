# Coach con tu API Key (inicio de sesión) — Cloudflare Workers

Con cuenta (Google o correo) el usuario usa **tu** API Key de OpenAI sin verla ni pegarla. La clave vive como
secreto en un Cloudflare Worker; la app solo envía su token de Firebase. Sin cuenta, el usuario usa su propia clave.

```
App ──(token de Firebase + conversación)──> Worker "gymcoachai-api" ──(tu API Key)──> OpenAI
                                             ├ valida la firma del token y que el correo esté verificado
                                             ├ límite diario por usuario (Durable Object)
                                             └ añade reglas fijas "solo fitness"
```

Firebase se sigue usando para el inicio de sesión y la sincronización (Authentication + Firestore), pero
**ya no necesitas el plan Blaze**: el backend corre en Cloudflare.

## 1. Firebase (una vez)
1. **Authentication → Sign-in method**: activa **Correo/contraseña** y **Google**.
2. **Project settings → tu app Android**: registra el SHA-1 de `debug.keystore` y descarga `google-services.json`.
3. **Firestore Database**: créala y despliega las reglas: `firebase deploy --only firestore:rules`.
4. Anota tu **Project ID** (Project settings → General). Debe coincidir con `FIREBASE_PROJECT_ID` en `worker/wrangler.toml`.

## 2. Cloudflare (una vez)
1. Crea una cuenta gratis en https://dash.cloudflare.com (no hace falta dominio propio).
2. Instala Node.js 20+ y, desde la carpeta `worker/`:
   ```bash
   cd worker
   npm install
   npx wrangler login                    # abre el navegador para autorizar
   ```

## 2b. Desde el celular (Termux) o sin instalar nada: GitHub Actions
`wrangler` no funciona en Termux (Android no está soportado). En su lugar, el workflow
`.github/workflows/deploy-worker.yml` despliega el Worker y guarda la clave por ti:
1. Cloudflare → My Profile → API Tokens → Create Token → plantilla **Edit Cloudflare Workers**.
2. Cloudflare → Workers & Pages → copia tu **Account ID** (y registra tu subdominio workers.dev si te lo pide).
3. GitHub → Settings → Secrets and variables → Actions → **Secrets** (no Variables): crea
   `CLOUDFLARE_API_TOKEN`, `CLOUDFLARE_ACCOUNT_ID` y `OPENAI_API_KEY`.
4. Sube los cambios o ejecuta el workflow a mano (Actions → Deploy Cloudflare Worker → Run workflow).
   La URL del Worker aparece en el registro del paso "Deploy Worker".

## 3. Configurar y desplegar (desde una computadora)
1. Abre `worker/wrangler.toml` y revisa `[vars]`: `FIREBASE_PROJECT_ID`, `ALLOWED_MODELS`, `DAILY_LIMIT`, `QUOTA_TIMEZONE`.
2. Guarda tu API Key de OpenAI como **secreto** (nunca en el archivo):
   ```bash
   npx wrangler secret put OPENAI_API_KEY      # pega tu clave cuando la pida
   ```
3. Despliega:
   ```bash
   npx wrangler deploy
   ```
   Imprime la URL, por ejemplo `https://gymcoachai-api.TU-USUARIO.workers.dev`. Esa URL (sin `/` final) es el `BACKEND_URL`.

## 4. Conectar la app
- **`google-services.json`**: como *secret* de GitHub `GOOGLE_SERVICES_JSON` (el workflow lo escribe antes de compilar).
- **`BACKEND_URL`**: en GitHub → Settings → Secrets and variables → Actions → pestaña **Variables** → `BACKEND_URL` = la URL del Worker.
  (o en `gradle.properties`: `BACKEND_URL=https://...`).
- Vuelve a ejecutar el workflow para generar el APK.

Sin `google-services.json` la app compila y funciona en modo local; sin `BACKEND_URL` el inicio de sesión funciona pero el coach sigue usando la API Key propia.

## 5. Probar
Con un token de Firebase válido (lo obtiene la app al iniciar sesión):
```bash
curl https://gymcoachai-api.TU-USUARIO.workers.dev/models                     # → 401 (sin sesión)
npx wrangler tail                                                             # ver los registros en vivo
```
Para probar en local: copia `worker/.dev.vars.example` a `worker/.dev.vars`, pon tu clave y ejecuta `npx wrangler dev`.

## 6. Antes de publicar
- Pon un **límite de gasto mensual** en tu cuenta de OpenAI.
- El límite diario y los modelos se cambian en `wrangler.toml` y con un nuevo `npx wrangler deploy`.
- Los usuarios con correo/contraseña deben verificar su correo para usar el servicio incluido (evita cuentas desechables).
- Plan gratuito de Workers: 100 000 solicitudes al día. Si lo superas, activa el plan de pago de Cloudflare (5 USD/mes).
