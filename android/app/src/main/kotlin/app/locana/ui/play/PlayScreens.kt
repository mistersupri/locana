package app.locana.ui.play

import app.locana.ui.theme.cardBrush
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
import app.locana.ui.theme.LocalTokens
import app.locana.ui.components.TagPill
import app.locana.ui.components.Segmented
import app.locana.ui.components.LocanaChip
import app.locana.ui.components.LocanaCard
import app.locana.ui.components.ListRowCard
import app.locana.ui.components.Illustration
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.offset
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

    val t = LocalTokens.current
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Play", style = MaterialTheme.typography.headlineLarge.copy(fontSize = 30.sp))
            Spacer(Modifier.height(4.dp))
        }
        val m = me
        if (m == null) {
            item { if (error != null) ErrorState(error!!, { vm.refresh() }) else LoadingState() }
        } else {
            val p = m.progress
            item {
                // Focal point of the tab: the level number, and how far to the next one.
                val source = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .pressScale(source)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF6B3BF5), Color(0xFF8D5BFF))))
                        .clickable(source, indication = LocalIndication.current, role = Role.Button, onClickLabel = "Open the level path") { nav.navigate(Routes.LEVELS) }
                        .padding(18.dp),
                ) {
                    Illustration("trophy", Modifier.align(Alignment.TopEnd), size = 120.dp)
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).border(3.dp, Color.White, CircleShape).clip(CircleShape)) {
                                Avatar(m.profile.displayName, m.profile.avatarKey, size = 40.dp, framed = m.profile.avatarFrameKey != null, userId = m.id, photoVersion = m.profile.avatarPhotoVersion)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(m.profile.displayName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), color = Color.White)
                                Text(
                                    m.profile.titleKey?.removePrefix("title_")?.replaceFirstChar { c -> c.uppercase() } ?: "@${m.username}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.8f),
                                )
                            }
                        }
                        Text("Level", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(top = 14.dp))
                        Text("${p.level}", style = HeroNumberStyle.copy(fontSize = 56.sp), color = Color.White)
                        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom) {
                            Text("${p.totalXp} XP", style = MaterialTheme.typography.titleLarge, color = t.yellow, modifier = Modifier.weight(1f))
                            Text(
                                p.nextLevelXp?.let { "${it - p.totalXp} to level ${p.level + 1}" } ?: "Top level reached",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.8f),
                            )
                        }
                        TileTrack(p.levelProgress.toFloat(), Modifier.padding(top = 10.dp), fill = t.yellow, track = Color.White.copy(alpha = 0.25f), height = 12.dp)
                        if (p.currentStreak > 0) StreakBadge(p.currentStreak)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(Icons.Outlined.GridView, "${p.tilesDiscovered}", "tiles", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    StatTile(Icons.Outlined.Museum, "${stats?.landmarksVisited ?: 0}", "landmarks", t.coralSoft, t.coralDark, Modifier.weight(1f))
                    StatTile(Icons.Outlined.TrackChanges, "${p.questsCompleted}", "quests", t.yellowSoft, t.yellowDark, Modifier.weight(1f))
                    StatTile(Icons.AutoMirrored.Outlined.DirectionsWalk, Formatting.distance(p.totalDistanceM), "walked", t.mintSoft, Color(0xFF0E8A5D), Modifier.weight(1f))
                }
            }
            if (recent.isNotEmpty()) {
                item { SectionHeader("Recently unlocked", actionLabel = "All", onAction = { nav.navigate(Routes.ACHIEVEMENTS) }) }
                items(recent, key = { it.code }) { a -> AchievementRow(a) }
            }
            item { SectionHeader("More") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    NavRow("Achievements", "Milestones for exploring and playing together", Icons.Outlined.MilitaryTech, t.yellowSoft, t.yellowDark) { nav.navigate(Routes.ACHIEVEMENTS) }
                    NavRow("Collection", "Frames, titles, markers and landmark stamps", Icons.Outlined.Diamond, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary) { nav.navigate(Routes.COLLECTIBLES) }
                    NavRow("Friends leaderboard", "This week's XP among your circles", Icons.Outlined.Groups, t.mintSoft, Color(0xFF0E8A5D)) { nav.navigate(Routes.leaderboard("friends")) }
                    NavRow("Circle leaderboard", "Who's carrying the team this week", Icons.Outlined.Leaderboard, t.coralSoft, t.coralDark) { nav.navigate(Routes.leaderboard("circle")) }
                    NavRow("Level path", "XP needed for each level and its reward", Icons.AutoMirrored.Outlined.TrendingUp, t.skySoft, Color(0xFF1E6FB0)) { nav.navigate(Routes.LEVELS) }
                }
            }
        }
    }
}

