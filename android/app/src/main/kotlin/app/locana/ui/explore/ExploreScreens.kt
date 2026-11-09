package app.locana.ui.explore

import app.locana.ui.theme.cardBrush
import androidx.compose.foundation.clickable
import app.locana.ui.components.ListRowCard
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material.icons.outlined.TempleBuddhist
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.Info
import androidx.compose.foundation.text.BasicTextField
import app.locana.ui.theme.LocalTokens
import app.locana.ui.components.XpBadge
import app.locana.ui.components.TagPill
import app.locana.ui.components.LocanaCard
import app.locana.ui.components.Illustration
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Museum
import androidx.compose.material3.AssistChip
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import app.locana.ui.components.HeroPanel
import app.locana.ui.components.IconTile
import app.locana.ui.components.RollingNumber
import app.locana.ui.components.TileField
import app.locana.ui.components.conditionIcon
import app.locana.ui.components.pressScale
import app.locana.ui.theme.HeroNumberStyle
import app.locana.ui.theme.LocalHero
import app.locana.ui.theme.Motion
import app.locana.ui.theme.Palette
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
import app.locana.ui.components.LocanaSwitch
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

/** Screen with the mockup's top bar: white square back button, Bricolage title, optional action on the right. */
@Composable
fun BackScaffold(title: String, nav: NavController, actions: @Composable () -> Unit = {}, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { app.locana.ui.components.LocanaTopBar(title, onBack = { nav.popBackStack() }, action = actions) },
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
    val data = (state as? UiState.Data)?.value

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Explore", style = MaterialTheme.typography.headlineLarge.copy(fontSize = 30.sp))
            Spacer(Modifier.height(12.dp))
            TodayHero(
                daily = data?.quests.orEmpty().filter { it.type == "DAILY" },
                tilesRevealed = data?.stats?.tilesDiscovered ?: 0,
                exploring = exploring,
                hasPermission = perms.anyLocation,
                error = toggleError,
                onToggle = { if (perms.anyLocation) vm.setExploration(it) else nav.navigate(Routes.PERMISSIONS) },
            )
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShortcutChip("Explored map", Icons.Outlined.Map, Modifier.weight(1f)) { nav.navigate(Routes.EXPLORATION_MAP) }
                ShortcutChip("Landmarks", Icons.Outlined.Museum, Modifier.weight(1f)) { nav.navigate(Routes.LANDMARKS) }
                ShortcutChip("History", Icons.Outlined.History, Modifier.weight(1f)) { nav.navigate(Routes.QUEST_HISTORY) }
            }
        }
        when (val s = state) {
            UiState.Loading -> item { LoadingState(label = "Loading quests") }
            is UiState.Error -> item { ErrorState(s.message, vm::load) }
            is UiState.Data -> {
                s.cachedAtMs?.let { at -> item { CachedNotice(at, { vm.load() }, Modifier.padding(top = 8.dp)) } }
                val groups = s.value.quests.groupBy { it.type }
                if (s.value.quests.isEmpty()) {
                    item {
                        EmptyState(
                            "No quests right now",
                            "New daily quests appear at midnight. Circle quests need a team circle, which you can pick in Profile.",
                        )
                    }
                }
                // Rhythm: the event is one wide banner, today's quests a list, the week a swipeable row.
                val specials = groups["SPECIAL"].orEmpty()
                val season = s.value.events.firstOrNull { it.live }
                if (specials.isNotEmpty() || season != null) item(key = "h-event") { SectionHeader("Event") }
                items(specials, key = { it.id }) { q -> EventQuestBanner(q, season?.name) { nav.navigate(Routes.quest(q.id)) } }
                if (specials.isEmpty() && season != null) item(key = "season") { SeasonNote(season.name, season.description, endsIn(season.endsAt)) }
                val daily = groups["DAILY"].orEmpty()
                if (daily.isNotEmpty()) {
                    item(key = "h-daily") { SectionHeader("Today") }
                    items(daily, key = { it.id }) { q -> QuestCard(q) { nav.navigate(Routes.quest(q.id)) } }
                }
                val weekly = groups["WEEKLY"].orEmpty()
                if (weekly.isNotEmpty()) {
                    item(key = "h-weekly") { SectionHeader("This week") }
                    item(key = "weekly") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(end = 16.dp)) {
                            items(weekly, key = { it.id }) { q -> WeeklyQuestCard(q) { nav.navigate(Routes.quest(q.id)) } }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Focal point of Explore: how many of today's quests are done, as a big number, with the tiles the
 * player has revealed lit in the field beside it. The exploration switch lives here because it is
 * what makes any of this count.
 */
@Composable
private fun TodayHero(daily: List<QuestDto>, tilesRevealed: Int, exploring: Boolean, hasPermission: Boolean, error: String?, onToggle: (Boolean) -> Unit) {
    val t = LocalTokens.current
    val done = daily.count { it.status == "COMPLETED" || it.status == "CLAIMED" }
    HeroPanel(brush = cardBrush(Color.White, Color(0xFFECE4FF), vertical = true)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Today", style = MaterialTheme.typography.labelLarge, color = t.ink2)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$done", style = HeroNumberStyle.copy(fontSize = 52.sp, lineHeight = 52.sp), color = MaterialTheme.colorScheme.onSurface)
                    Text(" / ${daily.size} quests", style = MaterialTheme.typography.titleSmall, color = t.ink2, modifier = Modifier.padding(bottom = 8.dp))
                }
            }
            Box(Modifier.size(110.dp).clip(RoundedCornerShape(30.dp)).border(4.dp, Color.White, RoundedCornerShape(30.dp)), contentAlignment = Alignment.Center) {
                Illustration("compass", size = 92.dp)
            }
        }
        Text(
            if (tilesRevealed == 0) "Your map is still dark. Walk somewhere new to light the first tile." else "$tilesRevealed tiles revealed so far",
            style = MaterialTheme.typography.bodySmall,
            color = t.ink2,
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
        )
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(LocalTokens.current.card).clickable(role = Role.Switch) { onToggle(!exploring) }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Exploration " + if (exploring) "on" else "off", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold))
                Text(
                    if (exploring) "Walks reveal tiles and count for quests. Circles never see where you went."
                    else "Turn on so walks count. Separate from sharing your location.",
                    style = MaterialTheme.typography.bodySmall,
                    color = t.ink2,
                )
            }
            Spacer(Modifier.width(12.dp))
            LocanaSwitch(checked = exploring, onCheckedChange = onToggle)
        }
        if (!hasPermission) Text("Needs location permission first.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun ShortcutChip(label: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    LocanaCard(modifier.heightIn(min = 42.dp), shape = RoundedCornerShape(14.dp), onClick = onClick, shadow = 2.dp) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
            Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold), maxLines = 1)
        }
    }
}

