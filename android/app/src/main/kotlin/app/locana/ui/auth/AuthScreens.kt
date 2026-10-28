package app.locana.ui.auth

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.locana.BuildConfig
import app.locana.ui.components.PrimaryButton
import app.locana.ui.components.SecondaryButton
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

@Composable
internal fun AuthColumn(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 480.dp).fillMaxWidth()) { content() }
    }
}

/** Wordmark set in the display face. The final logo is still to come (see docs). */
@Composable
internal fun Wordmark() {
    Text("Locana", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
internal fun FormError(text: String?) {
    if (text == null) return
    Text(
        text,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
internal fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    error: String? = null,
    password: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
    capitalize: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (password) KeyboardType.Password else keyboard,
            capitalization = if (capitalize) KeyboardCapitalization.Words else KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }, onGo = { onDone() }),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}

@Composable
fun LoginScreen(vm: AuthViewModel, expiredReason: String?, onRegister: () -> Unit, onForgot: () -> Unit) {
    val f by vm.form.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    AuthColumn {
        Spacer(Modifier.height(32.dp))
        Wordmark()
        Text("Sign in to see your circles.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        (f.info ?: expiredReason)?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 16.dp))
        }
        Spacer(Modifier.height(24.dp))
        Field(f.identifier, { v -> vm.edit { it.copy(identifier = v) } }, "Email or username", keyboard = KeyboardType.Email)
        Field(f.password, { v -> vm.edit { it.copy(password = v) } }, "Password", password = true, imeAction = ImeAction.Go, onDone = vm::login)
        FormError(f.error)
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Sign in", vm::login, Modifier.fillMaxWidth(), loading = f.busy)
        TextButton(onClick = onForgot, modifier = Modifier.align(Alignment.End)) { Text("Forgot password?") }
        if (BuildConfig.GOOGLE_SERVER_CLIENT_ID.isNotBlank()) {
            SecondaryButton(
                "Continue with Google",
                onClick = {
                    scope.launch {
                        val token = googleIdToken(context as Activity)
                        if (token != null) vm.google(token)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("New to Locana?", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onRegister) { Text("Create an account") }
        }
    }
}

/** Google ID token through Credential Manager; the server verifies it before trusting anything. */
private suspend fun googleIdToken(activity: Activity): String? = runCatching {
    val option = GetGoogleIdOption.Builder()
        .setServerClientId(BuildConfig.GOOGLE_SERVER_CLIENT_ID)
        .setFilterByAuthorizedAccounts(false)
        .build()
    val result = CredentialManager.create(activity).getCredential(activity, GetCredentialRequest.Builder().addCredentialOption(option).build())
    val c = result.credential
    if (c is CustomCredential && c.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) GoogleIdTokenCredential.createFrom(c.data).idToken else null
}.getOrNull()

@Composable
fun RegisterScreen(vm: AuthViewModel, onLogin: () -> Unit) {
    val f by vm.form.collectAsStateWithLifecycle()
    AuthColumn {
        Spacer(Modifier.height(24.dp))
        Text("Create your account", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Nothing is shared when you sign up. You choose who sees your location, and for how long, later.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
        )
        Field(f.displayName, { v -> vm.edit { it.copy(displayName = v) } }, "Your name", f.fieldErrors["displayName"], capitalize = true)
        Field(f.username, { v -> vm.edit { it.copy(username = v.lowercase()) } }, "Username", f.fieldErrors["username"])
        Field(f.email, { v -> vm.edit { it.copy(email = v) } }, "Email", f.fieldErrors["email"], keyboard = KeyboardType.Email)
        Field(f.password, { v -> vm.edit { it.copy(password = v) } }, "Password (10+ characters)", f.fieldErrors["password"], password = true, imeAction = ImeAction.Go, onDone = vm::register)
        FormError(f.error)
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Create account", vm::register, Modifier.fillMaxWidth(), loading = f.busy)
        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Already have an account?", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onLogin) { Text("Sign in") }
        }
    }
}

@Composable
fun ForgotPasswordScreen(vm: AuthViewModel, onDone: () -> Unit, onBack: () -> Unit) {
    val f by vm.form.collectAsStateWithLifecycle()
    AuthColumn {
        Spacer(Modifier.height(24.dp))
        Text("Reset your password", style = MaterialTheme.typography.headlineMedium)
        if (f.resetStep == 0) {
            Text("We'll email you a code.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
            Field(f.email, { v -> vm.edit { it.copy(email = v) } }, "Email", f.fieldErrors["email"], keyboard = KeyboardType.Email, imeAction = ImeAction.Go, onDone = vm::sendResetCode)
            FormError(f.error)
            Spacer(Modifier.height(20.dp))
            PrimaryButton("Email me a code", vm::sendResetCode, Modifier.fillMaxWidth(), loading = f.busy)
        } else {
            f.info?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)) }
            Field(f.code, { v -> vm.edit { it.copy(code = v.uppercase()) } }, "Code from the email")
            Field(f.password, { v -> vm.edit { it.copy(password = v) } }, "New password", f.fieldErrors["password"], password = true, imeAction = ImeAction.Go, onDone = { vm.resetPassword(onDone) })
            FormError(f.error)
            Spacer(Modifier.height(20.dp))
            PrimaryButton("Set new password", { vm.resetPassword(onDone) }, Modifier.fillMaxWidth(), loading = f.busy)
            TextButton(onClick = { vm.edit { it.copy(resetStep = 0) } }) { Text("Send another code") }
        }
        TextButton(onClick = onBack) { Text("Back to sign in") }
    }
}

@Composable
fun OnboardingScreen(onCreate: () -> Unit, onSignIn: () -> Unit) {
    AuthColumn {
        Spacer(Modifier.height(40.dp))
        Wordmark()
        Text(
            "Share where you are with the people you trust, and turn everyday walks into a map you reveal together.",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 12.dp),
        )
        Spacer(Modifier.height(28.dp))
        Text("Your circle", style = MaterialTheme.typography.titleMedium)
        Text(
            "A family, a partner, close friends. Each circle sees you only while you share with it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text("The game", style = MaterialTheme.typography.titleMedium)
        Text(
            "New streets uncover map tiles. Public landmarks, quests and friendly territory rounds earn XP for you and your circle.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // The privacy promise gets its own panel: it is what people need to believe before granting location.
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        ) {
            Row(Modifier.padding(16.dp)) {
                Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.padding(top = 2.dp, end = 12.dp))
                Column {
                    Text("Nothing is shared until you start it", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Pause with one tap, set sharing to end on its own, or hide from one person without leaving the circle.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        Spacer(Modifier.height(32.dp))
        PrimaryButton("Create an account", onCreate, Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        SecondaryButton("I already have one", onSignIn, Modifier.fillMaxWidth())
    }
}
