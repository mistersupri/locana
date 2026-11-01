package app.locana.ui.profile

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Switch
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
private fun NavItem(title: String, sub: String? = null, badge: Int = 0, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = sub?.let { { Text(it) } },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (badge > 0) Badge { Text("$badge") }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
        },
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    )
}

@Composable
private fun ToggleRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = sub?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        modifier = Modifier.clickable(role = Role.Switch) { onChange(!checked) },
    )
}

@Composable
fun ProfileScreen(nav: NavController, vm: ProfileViewModel = hiltViewModel()) {
    val me by vm.profile.collectAsStateWithLifecycle()
    val unread by vm.unread.collectAsStateWithLifecycle()
    val theme by vm.theme.collectAsStateWithLifecycle()
    var confirmOut by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(vertical = 16.dp)) {
        item {
            val m = me
            val hero = LocalHero.current
            if (m == null) LoadingState()
            else HeroPanel(Modifier.padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(m.profile.displayName, m.profile.avatarKey, size = 72.dp, framed = m.profile.avatarFrameKey != null)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.profile.displayName, style = MaterialTheme.typography.headlineSmall, color = hero.text)
                        Text("@${m.username}", style = MaterialTheme.typography.bodyMedium, color = hero.muted)
                        Text(
                            "Level ${m.progress.level} · ${m.progress.tilesDiscovered} tiles",
                            style = MaterialTheme.typography.labelLarge,
                            color = hero.reward,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                m.profile.bio?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = hero.text, modifier = Modifier.padding(top = 12.dp)) }
                HeroSecondaryButton("Edit profile", { nav.navigate(Routes.PROFILE_EDIT) }, Modifier.padding(top = 16.dp))
            }
            Spacer(Modifier.height(8.dp))
        }
        item {
            NavItem("Notifications", "Arrivals, quests and circle news", badge = unread) { nav.navigate(Routes.NOTIFICATIONS) }
            NavItem("Location sharing", "Who sees you, and until when") { nav.navigate(Routes.SHARING) }
            NavItem("Privacy", "History, exploration, rankings, alerts") { nav.navigate(Routes.PRIVACY) }
            NavItem("Account and security", "Password, devices, your data, delete account") { nav.navigate(Routes.ACCOUNT) }
            NavItem("Blocked people") { nav.navigate(Routes.BLOCKED) }
            NavItem("Permissions", "Location and notification access") { nav.navigate(Routes.PERMISSIONS) }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Appearance", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(ThemeMode.SYSTEM to "Match system", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark").forEach { (t, l) ->
                    FilterChip(selected = theme == t, onClick = { vm.setTheme(t) }, label = { Text(l) })
                }
            }
            TextButton(onClick = { confirmOut = true }, modifier = Modifier.padding(16.dp)) { Text("Sign out") }
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
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp)) {
            item {
                Field(name, { vm.name.value = it }, "Display name", capitalize = true)
                Field(username, { vm.username.value = it.lowercase() }, "Username")
                Field(bio, { vm.bio.value = it.take(160) }, "About you (optional)")
                SectionHeader("Avatar color")
                AvatarPicker(name, avatar) { vm.avatarKey.value = it }
                SectionHeader("Team circle")
                Text(
                    "Your validated landmark visits earn territory points for this circle, and it sets your circle quests. Leave it empty to play solo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = team == null, onClick = { vm.team.value = null }, label = { Text("None") })
                    circles?.value.orEmpty().forEach { c -> FilterChip(selected = team == c.id, onClick = { vm.team.value = c.id }, label = { Text(c.name) }) }
                }
                FormError(error)
                Spacer(Modifier.height(20.dp))
                PrimaryButton("Save", { vm.save { nav.popBackStack() } }, Modifier.fillMaxWidth(), loading = busy)
            }
        }
    }
}

