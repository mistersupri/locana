package app.locana.ui.play

import androidx.compose.foundation.background
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.runtime.remember
import app.locana.domain.Formatting
import app.locana.ui.components.HeroPanel
import app.locana.ui.components.IconTile
import app.locana.ui.components.pressScale
import app.locana.ui.theme.HeroNumberStyle
import app.locana.ui.theme.LocalHero
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Approval
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MilitaryTech
import androidx.compose.material.icons.outlined.Museum
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.locana.data.api.AchievementDto
import app.locana.data.api.CollectibleDto
import app.locana.ui.components.Avatar
import app.locana.ui.components.EmptyState
import app.locana.ui.components.ErrorState
import app.locana.ui.components.LoadingState
import app.locana.ui.components.SectionHeader
import app.locana.ui.components.StatusChip
import app.locana.ui.components.TileTrack
import app.locana.ui.components.UiState
import app.locana.ui.components.rarityLabel
import app.locana.ui.explore.BackScaffold
import app.locana.ui.explore.Stat
import app.locana.ui.nav.Routes
import app.locana.ui.theme.LocalReward
import app.locana.ui.theme.NumberStyle

/** Icons chosen for what each achievement or item is about; no generic sparkles or stars. */
fun iconFor(key: String): ImageVector = when {
    key.contains("first") -> Icons.Outlined.Explore
    key.contains("neighborhood") -> Icons.Outlined.Map
    key.contains("weekend") -> Icons.Outlined.WbSunny
    key.contains("landmarks") || key.startsWith("stamp_museum") -> Icons.Outlined.Museum
    key.contains("streak") -> Icons.Outlined.LocalFireDepartment
    key.contains("team") -> Icons.Outlined.Groups
    key.contains("champion") -> Icons.Outlined.Flag
    key.contains("completionist") -> Icons.Outlined.GridOn
    key.contains("quests") -> Icons.Outlined.TaskAlt
    key.contains("level") -> Icons.AutoMirrored.Outlined.TrendingUp
    key.startsWith("frame") -> Icons.Outlined.CropFree
    key.startsWith("marker") -> Icons.Outlined.Place
    key.startsWith("stamp") -> Icons.Outlined.Approval
    key == "title" -> Icons.Outlined.Badge
    else -> Icons.Outlined.MilitaryTech
}

@Composable
fun PlayScreen(nav: NavController, vm: PlayViewModel = hiltViewModel()) {
    val me by vm.profile.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val reward = LocalReward.current

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(16.dp)) {
        item {
            Text("Play", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(12.dp))
        }
        val m = me
        if (m == null) {
            item { if (error != null) ErrorState(error!!, { vm.refresh() }) else LoadingState() }
        } else {
            val p = m.progress
            item {
                // Focal point of the tab: the level number, and how far to the next one.
                val hero = LocalHero.current
                val source = remember { MutableInteractionSource() }
                HeroPanel(Modifier.pressScale(source).clickable(source, indication = LocalIndication.current, role = Role.Button) { nav.navigate(Routes.LEVELS) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(m.profile.displayName, m.profile.avatarKey, size = 40.dp, framed = m.profile.avatarFrameKey != null)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.profile.displayName, style = MaterialTheme.typography.titleMedium, color = hero.text)
                            Text(
                                m.profile.titleKey?.removePrefix("title_")?.replaceFirstChar { c -> c.uppercase() } ?: "@${m.username}",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (m.profile.titleKey != null) hero.reward else hero.muted,
                            )
                        }
                        StreakBadge(p.currentStreak)
                    }
                    Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            Text("Level", style = MaterialTheme.typography.labelLarge, color = hero.muted)
                            Text("${p.level}", style = HeroNumberStyle, color = hero.text)
                        }
                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 8.dp)) {
                            Text("${p.totalXp} XP", style = MaterialTheme.typography.titleLarge, color = hero.reward)
                            Text(
                                p.nextLevelXp?.let { "${it - p.totalXp} to level ${p.level + 1}" } ?: "Top level reached",
                                style = MaterialTheme.typography.labelMedium,
                                color = hero.muted,
                            )
                        }
                    }
                    TileTrack(p.levelProgress.toFloat(), Modifier.padding(top = 10.dp), fill = hero.reward, track = hero.tileHidden, height = 12.dp)
                }
            }
            item {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Row(Modifier.padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Stat("${p.tilesDiscovered}", "tiles")
                        Stat("${stats?.landmarksVisited ?: 0}", "landmarks")
                        Stat("${p.questsCompleted}", "quests")
                        Stat(Formatting.distance(p.totalDistanceM), "walked")
                    }
                }
                stats?.let { s ->
                    Spacer(Modifier.height(16.dp))
                    WeekBars(s.last7Days.map { it.xp })
                }
            }
            if (recent.isNotEmpty()) {
                item { SectionHeader("Recently unlocked", actionLabel = "All", onAction = { nav.navigate(Routes.ACHIEVEMENTS) }) }
                items(recent, key = { it.code }) { a -> AchievementRow(a) }
            }
            item {
                SectionHeader("More")
                NavRow("Achievements", "Milestones for exploring and playing together", Icons.Outlined.MilitaryTech) { nav.navigate(Routes.ACHIEVEMENTS) }
                NavRow("Collection", "Frames, titles, markers and landmark stamps", Icons.Outlined.Approval) { nav.navigate(Routes.COLLECTIBLES) }
                NavRow("Friends leaderboard", "This week's XP among your circles", Icons.Outlined.Leaderboard) { nav.navigate(Routes.leaderboard("friends")) }
                NavRow("Circle leaderboard", "Who's carrying the team this week", Icons.Outlined.Groups) { nav.navigate(Routes.leaderboard("circle")) }
                NavRow("Level path", "XP needed for each level and its reward", Icons.AutoMirrored.Outlined.TrendingUp) { nav.navigate(Routes.LEVELS) }
            }
        }
    }
}

