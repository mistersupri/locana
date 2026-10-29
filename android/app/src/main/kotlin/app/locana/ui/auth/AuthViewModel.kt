package app.locana.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.locana.data.api.AppError
import app.locana.data.prefs.SettingsStore
import app.locana.data.repo.AuthRepository
import app.locana.domain.Validation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthForm(
    val identifier: String = "",
    val email: String = "",
    val password: String = "",
    val username: String = "",
    val displayName: String = "",
    val code: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val resetStep: Int = 0,
    val info: String? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(private val auth: AuthRepository, private val settings: SettingsStore) : ViewModel() {
    private val _form = MutableStateFlow(AuthForm())
    val form: StateFlow<AuthForm> = _form.asStateFlow()

    fun edit(f: (AuthForm) -> AuthForm) = _form.update { f(it).copy(error = null) }

    fun markOnboardingSeen() = viewModelScope.launch { settings.setOnboardingSeen() }

    private fun messageFor(e: Throwable): String = when (val err = e as? AppError) {
        null -> "Something went wrong"
        else -> when (err.codeOrNull) {
            "USERNAME_TAKEN" -> "That username is taken. Try another."
            "EMAIL_TAKEN" -> "An account with this email exists. Sign in instead."
            "RATE_LIMITED" -> "Too many attempts. Wait a few minutes and try again."
            else -> err.message ?: "Something went wrong"
        }
    }

    private fun run(block: suspend () -> Result<*>, onOk: () -> Unit = {}) {
        _form.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val r = block()
            _form.update { it.copy(busy = false, error = r.exceptionOrNull()?.let(::messageFor)) }
            if (r.isSuccess) onOk()
        }
    }

    fun login() {
        val f = _form.value
        if (f.identifier.isBlank() || f.password.isEmpty()) {
            _form.update { it.copy(error = "Enter your email or username and password") }
            return
        }
        run({ auth.login(f.identifier, f.password) }, onOk = ::clearSecrets)
    }

    fun register() {
        val f = _form.value
        val errors = buildMap {
            Validation.displayName(f.displayName)?.let { put("displayName", it) }
            Validation.username(f.username)?.let { put("username", it) }
            Validation.email(f.email)?.let { put("email", it) }
            Validation.password(f.password)?.let { put("password", it) }
        }
        _form.update { it.copy(fieldErrors = errors) }
        if (errors.isNotEmpty()) return
        run({ auth.register(f.email, f.password, f.username, f.displayName) }, onOk = ::clearSecrets)
    }

    fun google(idToken: String) = run({ auth.google(idToken) }, onOk = ::clearSecrets)

    /** Typed passwords must not outlive the sign-in, nor reappear after signing out. */
    private fun clearSecrets() {
        _form.value = AuthForm()
    }

    fun sendResetCode() {
        val f = _form.value
        Validation.email(f.email)?.let { e -> _form.update { it.copy(fieldErrors = mapOf("email" to e)) }; return }
        run({ auth.forgot(f.email) }) {
            _form.update { it.copy(resetStep = 1, fieldErrors = emptyMap(), info = "If an account uses ${f.email}, a code is on its way. It works for 15 minutes.") }
        }
    }

    fun resetPassword(onDone: () -> Unit) {
        val f = _form.value
        Validation.password(f.password)?.let { e -> _form.update { it.copy(fieldErrors = mapOf("password" to e)) }; return }
        run({ auth.reset(f.email, f.code, f.password) }) {
            _form.update { AuthForm(identifier = f.email, info = "Password changed. Sign in with the new one.") }
            onDone()
        }
    }
}
