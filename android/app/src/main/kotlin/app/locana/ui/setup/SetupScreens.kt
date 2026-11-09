package app.locana.ui.setup

import app.locana.ui.theme.cardBrush
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
import app.locana.ui.theme.LocalTokens
import app.locana.ui.components.Illustration
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Alignment
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
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

/** Six colour swatches in a row. The chosen one gets a dark ring; the others a white one, as in the mockup. */
@Composable
fun AvatarPicker(name: String, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        AvatarColors.keys.forEach { key ->
            val isSel = key == selected
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .selectable(selected = isSel, role = Role.RadioButton, onClick = { onSelect(key) })
                    .border(3.dp, if (isSel) MaterialTheme.colorScheme.onSurface else Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Avatar(name.ifBlank { "?" }, key, size = 40.dp)
            }
        }
    }
}

@Composable
fun ProfileSetupScreen(vm: SetupViewModel, onNext: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    AuthColumn {
        Spacer(Modifier.height(8.dp))
        Text("How should your circle see you?", style = MaterialTheme.typography.headlineLarge.copy(fontSize = 28.sp, lineHeight = 31.sp))
        Text(
            "Your name and a color. You can add a photo later in your profile.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
        )
        // Live preview of the pin your circle will see.
        Box(
            Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(28.dp)).background(cardBrush(Color(0xFFEDE4FF), Color(0xFFDCF0FF))),
        ) {
            Illustration("city", Modifier.align(Alignment.CenterEnd), size = 190.dp)
            Column(Modifier.align(Alignment.CenterStart).padding(start = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(92.dp).border(5.dp, Color.White, CircleShape).clip(CircleShape), contentAlignment = Alignment.Center) {
                    Avatar(s.displayName.ifBlank { "?" }, s.avatarKey, size = 92.dp)
                }
                Text(
                    (s.displayName.ifBlank { "You" }) + " · just now",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(Color.White).padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        Field(s.displayName, { v -> vm.edit { it.copy(displayName = v) } }, "Display name", capitalize = true)
        Text("Pin color", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 20.dp, bottom = 10.dp, start = 2.dp))
        AvatarPicker(s.displayName, s.avatarKey) { k -> vm.edit { it.copy(avatarKey = k) } }
        FormError(s.error)
        Spacer(Modifier.height(24.dp))
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
    val motion = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.tracking.refreshPermissions()
        step = 4
    }
    fun done() {
        if (finishOnboarding) vm.finish()
        onDone()
    }

    val tokens = LocalTokens.current
    AuthColumn {
        Spacer(Modifier.height(8.dp))
        when {
            step == 0 -> Explain(
                title = "Location for your circles",
                body = "Locana uses your location to show you on the map to the circles you belong to, to record your places and trips, and to count tiles and landmarks for the game. Android will ask next; \"While using the app\" is enough to start.",
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
                illustration = "compass",
                body = "Without this, your circle sees you only while Locana is open, and your last position is marked as old after 10 minutes. With it, a notification stays visible whenever your location is in use, and you can pause from that notification. Android may still delay updates to save battery.",
                note = if (Build.VERSION.SDK_INT >= 30) "Android opens settings for this. Choose \"Allow all the time\"." else null,
                primary = "Allow in background",
                onPrimary = { background.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) },
                onSkip = { step = 2 },
                skipLabel = "Only while the app is open",
            )
            step <= 2 && Build.VERSION.SDK_INT >= 33 && !perms.notifications -> Explain(
                title = "Notifications for what matters",
                illustration = "mailbox",
                body = "Arrivals at saved places, finished quests, and when sharing ends. You can mute each kind later in Privacy.",
                primary = "Allow notifications",
                onPrimary = { notifications.launch(Manifest.permission.POST_NOTIFICATIONS) },
                onSkip = { step = 3 },
            )
            step <= 3 && Build.VERSION.SDK_INT >= 29 && !perms.motion -> Explain(
                title = "Optional: tell driving from walking",
                illustration = "car",
                body = "Android can tell whether you are in a vehicle, on foot or standing still, using the phone's motion sensors. Locana uses that so your circle sees \"Driving\" in traffic and not \"Walking\", and so a stay at home is not mistaken for a trip. Nothing but that label is stored.",
                primary = "Allow motion detection",
                onPrimary = { motion.launch(Manifest.permission.ACTIVITY_RECOGNITION) },
                onSkip = { step = 4 },
            )
            else -> {
                ArtStage("mascot", Color(0xFFD6F5E7))
                Text("You're set!", style = MaterialTheme.typography.headlineLarge.copy(fontSize = 28.sp, lineHeight = 31.sp), modifier = Modifier.padding(top = 20.dp))
                Text(
                    "Sharing starts by itself for the circles you belong to; you can hide from a person on the map.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatusCard(
                        icon = if (perms.anyLocation) Icons.Outlined.LocationOn else Icons.Outlined.LocationOff,
                        tile = if (perms.anyLocation) tokens.mintSoft else tokens.coralSoft,
                        tint = if (perms.anyLocation) Color(0xFF0E8A5D) else tokens.coralDark,
                        title = "Location",
                        detail = if (!perms.anyLocation) "Not allowed, browse only" else if (perms.background) "Allowed, also in the background" else "Allowed while the app is open",
                    )
                    StatusCard(
                        icon = if (perms.notifications) Icons.Outlined.Notifications else Icons.Outlined.NotificationsOff,
                        tile = if (perms.notifications) tokens.mintSoft else tokens.yellowSoft,
                        tint = if (perms.notifications) Color(0xFF0E8A5D) else Color(0xFFA86F00),
                        title = "Notifications",
                        detail = if (perms.notifications) "On" else "Off",
                    )
                }
                if (!perms.anyLocation) {
                    Row(
                        Modifier.padding(top = 12.dp).clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                        }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Outlined.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Text("Open app settings", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
                FormError(s.error)
                Spacer(Modifier.height(24.dp))
                PrimaryButton(if (finishOnboarding) "Go to the map" else "Done", ::done, Modifier.fillMaxWidth(), loading = s.busy)
            }
        }
    }
}

@Composable
private fun StatusCard(icon: androidx.compose.ui.graphics.vector.ImageVector, tile: Color, tint: Color, title: String, detail: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(LocalTokens.current.card).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(tile), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
        }
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold))
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The big rounded stage with an illustration in it, on top of every permission step. */
@Composable
private fun ArtStage(illustration: String, end: Color) {
    Box(
        Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(32.dp)).background(cardBrush(Color.White, end, vertical = true)),
        contentAlignment = Alignment.Center,
    ) { Illustration(illustration, size = 190.dp) }
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
    illustration: String = "phone",
    stageEnd: Color = Color(0xFFE6DCFF),
) {
    ArtStage(illustration, stageEnd)
    Text(title, style = MaterialTheme.typography.headlineLarge.copy(fontSize = 28.sp, lineHeight = 31.sp), modifier = Modifier.padding(top = 20.dp))
    Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
    note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp)) }
    Spacer(Modifier.height(28.dp))
    PrimaryButton(primary, onPrimary, Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    SecondaryButton(skipLabel, onSkip, Modifier.fillMaxWidth())
}