/** Seven small tiles of daily XP: a glance at this week's rhythm, not a chart to study. */
@Composable
private fun WeekBars(values: List<Int>) {
    if (values.isEmpty()) return
    val max = values.max().coerceAtLeast(1)
    val reward = LocalReward.current
    Row(Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        values.forEach { v ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .height((6 + 34 * v / max).dp)
                    .background(if (v > 0) reward.reward else MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.extraSmall),
            )
        }
    }
    Text("XP per day, last 7 days", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun NavRow(title: String, sub: String, icon: ImageVector, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    ListItem(
        leadingContent = { IconTile(icon, scheme.surfaceContainerHighest, scheme.onSurfaceVariant, size = 40.dp) },
        headlineContent = { Text(title) },
        supportingContent = { Text(sub) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    )
}

/** Streak as a flame badge: only shown once there is a streak, so it never nags with a zero. */
@Composable
private fun StreakBadge(days: Int) {
    if (days <= 0) return
    val hero = LocalHero.current
    Surface(color = hero.raised, shape = RoundedCornerShape(50)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.LocalFireDepartment, contentDescription = null, tint = hero.reward, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("$days day streak", style = MaterialTheme.typography.labelMedium, color = hero.text)
        }
    }
}

@Composable
fun AchievementRow(a: AchievementDto) {
    val unlocked = a.unlockedAt != null
    val reward = LocalReward.current
    // Rarity drives emphasis: legendary and epic unlocks get the reward border.
    val emphasized = unlocked && (a.rarity == "EPIC" || a.rarity == "LEGENDARY")
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .then(if (emphasized) Modifier.border(1.5.dp, reward.reward, MaterialTheme.shapes.medium) else Modifier),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).background(if (unlocked) reward.rewardContainer else MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center,
            ) {
                Icon(iconFor(a.iconKey), contentDescription = null, tint = if (unlocked) reward.onRewardContainer else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).alpha(if (unlocked) 1f else 0.8f)) {
                Text(a.name, style = MaterialTheme.typography.titleSmall)
                Text(a.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!unlocked) {
                    TileTrack(a.progress.toFloat() / a.target.coerceAtLeast(1), Modifier.padding(top = 8.dp), segments = minOf(a.target, 10), height = 6.dp)
                    Text("${a.progress} of ${a.target}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 2.dp))
                }
            }
            Spacer(Modifier.width(8.dp))
            StatusChip(rarityLabel(a.rarity))
        }
    }
}

@Composable
fun AchievementsScreen(nav: NavController, vm: AchievementsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    BackScaffold("Achievements", nav) { pad ->
        when (val s = state) {
            UiState.Loading -> LoadingState(Modifier.padding(pad))
            is UiState.Error -> ErrorState(s.message, { vm.load() }, Modifier.padding(pad))
            is UiState.Data -> {
                val (done, open) = s.value.partition { it.unlockedAt != null }
                LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp)) {
                    item { Text("${done.size} of ${s.value.size} unlocked", style = MaterialTheme.typography.titleMedium) }
                    if (done.isNotEmpty()) item { SectionHeader("Unlocked") }
                    items(done, key = { it.code }) { AchievementRow(it) }
                    if (open.isNotEmpty()) item { SectionHeader("In progress") }
                    items(open, key = { it.code }) { AchievementRow(it) }
                }
            }
        }
    }
}

@Composable
fun CollectiblesScreen(nav: NavController, vm: CollectiblesViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val me by vm.profile.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val equipped = setOfNotNull(me?.profile?.avatarFrameKey, me?.profile?.titleKey, me?.profile?.markerKey)
    BackScaffold("Collection", nav) { pad ->
        when (val s = state) {
            UiState.Loading -> LoadingState(Modifier.padding(pad))
            is UiState.Error -> ErrorState(s.message, { vm.load() }, Modifier.padding(pad))
            is UiState.Data -> LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp)) {
                error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                val groups = s.value.groupBy { it.kind }
                listOf("AVATAR_FRAME" to "Avatar frames", "TITLE" to "Titles", "MAP_MARKER" to "Map markers", "BADGE" to "Badges", "LANDMARK_STAMP" to "Landmark stamps").forEach { (kind, title) ->
                    val list = groups[kind].orEmpty()
                    if (list.isEmpty()) return@forEach
                    item(key = kind) {
                        SectionHeader("$title  ${list.count { it.acquiredAt != null }}/${list.size}")
                    }
                    items(list, key = { it.code }) { c -> CollectibleRow(c, c.code in equipped, onToggle = { vm.toggle(c) }) }
                }
            }
        }
    }
}