/** Special events get one wide yellow banner with the reward up front: the rarest, most valuable quest. */
@Composable
private fun EventQuestBanner(q: QuestDto, seasonName: String?, onClick: () -> Unit) {
    val t = LocalTokens.current
    val source = remember { MutableInteractionSource() }
    Box(Modifier.fillMaxWidth().padding(bottom = 4.dp).pressScale(source)) {
        Box(Modifier.matchParentSize().graphicsLayer { translationY = 4.dp.toPx() }.background(t.yellowDark, RoundedCornerShape(24.dp)))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Color(0xFFFFD45C), Color(0xFFFFB23C))))
                .clickable(source, indication = LocalIndication.current, role = Role.Button, onClick = onClick)
                .padding(16.dp),
        ) {
            Illustration("monas", Modifier.align(Alignment.TopEnd), size = 120.dp)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TagPill(listOfNotNull(seasonName, endsIn(q.expiresAt)).joinToString(" · "), Color.White.copy(alpha = 0.4f), Color(0xFF1F1645), icon = Icons.Outlined.CalendarMonth)
                Text(q.name, style = MaterialTheme.typography.headlineSmall, color = Color(0xFF1F1645), modifier = Modifier.fillMaxWidth(0.6f))
                Text(q.description, style = MaterialTheme.typography.bodySmall, color = Color(0xFF4A3300), modifier = Modifier.fillMaxWidth(0.62f))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuestProgressTrack(q.progress, q.target, q.unit, Modifier.weight(1f), fill = Color(0xFF1F1645), track = Color.White.copy(alpha = 0.5f))
                    XpBadge("+${q.rewardXp} XP")
                }
                Text(Formatting.progress(q.progress, q.target, q.unit), style = MaterialTheme.typography.labelMedium, color = Color(0xFF4A3300))
            }
        }
    }
}

