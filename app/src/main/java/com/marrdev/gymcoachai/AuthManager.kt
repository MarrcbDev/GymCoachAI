package com.marrdev.gymcoachai

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

data class AccountInfo(
    val uid: String,
    val email: String,
    val name: String,
    val emailVerified: Boolean,
    val provider: String, // "google" | "email"
)

/**
 * Envoltorio de Firebase Auth. Si la app se compila sin google-services.json, [available] es false
 * y todo el resto de la app sigue funcionando en modo local.
 */
class AuthManager(private val context: Context) {
    val available: Boolean = runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)

    private val auth: FirebaseAuth? get() = if (available) FirebaseAuth.getInstance() else null

    /** ID de cliente web generado por el plugin google-services; 0 si Google no está configurado. */
    private val webClientIdRes: Int
        get() = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)

    val googleAvailable: Boolean get() = available && webClientIdRes != 0

    fun current(): AccountInfo? = auth?.currentUser?.toInfo()

    fun listen(onChange: (AccountInfo?) -> Unit) {
        auth?.addAuthStateListener { onChange(it.currentUser?.toInfo()) }
    }

    private fun FirebaseUser.toInfo() = AccountInfo(
        uid = uid,
        email = email.orEmpty(),
        name = displayName.orEmpty(),
        emailVerified = isEmailVerified,
        provider = if (providerData.any { it.providerId == "google.com" }) "google" else "email",
    )

    suspend fun signInWithGoogle(activity: Activity): Result<Unit> = guarded {
        val a = auth ?: error("Firebase no está configurado.")
        if (webClientIdRes == 0) error("Google no está configurado en Firebase (falta el cliente web en google-services.json).")
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(context.getString(webClientIdRes))
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = CredentialManager.create(activity).getCredential(activity, request).credential
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
            a.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
        } else {
            error("Google devolvió un tipo de credencial no compatible.")
        }
    }

    suspend fun signInWithEmail(email: String, password: String): Result<Unit> = guarded {
        (auth ?: error("Firebase no está configurado.")).signInWithEmailAndPassword(email.trim(), password).await()
    }

    /** Crea la cuenta y envía el correo de verificación. */
    suspend fun signUpWithEmail(email: String, password: String): Result<Unit> = guarded {
        val a = auth ?: error("Firebase no está configurado.")
        val result = a.createUserWithEmailAndPassword(email.trim(), password).await()
        result.user?.sendEmailVerification()?.await()
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> = guarded {
        (auth ?: error("Firebase no está configurado.")).sendPasswordResetEmail(email.trim()).await()
    }

    suspend fun resendVerification(): Result<Unit> = guarded {
        auth?.currentUser?.sendEmailVerification()?.await()
    }

    /** Recarga el usuario (para ver si ya verificó el correo) y devuelve su estado. */
    suspend fun reload(): AccountInfo? {
        runCatching { auth?.currentUser?.reload()?.await() }
        return current()
    }

    /** Token de identidad que el backend valida. forceRefresh=true tras verificar el correo. */
    suspend fun idToken(forceRefresh: Boolean = false): String? =
        runCatching { auth?.currentUser?.getIdToken(forceRefresh)?.await()?.token }.getOrNull()

    fun signOut() {
        auth?.signOut()
    }

    private suspend fun guarded(block: suspend () -> Unit): Result<Unit> = try {
        block()
        Result.success(Unit)
    } catch (e: GetCredentialCancellationException) {
        Result.failure(Exception("Cancelaste el inicio de sesión."))
    } catch (e: NoCredentialException) {
        Result.failure(Exception("No hay ninguna cuenta de Google disponible en este dispositivo."))
    } catch (e: GetCredentialException) {
        Result.failure(Exception("No se pudo iniciar sesión con Google: ${e.message ?: e.type}"))
    } catch (e: FirebaseNetworkException) {
        Result.failure(Exception("Sin conexión. Revisa tu internet."))
    } catch (e: FirebaseAuthException) {
        Result.failure(Exception(spanish(e.errorCode, e.message)))
    } catch (e: Exception) {
        Result.failure(Exception(e.message ?: "Ocurrió un error inesperado."))
    }

    private fun spanish(code: String, fallback: String?): String = when (code) {
        "ERROR_INVALID_EMAIL" -> "El correo no es válido."
        "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL", "ERROR_USER_NOT_FOUND" -> "Correo o contraseña incorrectos."
        "ERROR_EMAIL_ALREADY_IN_USE" -> "Ya existe una cuenta con ese correo. Inicia sesión."
        "ERROR_WEAK_PASSWORD" -> "La contraseña es muy débil (mínimo 6 caracteres)."
        "ERROR_TOO_MANY_REQUESTS" -> "Demasiados intentos. Espera un momento e inténtalo de nuevo."
        "ERROR_USER_DISABLED" -> "Esta cuenta está deshabilitada."
        else -> fallback ?: "No se pudo completar la operación ($code)."
    }
}
