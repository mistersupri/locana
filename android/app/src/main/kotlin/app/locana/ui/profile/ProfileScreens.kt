package app.locana.ui.profile

import app.locana.ui.theme.cardBrush
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import app.locana.ui.components.LocanaChip
import app.locana.ui.components.LocanaCard
import app.locana.ui.components.IconTile
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.DeleteOutline
import app.locana.ui.theme.LocalTokens
import app.locana.ui.components.TagPill
import app.locana.ui.components.Segmented
import app.locana.ui.components.ListRowCard
import app.locana.ui.components.Illustration
import app.locana.ui.components.ChunkyStyle
import app.locana.ui.components.ChunkyButton
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import app.locana.ui.components.LocanaSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.locana.data.api.PrivacyPatch
import app.locana.data.prefs.ThemeMode
import app.locana.domain.Freshness
import app.locana.ui.auth.Field
import app.locana.ui.auth.FormError
import app.locana.ui.components.Avatar
import app.locana.ui.components.HeroPanel
import app.locana.ui.components.HeroSecondaryButton
import app.locana.ui.theme.LocalHero
import app.locana.ui.components.EmptyState
import app.locana.ui.components.ErrorState
import app.locana.ui.components.LoadingState
import app.locana.ui.components.PrimaryButton
import app.locana.ui.components.SecondaryButton
import app.locana.ui.components.SectionHeader
import app.locana.ui.components.StatusChip
import app.locana.ui.components.UiState
import app.locana.ui.explore.BackScaffold
import app.locana.ui.explore.endsIn
import app.locana.ui.nav.Routes
import app.locana.ui.setup.AvatarPicker
import java.time.Instant

@Composable
private fun NavItem(title: String, sub: String? = null, badge: Int = 0, icon: androidx.compose.ui.graphics.vector.ImageVector, tile: Color, tint: Color, onClick: () -> Unit) {
    ListRowCard(
        title = title,
        subtitle = sub,
        icon = icon,
        tileColor = tile,
        tileTint = tint,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp),
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (badge > 0) Badge { Text("$badge") }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = LocalTokens.current.ink3)
            }
        },
        onClick = onClick,
    )
}