@Composable
private fun SeasonNote(name: String, description: String, ends: String) {
    LocanaCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), shadow = 2.dp) {
        Column(Modifier.padding(16.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold))
            Text(description, style = MaterialTheme.typography.bodyMedium)
            Text(ends, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/**
 * Daily quest card from the mockup: a tinted icon tile, name and XP badge, the description, a smooth bar and
 * the progress line. A finished quest turns violet and says "Ready to claim" so the one action left is obvious.
 */
@Composable
fun QuestCard(q: QuestDto, onClick: () -> Unit) {
    val completed = q.status == "COMPLETED"
    val claimed = q.status == "CLAIMED"
    val scheme = MaterialTheme.colorScheme
    val t = LocalTokens.current
    LocanaCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), onClick = onClick, shadow = 3.dp) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(conditionIcon(q.condition), if (completed) scheme.primary else scheme.primaryContainer, if (completed) scheme.onPrimary else scheme.primary, size = 42.dp, modifier = Modifier.alpha(if (claimed) 0.6f else 1f))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(q.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold))
                    Text(q.description, style = MaterialTheme.typography.bodySmall, color = t.ink2)
                }
                Spacer(Modifier.width(8.dp))
                if (claimed) StatusChip("Claimed") else XpChip(q.rewardXp)
            }
            QuestProgressTrack(q.progress, q.target, q.unit)
            Row {
                Text(Formatting.progress(q.progress, q.target, q.unit), style = MaterialTheme.typography.labelMedium, color = t.ink2, modifier = Modifier.weight(1f))
                Text(
                    if (completed) "Ready to claim" else if (claimed) "Done" else endsIn(q.expiresAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (completed) scheme.primary else t.ink2,
                )
            }
        }
    }
}

