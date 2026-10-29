package app.locana.ui.explore

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.locana.BuildConfig
import app.locana.R
import app.locana.data.api.QuestDto
import app.locana.domain.Formatting
import app.locana.domain.TileBounds
import app.locana.ui.components.CachedNotice
import app.locana.ui.components.EmptyState
import app.locana.ui.components.ErrorState
import app.locana.ui.components.LoadingState
import app.locana.ui.components.PrimaryButton
import app.locana.ui.components.QuestProgressTrack
import app.locana.ui.components.SectionHeader
import app.locana.ui.components.StatusChip
import app.locana.ui.components.UiState
import app.locana.ui.components.XpChip
import app.locana.ui.nav.Routes
import app.locana.ui.theme.LocalDarkTheme
import app.locana.ui.theme.LocalReward
import app.locana.ui.theme.NumberStyle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.rememberCameraPositionState
import java.time.Duration
import java.time.Instant

/** "ends in 5 h" style label for quest deadlines. */
fun endsIn(iso: String): String {
    val d = Duration.between(Instant.now(), Instant.parse(iso))
    return when {
        d.isNegative -> "ended"
        d.toHours() >= 48 -> "${d.toDays()} days left"
        d.toHours() >= 1 -> "${d.toHours()} h left"
        else -> "${d.toMinutes().coerceAtLeast(1)} min left"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackScaffold(title: String, nav: NavController, actions: @Composable () -> Unit = {}, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = { actions() },
            )
        },
        content = content,
    )
}