/** One setting in a white card: title, a line of help, and the switch. */
@Composable
private fun ToggleRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Switch) { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold))
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        LocanaSwitch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun ProfileScreen(nav: NavController, vm: ProfileViewModel = hiltViewModel()) {
    val me by vm.profile.collectAsStateWithLifecycle()
    val unread by vm.unread.collectAsStateWithLifecycle()
    val theme by vm.theme.collectAsStateWithLifecycle()
    var confirmOut by remember { mutableStateOf(false) }
    val tk = LocalTokens.current
    val context = LocalContext.current
    val photoError by vm.photoError.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.setPhoto(context, uri) }
    BackScaffold("Profile", nav) { pad ->
    LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(vertical = 16.dp)) {
        item {
            val m = me
            val hero = LocalHero.current
            if (m == null) LoadingState()
            else Box(
                Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(Color(0xFF6B3BF5), Color(0xFF8D5BFF)))).padding(20.dp),
            ) {
                Illustration("mascot", Modifier.align(Alignment.TopEnd), size = 108.dp)
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(84.dp).border(4.dp, Color.White, CircleShape).clip(CircleShape), contentAlignment = Alignment.Center) {
                            Avatar(m.profile.displayName, m.profile.avatarKey, size = 84.dp, framed = m.profile.avatarFrameKey != null, userId = m.id, photoVersion = m.profile.avatarPhotoVersion)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f).padding(end = 70.dp)) {
                            Text(m.profile.displayName, style = MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("@${m.username}", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                        }
                    }
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TagPill("Level ${m.progress.level}", tk.yellow, Color(0xFF3A2800), icon = Icons.Outlined.StarOutline)
                        TagPill("${m.progress.tilesDiscovered} tiles", Color.White.copy(alpha = 0.22f), Color.White, icon = Icons.Outlined.GridView)
                    }
                    m.profile.bio?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Color.White, modifier = Modifier.padding(top = 12.dp)) }
                    Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ChunkyButton("Edit profile", { nav.navigate(Routes.PROFILE_EDIT) }, Modifier.weight(1f), style = ChunkyStyle.Secondary, compact = true)
                        ChunkyButton(if (m.profile.avatarPhotoVersion == null) "Add photo" else "Change photo", { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.weight(1f), style = ChunkyStyle.Secondary, compact = true)
                    }
                    if (m.profile.avatarPhotoVersion != null) Text("Remove photo", style = MaterialTheme.typography.labelLarge, color = Color.White, modifier = Modifier.clickable(role = Role.Button, onClick = vm::removePhoto).padding(vertical = 10.dp))
                    photoError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White) }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        item {
            NavItem("Notifications", "Arrivals, quests and circle news", badge = unread, icon = Icons.Outlined.Notifications, tile = tk.yellowSoft, tint = tk.yellowDark) { nav.navigate(Routes.NOTIFICATIONS) }
            NavItem("Privacy", "History, exploration, rankings, alerts", icon = Icons.Outlined.VisibilityOff, tile = tk.mintSoft, tint = app.locana.ui.theme.LocalTokens.current.onMint) { nav.navigate(Routes.PRIVACY) }
            NavItem("Account and security", "Password, devices, your data, delete account", icon = Icons.Outlined.Shield, tile = MaterialTheme.colorScheme.primaryContainer, tint = MaterialTheme.colorScheme.primary) { nav.navigate(Routes.ACCOUNT) }
            NavItem("Blocked people", "Hidden from each other", icon = Icons.Outlined.PersonOff, tile = tk.coralSoft, tint = tk.coralDark) { nav.navigate(Routes.BLOCKED) }
            NavItem("Permissions", "Location and notification access", icon = Icons.Outlined.LocationOn, tile = tk.skySoft, tint = app.locana.ui.theme.LocalTokens.current.onSky) { nav.navigate(Routes.PERMISSIONS) }
            Text("Appearance", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 22.dp, top = 18.dp, bottom = 8.dp))
            Segmented(
                listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark"),
                theme, { vm.setTheme(it) }, Modifier.padding(horizontal = 20.dp),
            )
            Text(
                "Sign out",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = tk.coralDark,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp).clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { confirmOut = true }.padding(horizontal = 8.dp, vertical = 8.dp),
            )
        }
    }
    }
    if (confirmOut) {
        AlertDialog(
            onDismissRequest = { confirmOut = false },
            title = { Text("Sign out?") },
            text = { Text("Location sharing from this phone stops. Your circles will see your last position as not live.") },
            confirmButton = { TextButton(onClick = { confirmOut = false; vm.signOut() }) { Text("Sign out") } },
            dismissButton = { TextButton(onClick = { confirmOut = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun ProfileEditScreen(nav: NavController, vm: ProfileEditViewModel = hiltViewModel()) {
    val name by vm.name.collectAsStateWithLifecycle()
    val username by vm.username.collectAsStateWithLifecycle()
    val bio by vm.bio.collectAsStateWithLifecycle()
    val avatar by vm.avatarKey.collectAsStateWithLifecycle()
    val team by vm.team.collectAsStateWithLifecycle()
    val circles by vm.circles.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    BackScaffold("Edit profile", nav) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(20.dp)) {
            item {
                Box(
                    Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(28.dp)).background(cardBrush(Color(0xFFEDE4FF), Color(0xFFFBF6EE), vertical = true)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(96.dp).border(5.dp, Color.White, CircleShape).clip(CircleShape)) {
                        Avatar(name.ifBlank { "?" }, avatar, size = 96.dp)
                    }
                }
                Field(name, { vm.name.value = it }, "Display name", capitalize = true)
                Field(username, { vm.username.value = it.lowercase() }, "Username")
                Field(bio, { vm.bio.value = it.take(160) }, "About you (optional)")
                Text("Avatar color", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 20.dp, bottom = 10.dp, start = 2.dp))
                AvatarPicker(name, avatar) { vm.avatarKey.value = it }
                Text("Team circle", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp, start = 2.dp))
                Text(
                    "Your validated landmark visits earn territory points for this circle, and it sets your circle quests. Leave it empty to play solo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LocanaChip("None", team == null, { vm.team.value = null })
                    circles?.value.orEmpty().forEach { c -> LocanaChip(c.name, team == c.id, { vm.team.value = c.id }) }
                }
                FormError(error)
                Spacer(Modifier.height(20.dp))
                PrimaryButton("Save", { vm.save { nav.popBackStack() } }, Modifier.fillMaxWidth(), loading = busy)
            }
        }
    }
}

