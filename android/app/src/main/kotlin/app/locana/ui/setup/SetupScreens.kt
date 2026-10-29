package app.locana.ui.setup

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.locana.ui.auth.AuthColumn
import app.locana.ui.auth.Field
import app.locana.ui.auth.FormError
import app.locana.ui.components.Avatar
import app.locana.ui.components.PrimaryButton
import app.locana.ui.components.SecondaryButton
import app.locana.ui.theme.AvatarColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AvatarPicker(name: String, selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AvatarColors.keys.forEach { key ->
            val isSel = key == selected
            // The selection ring sits outside the avatar with a gap, in the text color, so it stays
            // visible on every avatar color (a teal ring on the teal avatar was invisible).
            Box(
                Modifier
                    .clip(CircleShape)
                    .selectable(selected = isSel, role = Role.RadioButton, onClick = { onSelect(key) })
                    .border(3.dp, if (isSel) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
                    .padding(5.dp),
            ) {
                Avatar(name.ifBlank { "?" }, key, size = 52.dp)
            }
        }
    }
}

@Composable
fun ProfileSetupScreen(vm: SetupViewModel, onNext: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    AuthColumn {
        Spacer(Modifier.height(24.dp))
        Text("How should your circle see you?", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Your name and a color. Locana shows initials instead of photos.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
        )
        Field(s.displayName, { v -> vm.edit { it.copy(displayName = v) } }, "Display name", capitalize = true)
        Spacer(Modifier.height(20.dp))
        AvatarPicker(s.displayName, s.avatarKey) { k -> vm.edit { it.copy(avatarKey = k) } }
        FormError(s.error)
        Spacer(Modifier.height(28.dp))
        PrimaryButton("Continue", { vm.saveProfile(onNext) }, Modifier.fillMaxWidth(), loading = s.busy)
    }
}

/**
 * Staged permission education. Each permission is explained before Android asks, every step
 * can be skipped, and the app works (map, circles, game screens) with all of them declined.
 * Background location is offered only after foreground location was granted.
 */
@Composable
fun PermissionsScreen(vm: SetupViewModel, onDone: () -> Unit, finishOnboarding: Boolean = true) {
    val perms by vm.tracking.permissions.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    val s by vm.state.collectAsStateWithLifecycle()

    val foreground = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.tracking.refreshPermissions()
        step = 1
    }
    val background = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.tracking.refreshPermissions()
        vm.backgroundExplained()
        step = 2
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.tracking.refreshPermissions()
        step = 3
    }
    fun done() {
        if (finishOnboarding) vm.finish()
        onDone()
    }

    AuthColumn {
        Spacer(Modifier.height(24.dp))
        when {
            step == 0 -> Explain(
                title = "Location, only when you share",
                body = "Locana uses your location to show you on the map to the circles you choose, and to count tiles and landmarks if you turn on exploration. Android will ask next; \"While using the app\" is enough to start.",
                note = if (perms.anyLocation) "Already allowed." else null,
                primary = if (perms.anyLocation) "Continue" else "Allow location",
                onPrimary = {
                    if (perms.anyLocation) step = 1
                    else foreground.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                },
                onSkip = { step = 2 },
            )
            step == 1 && perms.anyLocation && !perms.background && Build.VERSION.SDK_INT >= 29 -> Explain(
                title = "Optional: keep sharing with the app closed",
                body = "Without this, your circle sees you only while Locana is open, and your last position is marked as old after 10 minutes. With it, a notification stays visible whenever your location is in use, and you can pause from that notification. Android may still delay updates to save battery.",
                note = if (Build.VERSION.SDK_INT >= 30) "Android opens settings for this. Choose \"Allow all the time\"." else null,
                primary = "Allow in background",
                onPrimary = { background.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) },
                onSkip = { step = 2 },
                skipLabel = "Only while the app is open",
            )
            step <= 2 && Build.VERSION.SDK_INT >= 33 && !perms.notifications -> Explain(
                title = "Notifications for what matters",
                body = "Arrivals at saved places, finished quests, and when sharing ends. You can mute each kind later in Privacy.",
                primary = "Allow notifications",
                onPrimary = { notifications.launch(Manifest.permission.POST_NOTIFICATIONS) },
                onSkip = { step = 3 },
            )
            else -> {
                Text("You're set", style = MaterialTheme.typography.headlineMedium)
                Text(
                    statusLine(perms.anyLocation, perms.background, perms.notifications),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (!perms.anyLocation) {
                    TextButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                    }) { Text("Open app settings") }
                }
                FormError(s.error)
                Spacer(Modifier.height(24.dp))
                PrimaryButton(if (finishOnboarding) "Go to the map" else "Done", ::done, Modifier.fillMaxWidth(), loading = s.busy)
            }
        }
    }
}

private fun statusLine(location: Boolean, background: Boolean, notifications: Boolean) = buildString {
    append(if (location) "Location: allowed" else "Location: not allowed. You can browse, but sharing and exploration stay off.")
    if (location) append(if (background) ", including in the background." else ", while the app is open.")
    append(if (notifications) " Notifications: on." else " Notifications: off.")
    append(" Sharing itself is still off; start it from the Map when you want.")
}

@Composable
private fun Explain(
    title: String,
    body: String,
    primary: String,
    onPrimary: () -> Unit,
    onSkip: () -> Unit,
    note: String? = null,
    skipLabel: String = "Not now",
) {
    Text(title, style = MaterialTheme.typography.headlineMedium)
    Text(body, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp))
    note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp)) }
    Spacer(Modifier.height(28.dp))
    PrimaryButton(primary, onPrimary, Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    SecondaryButton(skipLabel, onSkip, Modifier.fillMaxWidth())
}