@Composable
fun ExploreScreen(nav: NavController, vm: ExploreViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val exploring by vm.explorationEnabled.collectAsStateWithLifecycle()
    val toggleError by vm.toggleError.collectAsStateWithLifecycle()
    val perms by vm.tracking.permissions.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.tracking.refreshPermissions() }

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        item {
            Text("Explore", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(12.dp))
            ExplorationToggle(
                enabled = exploring,
                hasPermission = perms.anyLocation,
                error = toggleError,
                onToggle = { if (perms.anyLocation) vm.setExploration(it) else nav.navigate(Routes.PERMISSIONS) },
            )
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { nav.navigate(Routes.EXPLORATION_MAP) }) { Text("My explored map") }
                TextButton(onClick = { nav.navigate(Routes.LANDMARKS) }) { Text("Find landmarks") }
                TextButton(onClick = { nav.navigate(Routes.QUEST_HISTORY) }) { Text("History") }
            }
        }
        when (val s = state) {
            UiState.Loading -> item { LoadingState(label = "Loading quests") }
            is UiState.Error -> item { ErrorState(s.message, vm::load) }
            is UiState.Data -> {
                s.cachedAtMs?.let { at -> item { CachedNotice(at, { vm.load() }, Modifier.padding(top = 8.dp)) } }
                s.value.events.filter { it.live }.forEach { e ->
                    item(key = "ev" + e.id) {
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                            Column(Modifier.padding(16.dp)) {
                                Text(e.name, style = MaterialTheme.typography.titleMedium)
                                Text(e.description, style = MaterialTheme.typography.bodyMedium)
                                Text(endsIn(e.endsAt), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
                val groups = s.value.quests.groupBy { it.type }
                if (s.value.quests.isEmpty()) {
                    item {
                        EmptyState(
                            "No quests right now",
                            "New daily quests appear at midnight. Circle quests need a team circle, which you can pick in Profile.",
                        )
                    }
                }
                listOf("SPECIAL" to "Event", "DAILY" to "Today", "WEEKLY" to "This week").forEach { (type, title) ->
                    val qs = groups[type].orEmpty()
                    if (qs.isNotEmpty()) {
                        item(key = "h$type") { SectionHeader(title) }
                        items(qs, key = { it.id }) { q -> QuestCard(q) { nav.navigate(Routes.quest(q.id)) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExplorationToggle(enabled: Boolean, hasPermission: Boolean, error: String?, onToggle: (Boolean) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.clickable(role = Role.Switch) { onToggle(!enabled) }.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Exploration", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (enabled) "On. Your walks reveal tiles and count for quests. Your circles do not see where you went."
                        else "Off. Turn on to let the game use your location for tiles, landmarks and quests. Separate from sharing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
            if (!hasPermission) Text("Needs location permission first.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
fun QuestCard(q: QuestDto, onClick: () -> Unit) {
    val completed = q.status == "COMPLETED"
    val claimed = q.status == "CLAIMED"
    Surface(
        color = if (completed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (completed) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable(role = Role.Button, onClick = onClick),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(q.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (claimed) StatusChip("Claimed") else XpChip(q.rewardXp)
            }
            Text(q.description, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
            QuestProgressTrack(q.progress, q.target, q.unit)
            Row(Modifier.padding(top = 6.dp)) {
                Text(Formatting.progress(q.progress, q.target, q.unit), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                Text(if (completed) "Ready to claim" else endsIn(q.expiresAt), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun QuestScreen(nav: NavController, vm: QuestViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val claim by vm.claim.collectAsStateWithLifecycle()
    val claiming by vm.claiming.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    BackScaffold("Quest", nav) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (val s = state) {
                UiState.Loading -> LoadingState()
                is UiState.Error -> ErrorState(s.message, { vm.load() })
                is UiState.Data -> {
                    val q = s.value
                    LazyColumn(contentPadding = PaddingValues(16.dp)) {
                        item {
                            Text(q.name, style = MaterialTheme.typography.headlineMedium)
                            Text(q.description, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp))
                            QuestProgressTrack(q.progress, q.target, q.unit, Modifier.height(14.dp))
                            Text(Formatting.progress(q.progress, q.target, q.unit), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                XpChip(q.rewardXp)
                                StatusChip(q.type.lowercase().replaceFirstChar { it.uppercase() })
                                StatusChip(if (q.status == "ACTIVE") endsIn(q.expiresAt) else q.status.lowercase().replaceFirstChar { it.uppercase() })
                            }
                            Text(
                                "Progress counts only from precise location fixes taken while exploration is on. Repeat visits to the same tile do not count twice.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 16.dp),
                            )
                            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
                            if (q.status == "COMPLETED") {
                                Spacer(Modifier.height(20.dp))
                                PrimaryButton("Claim ${q.rewardXp} XP", vm::claim, Modifier.fillMaxWidth(), loading = claiming)
                            }
                            if (q.recentProgress.isNotEmpty()) SectionHeader("Recent progress")
                        }
                        items(q.recentProgress) { p ->
                            ListItem(
                                headlineContent = { Text("+${p.delta} ${q.unit}") },
                                supportingContent = { Text(Instant.parse(p.at).toString().take(16).replace('T', ' ')) },
                            )
                        }
                    }
                }
            }
        }
    }
    claim?.let { r -> QuestCompleteDialog(r.quest.name, r.xpAwarded, r.alreadyClaimed, r.achievements.map { it.name }, r.collectibles.map { it.name }, vm::dismissClaim) }
}

/** Rare, earned moment: allowed a little more presence than everyday UI (see Motion.kt). */
@Composable
fun QuestCompleteDialog(name: String, xp: Int, already: Boolean, achievements: List<String>, collectibles: List<String>, onDismiss: () -> Unit) {
    val reward = LocalReward.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Nice") } },
        title = { Text(if (already) "Already claimed" else "Quest complete") },
        text = {
            Column {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text("+$xp XP", style = NumberStyle, color = reward.reward, modifier = Modifier.padding(vertical = 8.dp))
                achievements.forEach { Text("Achievement: $it", style = MaterialTheme.typography.bodyMedium) }
                collectibles.forEach { Text("Collected: $it", style = MaterialTheme.typography.bodyMedium) }
                if (already) Text("This reward was added to your XP earlier.", style = MaterialTheme.typography.bodySmall)
            }
        },
    )
}

@Composable
fun QuestHistoryScreen(nav: NavController, vm: QuestHistoryViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    BackScaffold("Quest history", nav) { pad ->
        when (val s = state) {
            UiState.Loading -> LoadingState(Modifier.padding(pad))
            is UiState.Error -> ErrorState(s.message, { vm.more() }, Modifier.padding(pad))
            is UiState.Data -> if (s.value.isEmpty()) {
                EmptyState("Nothing here yet", "Claimed and expired quests show up here.", Modifier.padding(pad))
            } else {
                LazyColumn(contentPadding = pad) {
                    items(s.value, key = { it.id }) { q ->
                        ListItem(
                            headlineContent = { Text(q.name) },
                            supportingContent = { Text(q.startedAt.take(10) + " · " + Formatting.progress(q.progress, q.target, q.unit)) },
                            trailingContent = { if (q.status == "CLAIMED") XpChip(q.rewardXp) else StatusChip("Expired") },
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
fun LandmarksScreen(nav: NavController, vm: LandmarksViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val q by vm.query.collectAsStateWithLifecycle()
    val located by vm.located.collectAsStateWithLifecycle()
    BackScaffold("Landmarks", nav) { pad ->
        Column(Modifier.padding(pad)) {
            OutlinedTextField(
                value = q,
                onValueChange = vm::onQuery,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                label = { Text("Search public places") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
            if (!located) {
                Text(
                    "We don't know where you are right now, so distances are hidden.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            when (val s = state) {
                UiState.Loading -> LoadingState()
                is UiState.Error -> ErrorState(s.message, { vm.search() })
                is UiState.Data -> if (s.value.isEmpty()) {
                    EmptyState("No landmarks found", if (q.isBlank()) "No public landmarks are mapped near you yet." else "Try another name.")
                } else {
                    LazyColumn {
                        items(s.value, key = { it.id }) { l ->
                            ListItem(
                                headlineContent = { Text(l.name) },
                                supportingContent = {
                                    val parts = listOfNotNull(
                                        l.category.replaceFirstChar { it.uppercase() },
                                        "${Formatting.distance(l.distanceM)} away".takeIf { located },
                                        l.description,
                                    )
                                    Text(parts.joinToString(" · "), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                },
                                trailingContent = { if (l.visited) StatusChip("Visited") else XpChip(l.rewardXp) },
                                modifier = l.territoryId?.let { id -> Modifier.clickable(role = Role.Button) { nav.navigate(Routes.territory(id)) } } ?: Modifier,
                            )
                            HorizontalDivider()
                        }
                        item {
                            Text(
                                "Only public places are listed. Respect opening hours and local rules; never enter restricted areas for the game.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TerritoryScreen(nav: NavController, vm: TerritoryViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val team by vm.teamCircleId.collectAsStateWithLifecycle()
    BackScaffold("Territory", nav) { pad ->
        when (val s = state) {
            UiState.Loading -> LoadingState(Modifier.padding(pad))
            is UiState.Error -> ErrorState(s.message, { vm.load() }, Modifier.padding(pad))
            is UiState.Data -> {
                val t = s.value
                LazyColumn(contentPadding = PaddingValues(16.dp), modifier = Modifier.padding(pad)) {
                    item {
                        Text(t.name, style = MaterialTheme.typography.headlineMedium)
                        Text(t.landmark.category.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                            Column(Modifier.padding(16.dp)) {
                                Text(if (t.ownerCircleName != null) "Held by ${t.ownerCircleName}" else "Unclaimed", style = MaterialTheme.typography.titleLarge)
                                t.expiresAt?.let { Text("Hold ends: ${endsIn(it)}", style = MaterialTheme.typography.bodyMedium) }
                                Text(
                                    "Each member's validated visit earns their team circle 1 point per day. ${t.rules.claimMinScore} points claims an unclaimed spot; beat the holder's score this week to take it. Holds fade after ${t.rules.holdDays} days without visits.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                        if (team == null) {
                            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Pick a team circle in Profile so your visits count here.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    TextButton(onClick = { nav.navigate(Routes.PROFILE_EDIT) }) { Text("Pick") }
                                }
                            }
                        }
                        SectionHeader("This week (${t.periodKey})")
                        if (t.leaderboard.isEmpty()) Text("No points yet this week.", style = MaterialTheme.typography.bodyMedium)
                    }
                    items(t.leaderboard) { r ->
                        ListItem(
                            leadingContent = { Text("#${r.rank}", style = MaterialTheme.typography.titleMedium) },
                            headlineContent = { Text(r.circleName + if (r.isMine) " (yours)" else "") },
                            trailingContent = { Text("${r.score} pts", style = MaterialTheme.typography.titleSmall) },
                        )
                    }
                    if (t.history.isNotEmpty()) item { SectionHeader("History") }
                    items(t.history) { h ->
                        ListItem(
                            headlineContent = { Text(if (h.type == "CLAIM") "${h.circleName} claimed it" else "${h.circleName ?: "The holder"}'s hold faded") },
                            supportingContent = { Text(h.day) },
                        )
                    }
                    item {
                        Text(
                            "Territories are a game around public places. They do not mean anyone owns the real place.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExplorationMapScreen(nav: NavController, vm: ExplorationMapViewModel = hiltViewModel()) {
    val tiles by vm.tiles.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val tooLarge by vm.tooLarge.collectAsStateWithLifecycle()
    val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(LatLng(-6.2, 106.82), 14f) }
    val context = LocalContext.current
    val dark = LocalDarkTheme.current
    val primary = MaterialTheme.colorScheme.primary
    LaunchedEffect(camera.isMoving) {
        if (camera.isMoving) return@LaunchedEffect
        val b = camera.projection?.visibleRegion?.latLngBounds ?: return@LaunchedEffect
        vm.onViewport(TileBounds(b.northeast.latitude, b.southwest.latitude, b.northeast.longitude, b.southwest.longitude))
    }
    LaunchedEffect(tiles.isNotEmpty()) {
        // Jump to the most recent discovery the first time tiles arrive.
        tiles.maxByOrNull { it.discoveredAt }?.let { camera.move(CameraUpdateFactory.newLatLng(LatLng((it.north + it.south) / 2, (it.east + it.west) / 2))) }
    }
    BackScaffold("My explored map", nav) { pad ->
        Column(Modifier.padding(pad)) {
            stats?.let { s ->
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Stat("${s.tilesDiscovered}", "tiles")
                    Stat("${s.landmarksVisited}", "landmarks")
                    Stat(Formatting.distance(s.totalDistanceM), "walked")
                    Stat("${s.currentStreak}", "day streak")
                }
            }
            if (tooLarge) Text("Zoom in to see your tiles.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
            if (BuildConfig.HAS_MAPS_KEY) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = camera,
                    properties = MapProperties(mapStyleOptions = MapStyleOptions.loadRawResourceStyle(context, if (dark) R.raw.map_style_dark else R.raw.map_style_light)),
                    uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false),
                ) {
                    tiles.forEach { t ->
                        Polygon(
                            points = listOf(LatLng(t.north, t.west), LatLng(t.north, t.east), LatLng(t.south, t.east), LatLng(t.south, t.west)),
                            fillColor = primary.copy(alpha = 0.22f),
                            strokeColor = primary.copy(alpha = 0.45f),
                            strokeWidth = 1f,
                        )
                    }
                }
            } else {
                EmptyState("Map unavailable", "This build has no Google Maps key. Your stats above are still up to date.")
            }
        }
    }
}

@Composable
fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