@Composable
fun PrivacyScreen(nav: NavController, vm: PrivacyViewModel = hiltViewModel()) {
    val me by vm.profile.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var alias by remember(me?.privacy?.leaderboardAlias) { mutableStateOf(me?.privacy?.leaderboardAlias.orEmpty()) }
    BackScaffold("Privacy", nav) { pad ->
        val p = me?.privacy
        if (p == null) {
            LoadingState(Modifier.padding(pad))
            return@BackScaffold
        }
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
            item {
                message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 12.dp)) }
                SectionHeader("Location history")
                LocanaCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), shadow = 3.dp) {
                    Column {
                        ToggleRow("Keep my location history", "Your places and trips, shown to you and to the people in your circles who can see you. On by default.", p.historyEnabled) { vm.patch(PrivacyPatch(historyEnabled = it)) }
                        if (p.historyEnabled) {
                            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Keep for", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                Text("${p.historyRetentionDays} days", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            }
                            var days by remember(p.historyRetentionDays) { mutableStateOf(p.historyRetentionDays.toFloat()) }
                            Slider(
                                value = days,
                                onValueChange = { days = it },
                                onValueChangeFinished = { vm.patch(PrivacyPatch(historyRetentionDays = days.toInt())) },
                                valueRange = 1f..30f,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                        Text(
                            "Delete my location history",
                            style = MaterialTheme.typography.labelLarge,
                            color = LocalTokens.current.coralDark,
                            modifier = Modifier.clickable(role = Role.Button) { confirmDelete = true }.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }

                SectionHeader("Game")
                LocanaCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), shadow = 3.dp) {
                    Column {
                        ToggleRow("Exploration", "Lets the game use your location for tiles, landmarks and quests", p.explorationEnabled) { vm.patch(PrivacyPatch(explorationEnabled = it)) }
                        ToggleRow("Show me on leaderboards", "When off, only you see your rank", p.showInLeaderboards) { vm.patch(PrivacyPatch(showInLeaderboards = it)) }
                        ToggleRow("Share my exploration totals with circles", "Counts only, never where you went", p.shareExplorationWithCircles) { vm.patch(PrivacyPatch(shareExplorationWithCircles = it)) }
                    }
                }
                Field(alias, { alias = it.take(24) }, "Leaderboard alias (optional)")
                Text(
                    "Save alias",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp).clickable(role = Role.Button) { vm.patch(PrivacyPatch(leaderboardAlias = alias.trim().ifEmpty { null })) }.padding(8.dp),
                )

                SectionHeader("Notifications")
                LocanaCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), shadow = 3.dp) {
                    Column {
                        ToggleRow("Arrivals and departures", null, p.notifyArrivals) { vm.patch(PrivacyPatch(notifyArrivals = it)) }
                        ToggleRow("Quests", null, p.notifyQuests) { vm.patch(PrivacyPatch(notifyQuests = it)) }
                        ToggleRow("Levels and achievements", null, p.notifyAchievements) { vm.patch(PrivacyPatch(notifyAchievements = it)) }
                        ToggleRow("Circle activity and invitations", null, p.notifyCircle) { vm.patch(PrivacyPatch(notifyCircle = it)) }
                    }
                }
                Text(
                    "Security alerts and \"sharing ended\" notices always arrive: they are about your own account and location.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete location history?") },
            text = { Text("All saved past positions are removed from our servers. Your current live sharing is not affected.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteHistory() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun NotificationsScreen(nav: NavController, vm: NotificationsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    BackScaffold("Notifications", nav, actions = { Text("Mark all read", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable(role = Role.Button) { vm.markAll() }.padding(8.dp)) }) { pad ->
        when (val s = state) {
            UiState.Loading -> LoadingState(Modifier.padding(pad))
            is UiState.Error -> ErrorState(s.message, { vm.load() }, Modifier.padding(pad))
            is UiState.Data -> if (s.value.isEmpty()) {
                Column(Modifier.padding(pad).padding(horizontal = 20.dp)) {
                    EmptyState("You're all caught up", "Arrivals, quest rewards and circle news will show up here.", illustration = "mailbox")
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(LocalTokens.current.yellowSoft).padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconTile(Icons.Outlined.Lightbulb, Color(0xFFFFC93C), Color(0xFF3A2800), size = 36.dp)
                        Text("Tip: save a place to get arrival alerts from your circle.", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 13.sp), color = LocalTokens.current.onYellowSoft)
                    }
                }
            } else {
                LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(s.value, key = { it.id }) { n ->
                        ListRowCard(
                            title = n.title,
                            subtitle = listOf(n.body, Freshness.ageLabel(Instant.parse(n.createdAt).toEpochMilli(), System.currentTimeMillis())).filter { it.isNotBlank() }.joinToString(" · "),
                            icon = Icons.Outlined.Notifications,
                            tileColor = LocalTokens.current.yellowSoft,
                            tileTint = LocalTokens.current.yellowDark,
                            trailing = { if (!n.read) Badge() },
                        )
                    }
                    if (vm.hasMore) item { LaunchedEffect(s.value.size) { vm.more() } }
                }
            }
        }
    }
}