@Composable
private fun CollectibleRow(c: CollectibleDto, equipped: Boolean, onToggle: () -> Unit) {
    val owned = c.acquiredAt != null
    val equippable = owned && c.kind in setOf("AVATAR_FRAME", "TITLE", "MAP_MARKER")
    ListItem(
        leadingContent = { Icon(iconFor(c.iconKey), contentDescription = null, modifier = Modifier.alpha(if (owned) 1f else 0.4f)) },
        headlineContent = { Text(c.name) },
        supportingContent = { Text(if (owned) c.description else "Locked. ${c.description}", maxLines = 2, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            when {
                equipped -> StatusChip("Equipped", container = MaterialTheme.colorScheme.primaryContainer, content = MaterialTheme.colorScheme.onPrimaryContainer)
                equippable -> StatusChip("Equip")
                else -> StatusChip(rarityLabel(c.rarity))
            }
        },
        modifier = if (equippable) Modifier.clickable(role = Role.Button, onClickLabel = if (equipped) "Unequip" else "Equip", onClick = onToggle) else Modifier,
    )
}

@Composable
fun LeaderboardScreen(nav: NavController, vm: LeaderboardViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val q by vm.query.collectAsStateWithLifecycle()
    val circles by vm.circleList.collectAsStateWithLifecycle()
    val more by vm.loadingMore.collectAsStateWithLifecycle()
    BackScaffold("Leaderboards", nav) { pad ->
        Column(Modifier.padding(pad)) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("friends" to "Friends", "circle" to "Circle", "global" to "Season").forEach { (k, l) ->
                    FilterChip(selected = q.scope == k, onClick = { vm.set { it.copy(scope = k, period = if (k == "global") "season" else it.period) } }, label = { Text(l) })
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("xp" to "XP", "tiles" to "Exploration", "quests" to "Quests").forEach { (k, l) ->
                    FilterChip(selected = q.metric == k, onClick = { vm.set { it.copy(metric = k) } }, label = { Text(l) })
                }
                if (q.scope != "global") listOf("week" to "This week", "season" to "Season").forEach { (k, l) ->
                    FilterChip(selected = q.period == k, onClick = { vm.set { it.copy(period = k) } }, label = { Text(l) })
                }
            }
            if (q.scope == "circle") {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    circles?.value.orEmpty().forEach { c -> FilterChip(selected = q.circleId == c.id, onClick = { vm.set { it.copy(circleId = c.id) } }, label = { Text(c.name) }) }
                }
            }
            when (val s = state) {
                UiState.Loading -> LoadingState()
                is UiState.Error -> ErrorState(s.message, { vm.load() })
                is UiState.Data -> {
                    val b = s.value
                    if (b.entries.isEmpty()) {
                        EmptyState("No scores yet", "Scores appear once someone earns XP, reveals tiles or claims a quest in this period.")
                    } else {
                        if (b.me.optedOut) {
                            Text(
                                "You're hidden from other players' boards. Only you see your row. Change this in Privacy.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                        LazyColumn {
                            items(b.entries, key = { it.userId }) { e ->
                                ListItem(
                                    leadingContent = { Text("#${e.rank}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(44.dp)) },
                                    headlineContent = { Text(e.name + if (e.isMe) " (you)" else "", style = if (e.isMe) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyLarge) },
                                    supportingContent = { Text("Level ${e.level}") },
                                    trailingContent = { Text(if (b.metric == "xp") "${e.score} XP" else "${e.score}", style = MaterialTheme.typography.titleSmall) },
                                )
                                HorizontalDivider()
                            }
                            if (b.entries.size < b.total) item {
                                LaunchedEffect(b.entries.size) { vm.more() }
                                if (more) LoadingState()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LevelsScreen(nav: NavController, vm: LevelsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val me by vm.profile.collectAsStateWithLifecycle()
    val current = me?.progress?.level ?: 1
    BackScaffold("Level path", nav) { pad ->
        when (val s = state) {
            UiState.Loading -> LoadingState(Modifier.padding(pad))
            is UiState.Error -> ErrorState(s.message, { vm.load() }, Modifier.padding(pad))
            is UiState.Data -> LazyColumn(Modifier.padding(pad)) {
                items(s.value, key = { it.level }) { l ->
                    ListItem(
                        leadingContent = { Text("${l.level}", style = MaterialTheme.typography.titleLarge, color = if (l.level <= current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) },
                        headlineContent = { Text("${l.totalXp} XP") },
                        supportingContent = l.rewardCollectibleCode?.let { { Text("Unlocks ${it.substringAfter('_').replaceFirstChar { c -> c.uppercase() }}") } },
                        trailingContent = { if (l.level == current) StatusChip("You're here") },
                    )
                }
            }
        }
    }
}