@Composable
private fun StatTile(icon: ImageVector, value: String, label: String, tile: Color, tint: Color, modifier: Modifier) {
    LocanaCard(modifier, shape = RoundedCornerShape(18.dp), shadow = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            IconTile(icon, tile, tint, size = 30.dp)
            Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NavRow(title: String, sub: String, icon: ImageVector, tile: Color, tint: Color, onClick: () -> Unit) {
    ListRowCard(title = title, subtitle = sub, icon = icon, tileColor = tile, tileTint = tint, onClick = onClick)
}

/** Streak as a flame badge: only shown once there is a streak, so it never nags with a zero. */
@Composable
private fun StreakBadge(days: Int) {
    if (days <= 0) return
    TagPill("$days day streak", Color.White.copy(alpha = 0.22f), Color.White, Modifier.padding(top = 10.dp), icon = Icons.Outlined.LocalFireDepartment)
}

/** Rarity pill colours from the mockup: common is quiet, rare is sky, epic is violet, legendary is gold. */
@Composable
internal fun RarityPill(rarity: String, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    val (bg, fg) = when (rarity) {
        "RARE" -> t.skySoft to Color(0xFF1E6FB0)
        "EPIC" -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
        "LEGENDARY" -> t.yellowSoft to t.onYellowSoft
        else -> MaterialTheme.colorScheme.surfaceContainerHigh to t.ink2
    }
    TagPill(rarityLabel(rarity), bg, fg, modifier)
}

@Composable
fun AchievementRow(a: AchievementDto) {
    val unlocked = a.unlockedAt != null
    val t = LocalTokens.current
    LocanaCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), shadow = 2.dp) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(iconFor(a.iconKey), if (unlocked) t.yellowSoft else MaterialTheme.colorScheme.primaryContainer, if (unlocked) t.yellowDark else MaterialTheme.colorScheme.primary, size = 44.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(a.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold))
                Text(a.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!unlocked) {
                    TileTrack(a.progress.toFloat() / a.target.coerceAtLeast(1), Modifier.padding(top = 8.dp), height = 6.dp)
                    Text("${a.progress} of ${a.target}", style = MaterialTheme.typography.labelSmall, color = t.ink2, modifier = Modifier.padding(top = 2.dp))
                }
            }
            Spacer(Modifier.width(8.dp))
            RarityPill(a.rarity)
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
                LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        val t = LocalTokens.current
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cardBrush(Color(0xFFFFE9D6), Color(0xFFFFF6E2))).padding(18.dp)) {
                            Illustration("trophy", Modifier.align(Alignment.CenterEnd), size = 100.dp)
                            Column(Modifier.fillMaxWidth(0.62f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("${done.size} of ${s.value.size} unlocked", style = MaterialTheme.typography.headlineSmall)
                                TileTrack(done.size.toFloat() / s.value.size.coerceAtLeast(1), fill = t.yellowDark, track = Color.White, height = 10.dp)
                            }
                        }
                    }
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
            is UiState.Data -> LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                val groups = s.value.groupBy { it.kind }
                listOf("AVATAR_FRAME" to "Avatar frames", "TITLE" to "Titles", "MAP_MARKER" to "Map markers", "BADGE" to "Badges", "LANDMARK_STAMP" to "Landmark stamps").forEach { (kind, title) ->
                    val list = groups[kind].orEmpty()
                    if (list.isEmpty()) return@forEach
                    item(key = kind) {
                        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                            TagPill("${list.count { it.acquiredAt != null }}/${list.size}", MaterialTheme.colorScheme.surfaceContainerHigh, LocalTokens.current.ink2)
                        }
                    }
                    items(list.chunked(2), key = { row -> kind + row.first().code }) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { c -> CollectibleCard(c, c.code in equipped, Modifier.weight(1f)) { vm.toggle(c) } }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/** A collectible as a card: icon tile, a lock until it is earned, its name and how to get it, and its rarity. */