/** Weekly quests are longer goals: compact cards in a row, so the week reads as a set. */
@Composable
private fun WeeklyQuestCard(q: QuestDto, onClick: () -> Unit) {
    val completed = q.status == "COMPLETED"
    val scheme = MaterialTheme.colorScheme
    LocanaCard(Modifier.width(230.dp), shape = RoundedCornerShape(22.dp), onClick = onClick, color = if (completed) scheme.primaryContainer else LocalTokens.current.card, shadow = 3.dp) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(conditionIcon(q.condition), scheme.primaryContainer, scheme.primary, size = 38.dp)
                Spacer(Modifier.weight(1f))
                XpChip(q.rewardXp)
            }
            Text(q.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), modifier = Modifier.padding(top = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(q.description, style = MaterialTheme.typography.bodySmall, color = LocalTokens.current.ink2, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 10.dp))
            QuestProgressTrack(q.progress, q.target, q.unit)
            Text(
                if (completed) "Ready to claim" else "${Formatting.progress(q.progress, q.target, q.unit)} · ${endsIn(q.expiresAt)}",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 6.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
                    LazyColumn(contentPadding = PaddingValues(20.dp)) {
                        item {
                            // Focal point: how far along this quest is, as the big number.
                            val hero = LocalHero.current
                            HeroPanel {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconTile(conditionIcon(q.condition), hero.raised, hero.accent)
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(q.name, style = MaterialTheme.typography.headlineSmall, color = hero.text)
                                        Text(
                                            q.type.lowercase().replaceFirstChar { it.uppercase() } + " · " +
                                                if (q.status == "ACTIVE") endsIn(q.expiresAt) else q.status.lowercase().replaceFirstChar { it.uppercase() },
                                            style = MaterialTheme.typography.labelMedium,
                                            color = hero.muted,
                                        )
                                    }
                                }
                                Text(q.description, style = MaterialTheme.typography.bodyLarge, color = hero.text, modifier = Modifier.padding(top = 14.dp))
                                if (q.unit == "tiles") {
                                    Box(Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                                        TileField(lit = q.progress.coerceAtMost(25), columns = 9, rows = 5, tileSize = 22.dp, gap = 4.dp, litColor = MaterialTheme.colorScheme.primary, hiddenColor = MaterialTheme.colorScheme.primaryContainer, description = "${q.progress} of ${q.target} tiles")
                                    }
                                }
                                Row(Modifier.padding(top = 18.dp), verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        if (q.unit == "meters") Formatting.distance(q.progress) else "${q.progress}",
                                        style = HeroNumberStyle,
                                        color = hero.text,
                                    )
                                    Text(
                                        " / " + if (q.unit == "meters") Formatting.distance(q.target) else "${q.target} ${q.unit}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = hero.muted,
                                        modifier = Modifier.padding(bottom = 10.dp).weight(1f),
                                    )
                                    XpBadge("+${q.rewardXp} XP", Modifier.padding(bottom = 10.dp))
                                }
                                QuestProgressTrack(q.progress, q.target, q.unit, Modifier.height(14.dp).padding(top = 2.dp), fill = hero.accent, track = hero.tileHidden)
                            }
                            Row(
                                Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(LocalTokens.current.skySoft).padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(Icons.Outlined.Info, contentDescription = null, tint = Color(0xFF1E6FB0), modifier = Modifier.size(18.dp))
                                Text(
                                    "Progress counts only from precise location fixes taken while exploration is on. Repeat visits to the same tile do not count twice.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF16507F),
                                )
                            }
                            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
                            if (q.status == "COMPLETED") {
                                Spacer(Modifier.height(20.dp))
                                PrimaryButton("Claim ${q.rewardXp} XP", vm::claim, Modifier.fillMaxWidth(), loading = claiming)
                            }
                            if (q.recentProgress.isNotEmpty()) SectionHeader("Recent progress")
                        }
                        items(q.recentProgress) { p ->
                            ListRowCard("+${p.delta} ${q.unit}", subtitle = Instant.parse(p.at).toString().take(16).replace('T', ' '), icon = Icons.Outlined.GridView, modifier = Modifier.padding(top = 8.dp), trailing = null)
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
                Box(Modifier.padding(vertical = 8.dp)) { RollingNumber(xp, NumberStyle, reward.reward, prefix = "+", suffix = " XP") }
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
                EmptyState("Nothing here yet", "Claimed and expired quests show up here.", Modifier.padding(pad), illustration = "chest")
            } else {
                LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = pad.calculateTopPadding() + 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(s.value, key = { it.id }) { q ->
                        ListRowCard(
                            title = q.name,
                            subtitle = q.startedAt.take(10) + " · " + Formatting.progress(q.progress, q.target, q.unit),
                            icon = conditionIcon(q.condition),
                            trailing = { if (q.status == "CLAIMED") XpChip(q.rewardXp) else StatusChip("Expired") },
                        )
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
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(16.dp)).background(LocalTokens.current.card).border(2.dp, LocalTokens.current.line, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Outlined.Search, contentDescription = null, tint = LocalTokens.current.ink3)
                androidx.compose.foundation.text.BasicTextField(
                    value = q,
                    onValueChange = vm::onQuery,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f).semantics { contentDescription = "Search public places" },
                    decorationBox = { inner -> Box { if (q.isEmpty()) Text("Search public places", style = MaterialTheme.typography.bodyLarge, color = LocalTokens.current.ink3); inner() } },
                )
            }
            if (!located) {
                Text(
                    "We don't know where you are right now, so distances are hidden.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp),
                )
            }
            when (val s = state) {
                UiState.Loading -> LoadingState()
                is UiState.Error -> ErrorState(s.message, { vm.search() })
                is UiState.Data -> if (s.value.isEmpty()) {
                    EmptyState("No landmarks found", if (q.isBlank()) "No public landmarks are mapped near you yet." else "Try another name.", illustration = "city")
                } else {
                    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        item {
                            // The first landmark in the list gets the banner the mockup calls "nearest".
                            s.value.firstOrNull()?.let { n ->
                                val t = LocalTokens.current
                                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cardBrush(t.mintSoft, t.skySoft)).padding(16.dp)) {
                                    Illustration("monas", Modifier.align(Alignment.CenterEnd), size = 110.dp)
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TagPill(if (located) "Nearest landmark" else "Landmark", Color.White, MaterialTheme.colorScheme.onSurface, icon = Icons.Outlined.Navigation)
                                        Text(n.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth(0.62f))
                                        XpBadge("+${n.rewardXp} XP")
                                    }
                                }
                            }
                        }
                        items(s.value, key = { it.id }) { l ->
                            val t = LocalTokens.current
                            val (icon, tile, tint) = when (l.category) {
                                "library" -> Triple(Icons.Outlined.LocalLibrary, t.skySoft, Color(0xFF1E6FB0))
                                "museum" -> Triple(Icons.Outlined.Museum, t.coralSoft, t.coralDark)
                                "religious" -> Triple(Icons.Outlined.TempleBuddhist, t.mintSoft, Color(0xFF0E8A5D))
                                "park" -> Triple(Icons.Outlined.Park, t.mintSoft, Color(0xFF0E8A5D))
                                else -> Triple(Icons.Outlined.Museum, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary)
                            }
                            ListRowCard(
                                title = l.name,
                                subtitle = listOfNotNull(l.category.replaceFirstChar { it.uppercase() }, "${Formatting.distance(l.distanceM)} away".takeIf { located }, l.description).joinToString(" · "),
                                icon = icon, tileColor = tile, tileTint = tint,
                                trailing = { if (l.visited) StatusChip("Visited") else XpChip(l.rewardXp) },
                                onClick = l.territoryId?.let { id -> { nav.navigate(Routes.territory(id)) } },
                            )
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
                val t = LocalTokens.current
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat("${s.tilesDiscovered}", "tiles", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    Stat("${s.landmarksVisited}", "landmarks", t.coralDark, Modifier.weight(1f))
                    Stat(Formatting.distance(s.totalDistanceM), "walked", Color(0xFF0E8A5D), Modifier.weight(1f))
                    Stat("${s.currentStreak}", "day streak", t.onYellowSoft, Modifier.weight(1f))
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
                Box(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxSize().clip(RoundedCornerShape(28.dp)).background(Color(0xFF4A3B6E)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(28.dp)) {
                        Illustration("compass", size = 110.dp)
                        Text("Your map is still foggy", style = MaterialTheme.typography.titleLarge, color = Color.White, modifier = Modifier.padding(top = 12.dp))
                        Text("Every new street you walk clears a tile. Your stats above are still up to date.", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun Stat(value: String, label: String, tint: Color = MaterialTheme.colorScheme.onSurface, modifier: Modifier = Modifier) {
    LocanaCard(modifier, shape = RoundedCornerShape(16.dp), shadow = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = tint)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