@Composable
fun AccountScreen(nav: NavController, vm: AccountViewModel = hiltViewModel()) {
    val me by vm.profile.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var changing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    BackScaffold("Account and security", nav) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
            item {
                val tk = LocalTokens.current
                ListRowCard(me?.email.orEmpty(), subtitle = if (me?.googleLinked == true) "Signed in with Google too" else null, icon = Icons.Outlined.MailOutline, trailing = null)
                message?.let { Text(it, modifier = Modifier.padding(top = 8.dp)) }
                if (me?.hasPassword != false) SecondaryButton("Change password", { changing = true }, Modifier.fillMaxWidth().padding(top = 12.dp))
                SectionHeader("Signed-in devices", actionLabel = "Sign out others", onAction = vm::signOutOthers)
            }
            when (val s = sessions) {
                UiState.Loading -> item { LoadingState() }
                is UiState.Error -> item { ErrorState(s.message, { vm.loadSessions() }) }
                is UiState.Data -> items(s.value, key = { it.id }) { d ->
                    val tk = LocalTokens.current
                    ListRowCard(
                        title = (d.deviceName ?: "Unknown device") + if (d.current) " · this phone" else "",
                        subtitle = "Signed in ${d.createdAt.take(10)}",
                        icon = Icons.Outlined.Smartphone,
                        tileColor = tk.mintSoft,
                        tileTint = app.locana.ui.theme.LocalTokens.current.onMint,
                        modifier = Modifier.padding(bottom = 8.dp),
                        trailing = {
                            if (d.current) TagPill("Active", tk.mintSoft, app.locana.ui.theme.LocalTokens.current.onMint)
                            else Text("Sign out", style = MaterialTheme.typography.labelLarge, color = tk.coralDark, modifier = Modifier.clickable(role = Role.Button) { vm.revoke(d.id) }.padding(8.dp))
                        },
                    )
                }
            }
            item {
                val tk = LocalTokens.current
                SectionHeader("Your data")
                LocanaCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), shadow = 3.dp) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Download a copy of everything Locana stores about you, including your own location history.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SecondaryButton("Export my data", { vm.export { context.startActivity(it) } }, Modifier.fillMaxWidth())
                    }
                }
                SectionHeader("Delete account")
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(tk.coralSoft).border(1.5.dp, tk.coral.copy(alpha = 0.3f), RoundedCornerShape(22.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Deletes your account, live location, history, places you created, game progress and devices right away. Circles you own pass to another member.",
                        style = MaterialTheme.typography.bodySmall,
                        color = tk.ink2,
                    )
                    ChunkyButton("Delete my account", { deleting = true }, Modifier.fillMaxWidth(), style = ChunkyStyle.Coral, icon = Icons.Outlined.DeleteOutline)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    if (changing) {
        var current by remember { mutableStateOf("") }
        var next by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { changing = false },
            title = { Text("Change password") },
            text = {
                Column {
                    OutlinedTextField(current, { current = it }, label = { Text("Current password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                    OutlinedTextField(next, { next = it }, label = { Text("New password (10+ characters)") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = { vm.changePassword(current, next) { changing = false } }) { Text("Change") } },
            dismissButton = { TextButton(onClick = { changing = false }) { Text("Cancel") } },
        )
    }
    if (deleting) {
        var pw by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Delete your account?") },
            text = {
                Column {
                    Text("This can't be undone.")
                    if (me?.hasPassword != false) OutlinedTextField(pw, { pw = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                }
            },
            confirmButton = { TextButton(enabled = !busy, onClick = { vm.deleteAccount(pw.ifEmpty { null }) }) { Text("Delete forever", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Keep my account") } },
        )
    }
}

@Composable
fun BlockedScreen(nav: NavController, vm: BlockedViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    BackScaffold("Blocked people", nav) { pad ->
        when (val s = state) {
            UiState.Loading -> LoadingState(Modifier.padding(pad))
            is UiState.Error -> ErrorState(s.message, { vm.load() }, Modifier.padding(pad))
            is UiState.Data -> if (s.value.isEmpty()) {
                EmptyState("Nobody blocked", "Blocking someone hides your locations from each other in every shared circle.", Modifier.padding(pad), illustration = "shield")
            } else {
                LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(s.value, key = { it.userId }) { b ->
                        ListRowCard(
                            title = b.displayName ?: b.username,
                            subtitle = "@${b.username} · since ${b.since.take(10)}",
                            icon = Icons.Outlined.PersonOff,
                            tileColor = LocalTokens.current.coralSoft,
                            tileTint = LocalTokens.current.coralDark,
                            trailing = { Text("Unblock", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable(role = Role.Button) { vm.unblock(b.userId) }.padding(8.dp)) },
                        )
                    }
                }
            }
        }
    }
}