@Composable
fun SharingScreen(nav: NavController, vm: SharingViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val circles by vm.circles.collectAsStateWithLifecycle()
    val perms by vm.tracking.permissions.collectAsStateWithLifecycle()
    val running by vm.tracking.running.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Picks up permissions granted or revoked in system settings while we were away.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.tracking.refreshPermissions() }
    BackScaffold("Location sharing", nav) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp)) {
            val st = s
            if (st == null) {
                item { if (error != null) ErrorState(error!!, { vm.refresh() }) else LoadingState() }
                return@LazyColumn
            }
            item {
                Surface(
                    color = if (st.collectLocation) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            when {
                                st.globallyPaused -> "Sharing is paused everywhere"
                                st.sessions.any { it.status == "ACTIVE" } -> st.sessions.count { it.status == "ACTIVE" }.let { n -> if (n == 1) "Sharing with 1 circle" else "Sharing with $n circles" }
                                else -> "You're not sharing with anyone"
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            if (running) "This phone is collecting location now. The notification stays visible while it does." else "This phone is not collecting location.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (pending > 0) Text("$pending position(s) waiting to upload.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
                ToggleRow("Pause all sharing", "Everyone sees you as hidden until you turn this off", st.globallyPaused) { vm.pauseAll(it) }
                if (st.sessions.isNotEmpty()) SecondaryButton("Stop sharing everywhere", { vm.stopAll() }, Modifier.fillMaxWidth())
                SectionHeader("By circle")
            }
            items(circles?.value.orEmpty(), key = { it.id }) { c ->
                val session = st.sessions.firstOrNull { it.circleId == c.id }
                ListItem(
                    headlineContent = { Text(c.name) },
                    supportingContent = {
                        Text(
                            when (session?.status) {
                                "ACTIVE" -> session.expiresAt?.let { "Sharing, ${endsIn(it)}" } ?: "Sharing until you stop"
                                "PAUSED" -> "Paused"
                                else -> "Not sharing"
                            },
                        )
                    },
                    trailingContent = {
                        androidx.compose.foundation.layout.Row {
                            when (session?.status) {
                                "ACTIVE" -> { TextButton(onClick = { vm.pause(session.id) }) { Text("Pause") }; TextButton(onClick = { vm.stop(session.id) }) { Text("Stop") } }
                                "PAUSED" -> { TextButton(onClick = { vm.resume(session.id) }) { Text("Resume") }; TextButton(onClick = { vm.stop(session.id) }) { Text("Stop") } }
                                else -> TextButton(enabled = perms.anyLocation, onClick = { vm.start(c.id, 60) }) { Text("Share 1 h") }
                            }
                        }
                    },
                )
                HorizontalDivider()
            }
            item {
                SectionHeader("This phone")
                StatusChip(if (perms.fine) "Precise location allowed" else if (perms.coarse) "Approximate location only" else "Location not allowed")
                if (!perms.background && perms.anyLocation && Build.VERSION.SDK_INT >= 29) {
                    Text(
                        "Background location is off, so your circles see you only while Locana is open. After 10 minutes closed, your last position shows as old.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    TextButton(onClick = { nav.navigate(Routes.PERMISSIONS) }) { Text("Review permissions") }
                }
                if (perms.background && !vm.ignoringBatteryOptimizations()) {
                    Text(
                        "Some phones (Xiaomi, Oppo, Vivo, Samsung and others) stop background apps to save battery. If your circle often sees you as old, allow Locana to run unrestricted in battery settings.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }) { Text("Open battery settings") }
                }
                if (!perms.anyLocation) {
                    TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))) }) { Text("Open app settings") }
                }
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
        LazyColumn(Modifier.padding(pad)) {
            item {
                message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp)) }
                SectionHeader("Location history", Modifier.padding(horizontal = 16.dp))
                ToggleRow("Keep my location history", "A private trail only you can see. Off by default.", p.historyEnabled) { vm.patch(PrivacyPatch(historyEnabled = it)) }
                if (p.historyEnabled) {
                    Text("Keep for ${p.historyRetentionDays} days", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp))
                    var days by remember(p.historyRetentionDays) { mutableStateOf(p.historyRetentionDays.toFloat()) }
                    Slider(
                        value = days,
                        onValueChange = { days = it },
                        onValueChangeFinished = { vm.patch(PrivacyPatch(historyRetentionDays = days.toInt())) },
                        valueRange = 1f..30f,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Delete my location history") }

                SectionHeader("Game", Modifier.padding(horizontal = 16.dp))
                ToggleRow("Exploration", "Lets the game use your location for tiles, landmarks and quests", p.explorationEnabled) { vm.patch(PrivacyPatch(explorationEnabled = it)) }
                ToggleRow("Show me on leaderboards", "When off, only you see your rank", p.showInLeaderboards) { vm.patch(PrivacyPatch(showInLeaderboards = it)) }
                ToggleRow("Share my exploration totals with circles", "Counts only, never where you went", p.shareExplorationWithCircles) { vm.patch(PrivacyPatch(shareExplorationWithCircles = it)) }
                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it.take(24) },
                    label = { Text("Leaderboard alias (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                TextButton(onClick = { vm.patch(PrivacyPatch(leaderboardAlias = alias.trim().ifEmpty { null })) }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Save alias") }

                SectionHeader("Notifications", Modifier.padding(horizontal = 16.dp))
                ToggleRow("Arrivals and departures", null, p.notifyArrivals) { vm.patch(PrivacyPatch(notifyArrivals = it)) }
                ToggleRow("Quests", null, p.notifyQuests) { vm.patch(PrivacyPatch(notifyQuests = it)) }
                ToggleRow("Levels and achievements", null, p.notifyAchievements) { vm.patch(PrivacyPatch(notifyAchievements = it)) }
                ToggleRow("Circle activity and invitations", null, p.notifyCircle) { vm.patch(PrivacyPatch(notifyCircle = it)) }
                Text(
                    "Security alerts and \"sharing ended\" notices always arrive: they are about your own account and location.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
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
    BackScaffold("Notifications", nav, actions = { TextButton(onClick = { vm.markAll() }) { Text("Mark all read") } }) { pad ->
        when (val s = state) {
            UiState.Loading -> LoadingState(Modifier.padding(pad))
            is UiState.Error -> ErrorState(s.message, { vm.load() }, Modifier.padding(pad))
            is UiState.Data -> if (s.value.isEmpty()) {
                EmptyState("You're all caught up", "Arrivals, quest rewards and circle news will show up here.", Modifier.padding(pad))
            } else {
                LazyColumn(Modifier.padding(pad)) {
                    items(s.value, key = { it.id }) { n ->
                        ListItem(
                            headlineContent = { Text(n.title, style = if (n.read) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleSmall) },
                            supportingContent = {
                                Text(listOf(n.body, Freshness.ageLabel(Instant.parse(n.createdAt).toEpochMilli(), System.currentTimeMillis())).filter { it.isNotBlank() }.joinToString(" · "))
                            },
                            trailingContent = { if (!n.read) Badge() },
                        )
                        HorizontalDivider()
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
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp)) {
            item {
                Text(me?.email.orEmpty(), style = MaterialTheme.typography.titleMedium)
                if (me?.googleLinked == true) Text("Signed in with Google too", style = MaterialTheme.typography.bodySmall)
                message?.let { Text(it, modifier = Modifier.padding(top = 8.dp)) }
                if (me?.hasPassword != false) SecondaryButton("Change password", { changing = true }, Modifier.padding(top = 12.dp))
                SectionHeader("Signed-in devices", actionLabel = "Sign out others", onAction = vm::signOutOthers)
            }
            when (val s = sessions) {
                UiState.Loading -> item { LoadingState() }
                is UiState.Error -> item { ErrorState(s.message, { vm.loadSessions() }) }
                is UiState.Data -> items(s.value, key = { it.id }) { d ->
                    ListItem(
                        headlineContent = { Text((d.deviceName ?: "Unknown device") + if (d.current) " (this phone)" else "") },
                        supportingContent = { Text("Signed in ${d.createdAt.take(10)}") },
                        trailingContent = { if (!d.current) TextButton(onClick = { vm.revoke(d.id) }) { Text("Sign out") } },
                    )
                }
            }
            item {
                SectionHeader("Your data")
                Text("Download a copy of everything Locana stores about you, including your own location history.", style = MaterialTheme.typography.bodySmall)
                SecondaryButton("Export my data", { vm.export { context.startActivity(it) } }, Modifier.padding(top = 8.dp))
                SectionHeader("Delete account")
                Text(
                    "Deletes your account, live location, history, places you created, game progress and devices right away. Circles you own pass to another member.",
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { deleting = true }) { Text("Delete my account", color = MaterialTheme.colorScheme.error) }
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
                EmptyState("Nobody blocked", "Blocking someone hides your locations from each other in every shared circle.", Modifier.padding(pad))
            } else {
                LazyColumn(Modifier.padding(pad)) {
                    items(s.value, key = { it.userId }) { b ->
                        ListItem(
                            headlineContent = { Text(b.displayName ?: b.username) },
                            supportingContent = { Text("@${b.username} · since ${b.since.take(10)}") },
                            trailingContent = { TextButton(onClick = { vm.unblock(b.userId) }) { Text("Unblock") } },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
