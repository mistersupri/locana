package app.locana.ui.auth

import app.locana.ui.theme.cardBrush
import android.app.Activity
import androidx.compose.foundation.layout.Column
import app.locana.ui.theme.Palette
import app.locana.ui.theme.LocalTokens
import app.locana.ui.components.Illustration
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
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
import app.locana.ui.components.HeroPanel
import app.locana.ui.components.TileField
import app.locana.ui.theme.LocalHero
import app.locana.ui.components.SecondaryButton
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

@Composable
internal fun AuthColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 480.dp).fillMaxWidth()) { content() }
    }
}

/** Wordmark: the pin and the name in the display face. */
@Composable
internal fun Wordmark(color: Color = MaterialTheme.colorScheme.primary, size: androidx.compose.ui.unit.TextUnit = 24.sp) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Outlined.Place, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Text("Locana", style = MaterialTheme.typography.headlineSmall.copy(fontSize = size), color = color)
    }
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

/**
 * Text field from the mockup: the label sits above a white rounded box with a soft outline; an error turns the
 * box coral with a small alert icon and the message below it. The label is also the accessibility name.
 */
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
    val t = LocalTokens.current
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (error != null) t.coralDark else scheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp, start = 2.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface, fontWeight = FontWeight.SemiBold),
            cursorBrush = SolidColor(scheme.primary),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (password) KeyboardType.Password else keyboard,
                capitalization = if (capitalize) KeyboardCapitalization.Words else KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(onDone = { onDone() }, onGo = { onDone() }),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            decorationBox = { inner ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (error != null) t.coralSoft else t.card)
                        .border(2.dp, if (error != null) t.coral else t.line, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) { inner() }
                    if (error != null) Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = t.coral, modifier = Modifier.size(18.dp))
                }
            },
        )
        if (error != null) Text(error, style = MaterialTheme.typography.bodySmall, color = t.coralDark, modifier = Modifier.padding(top = 4.dp, start = 2.dp).semantics { liveRegion = LiveRegionMode.Polite })
    }
}

/** A link-style action in the primary colour. */
@Composable
internal fun LinkText(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 6.dp, vertical = 8.dp),
    )
}

@Composable
fun LoginScreen(vm: AuthViewModel, expiredReason: String?, onRegister: () -> Unit, onForgot: () -> Unit) {
    val f by vm.form.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    AuthColumn {
        // Violet hero card with the wordmark and the mascot.
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(Brush.linearGradient(listOf(Palette.Violet700, Color(0xFF9A6BFF))))
                .padding(24.dp),
        ) {
            Column {
                Wordmark(Color.White, 28.sp)
                Text("Sign in to see your circles.", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.9f), modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(110.dp))
            }
            Illustration("mascot", Modifier.align(Alignment.BottomEnd), size = 120.dp)
        }
        (f.info ?: expiredReason)?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 16.dp))
        }
        Field(f.identifier, { v -> vm.edit { it.copy(identifier = v) } }, "Email or username", keyboard = KeyboardType.Email)
        Field(f.password, { v -> vm.edit { it.copy(password = v) } }, "Password", password = true, imeAction = ImeAction.Go, onDone = vm::login)
        FormError(f.error)
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Sign in", vm::login, Modifier.fillMaxWidth(), loading = f.busy)
        LinkText("Forgot password?", onForgot, Modifier.align(Alignment.Start))
        if (BuildConfig.GOOGLE_SERVER_CLIENT_ID.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
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
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            Text("New to Locana?", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinkText("Create an account", onRegister)
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
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("Create your account", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "You are shared with a circle only once you join or create one. You can hide from a person, or leave, at any time.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Illustration("mailbox", size = 84.dp)
        }
        Field(f.displayName, { v -> vm.edit { it.copy(displayName = v) } }, "Your name", f.fieldErrors["displayName"], capitalize = true)
        Field(f.username, { v -> vm.edit { it.copy(username = v.lowercase()) } }, "Username", f.fieldErrors["username"])
        Field(f.email, { v -> vm.edit { it.copy(email = v) } }, "Email", f.fieldErrors["email"], keyboard = KeyboardType.Email)
        Field(f.password, { v -> vm.edit { it.copy(password = v) } }, "Password (10+ characters)", f.fieldErrors["password"], password = true, imeAction = ImeAction.Go, onDone = vm::register)
        FormError(f.error)
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Create account", vm::register, Modifier.fillMaxWidth(), loading = f.busy)
        Row(Modifier.padding(top = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text("Already have an account?", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinkText("Sign in", onLogin)
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
            LinkText("Send another code", { vm.edit { it.copy(resetStep = 0) } })
        }
        LinkText("Back to sign in", onBack)
    }
}

@Composable
fun OnboardingScreen(onCreate: () -> Unit, onSignIn: () -> Unit) {
    val t = LocalTokens.current
    AuthColumn {
        // Hero card: gradient, the brand, the promise, and the city with the mascot and a "+1 tile" sticker.
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(cardBrush(Color(0xFFF1EAFF), Color(0xFFFFF4DA)))
                .border(3.dp, Color.White, RoundedCornerShape(32.dp))
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Wordmark()
            Text(
                "Share where you are with the people you trust, and turn everyday walks into a map you reveal together.",
                style = MaterialTheme.typography.headlineSmall.copy(lineHeight = 25.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Box(Modifier.fillMaxWidth().height(200.dp)) {
                Illustration("city", Modifier.align(Alignment.Center), size = 200.dp)
                Illustration("mascot", Modifier.align(Alignment.BottomStart), size = 96.dp)
                Row(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 8.dp, bottom = 40.dp)
                        .rotate(-6f)
                        .clip(RoundedCornerShape(999.dp))
                        .background(t.yellow)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(14.dp))
                    Text("+1 tile", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold), color = MaterialTheme.colorScheme.onSurface)
                }
            }
            Text("Every new street you walk lights a tile.", style = MaterialTheme.typography.bodySmall, color = t.ink2, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(20.dp))
        FeatureRow(Icons.Outlined.Groups, t.mintSoft, Color(0xFF0E8A5D), "Your circle", "A family, a partner, close friends. Each circle sees you while you belong to it.")
        Spacer(Modifier.height(14.dp))
        FeatureRow(Icons.Outlined.SportsEsports, t.yellowSoft, Color(0xFFA86F00), "The game", "New streets uncover map tiles. Public landmarks, quests and friendly territory rounds earn XP for you and your circle.")
        // The privacy promise gets its own panel: it is what people need to believe before granting location.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 20.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .border(1.5.dp, Color(0xFFD9CCFF), RoundedCornerShape(20.dp))
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("You stay in control", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold))
                Text(
                    "Hide from one person without leaving the circle, pause from the notification, or leave a circle at any time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = t.ink2,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        PrimaryButton("Create an account", onCreate, Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        SecondaryButton("I already have one", onSignIn, Modifier.fillMaxWidth())
    }
}

@Composable
private fun FeatureRow(icon: androidx.compose.ui.graphics.vector.ImageVector, tile: Color, tint: Color, title: String, body: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(tile), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge.copy(fontSize = 17.sp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = LocalTokens.current.ink2)
        }
    }
}