@Composable
private fun CollectibleCard(c: CollectibleDto, equipped: Boolean, modifier: Modifier, onToggle: () -> Unit) {
    val owned = c.acquiredAt != null
    val equippable = owned && c.kind in setOf("AVATAR_FRAME", "TITLE", "MAP_MARKER")
    val t = LocalTokens.current
    LocanaCard(modifier, shape = RoundedCornerShape(20.dp), shadow = 2.dp, onClick = if (equippable) onToggle else null) {
        Column(Modifier.padding(14.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                IconTile(iconFor(c.iconKey), t.skySoft, Color(0xFF1E6FB0), size = 42.dp, modifier = Modifier.alpha(if (owned) 1f else 0.7f))
                Spacer(Modifier.weight(1f))
                if (!owned) Icon(Icons.Outlined.Lock, contentDescription = "Locked", tint = t.ink3, modifier = Modifier.size(16.dp))
            }
            Text(c.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold), modifier = Modifier.padding(top = 4.dp))
            Text(c.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            when {
                equipped -> TagPill("Equipped", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
                equippable -> TagPill("Equip", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary)
                else -> RarityPill(c.rarity)
            }
        }
    }
}

@Composable
fun LeaderboardScreen(nav: NavController, vm: LeaderboardViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val q by vm.query.collectAsStateWithLifecycle()
    val circles by vm.circleList.collectAsStateWithLifecycle()
    val more by vm.loadingMore.collectAsStateWithLifecycle()
    BackScaffold("Leaderboards", nav) { pad ->
        Column(Modifier.padding(pad)) {
            Segmented(
                listOf("friends" to "Friends", "circle" to "Circle", "global" to "Season"), q.scope,
                { k -> vm.set { it.copy(scope = k, period = if (k == "global") "season" else it.period) } },
                Modifier.padding(horizontal = 20.dp),
            )
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("xp" to "XP", "tiles" to "Exploration", "quests" to "Quests").forEach { (k, l) -> LocanaChip(l, q.metric == k, { vm.set { it.copy(metric = k) } }) }
                if (q.scope != "global") listOf("week" to "This week", "season" to "Season").forEach { (k, l) -> LocanaChip(l, q.period == k, { vm.set { it.copy(period = k) } }) }
            }
            if (q.scope == "circle") {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    circles?.value.orEmpty().forEach { c -> LocanaChip(c.name, q.circleId == c.id, { vm.set { it.copy(circleId = c.id) } }) }
                }
            }
            when (val s = state) {
                UiState.Loading -> LoadingState()
                is UiState.Error -> ErrorState(s.message, { vm.load() })
                is UiState.Data -> {
                    val b = s.value
                    if (b.entries.isEmpty()) {
                        EmptyState("No scores yet", "Scores appear once someone earns XP, reveals tiles or claims a quest in this period.", illustration = "trophy")
                    } else {
                        LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (b.me.optedOut) item {
                                Text(
                                    "You're hidden from other players' boards. Only you see your row. Change this in Privacy.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            item { Podium(b.entries.take(3), b.metric == "xp") }
                            items(b.entries, key = { it.userId }) { e ->
                                val t = LocalTokens.current
                                LocanaCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), shadow = if (e.isMe) 0.dp else 2.dp, color = if (e.isMe) MaterialTheme.colorScheme.primaryContainer else t.card) {
                                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("#${e.rank}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(40.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(e.name + if (e.isMe) " (you)" else "", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold))
                                            Text("Level ${e.level}", style = MaterialTheme.typography.bodySmall, color = t.ink2)
                                        }
                                        Text(if (b.metric == "xp") "${e.score} XP" else "${e.score}", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold))
                                    }
                                }
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

/** First, second and third place on steps of different height, with the winner in the middle. */
@Composable
private fun Podium(top: List<app.locana.data.api.BoardEntryDto>, xp: Boolean) {
    val t = LocalTokens.current
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cardBrush(Color(0xFFFFF1C9), Color(0xFFF1EAFF), vertical = true)).padding(top = 18.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
            listOf(1, 0, 2).forEach { i ->
                val e = top.getOrNull(i)
                val (height, color) = when (i) { 0 -> 118.dp to t.yellow; 1 -> 86.dp to Color(0xFFC9BCF0); else -> 66.dp to Color(0xFFFFB3A6) }
                Column(Modifier.weight(1f).padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (e != null) {
                        Avatar(e.name, e.avatarKey, size = 40.dp)
                        Text(e.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                        Text(if (xp) "${e.score} XP" else "${e.score}", style = MaterialTheme.typography.labelSmall, color = t.ink2, modifier = Modifier.padding(bottom = 6.dp))
                    } else {
                        Text("Invite", style = MaterialTheme.typography.labelSmall, color = t.ink3, modifier = Modifier.padding(bottom = 6.dp))
                    }
                    Box(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)).background(color), contentAlignment = Alignment.TopCenter) {
                        Text("${i + 1}", style = MaterialTheme.typography.headlineMedium, color = Color.White, modifier = Modifier.padding(top = 10.dp))
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
            is UiState.Data -> LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(s.value, key = { it.level }) { l -> LevelStep(l, current) }
            }
        }
    }
}

/** One step of the stair: the number in a chunky square, zigzagging to the right and back. */
@Composable
private fun LevelStep(l: app.locana.data.api.LevelDto, current: Int) {
    val t = LocalTokens.current
    val scheme = MaterialTheme.colorScheme
    val reward = l.rewardCollectibleCode != null
    val (face, edge, text) = when {
        l.level == current -> Triple(scheme.primary, t.primaryDark, scheme.onPrimary)
        reward -> Triple(t.yellow, t.yellowDark, Color(0xFF3A2800))
        else -> Triple(t.card, t.edge, scheme.onSurface)
    }
    val indent = listOf(0, 1, 2, 3, 2, 1)[(l.level - 1) % 6] * 34
    Row(Modifier.fillMaxWidth().padding(start = indent.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.padding(bottom = 4.dp)) {
            Box(Modifier.size(56.dp).offset(y = 4.dp).background(edge, RoundedCornerShape(18.dp)))
            Box(Modifier.size(56.dp).background(face, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                Text("${l.level}", style = MaterialTheme.typography.titleLarge, color = text)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("${l.totalXp} XP", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold), color = if (l.level == current) scheme.primary else scheme.onSurface)
            l.rewardCollectibleCode?.let { Text("\uD83C\uDF81 Unlocks ${it.substringAfter('_').replaceFirstChar { c -> c.uppercase() }}", style = MaterialTheme.typography.labelSmall, color = t.onYellowSoft) }
        }
        if (l.level == current) {
            Spacer(Modifier.width(10.dp))
            TagPill("You're here", scheme.primary, scheme.onPrimary, icon = Icons.Outlined.LocationOn)
        }
    }
}
