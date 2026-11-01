package app.locana.ui.circle

import android.content.Intent
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.locana.BuildConfig
import app.locana.data.api.FeedItemDto
import app.locana.data.api.MemberDto
import app.locana.domain.Freshness
import app.locana.ui.auth.Field
import app.locana.ui.auth.FormError
import app.locana.ui.components.Avatar
import app.locana.ui.components.AvatarStack
import app.locana.ui.components.HeroPanel
import app.locana.ui.components.HeroPrimaryButton
import app.locana.ui.components.HeroSecondaryButton
import app.locana.ui.theme.LocalHero
import app.locana.ui.components.CachedNotice
import app.locana.ui.components.EmptyState
import app.locana.ui.components.ErrorState
import app.locana.ui.components.LoadingState
import app.locana.ui.components.PrimaryButton
import app.locana.ui.components.QuestProgressTrack
import app.locana.ui.components.SecondaryButton
import app.locana.ui.components.SectionHeader
import app.locana.ui.components.StatusChip
import app.locana.ui.components.UiState
import app.locana.ui.explore.BackScaffold
import app.locana.ui.explore.endsIn
import app.locana.ui.nav.Routes
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant

private val reactionGlyph = linkedMapOf("clap" to "👏", "fire" to "🔥", "heart" to "❤️", "wow" to "😮", "laugh" to "😄")

private fun FeedItemDto.p(k: String) = payload[k]?.jsonPrimitive?.contentOrNull

fun feedText(i: FeedItemDto): String {
    val who = i.actor.displayName
    return when (i.kind) {
        "ACHIEVEMENT" -> "$who unlocked ${i.p("name") ?: "an achievement"}"
        "LEVEL_UP" -> "$who reached level ${i.p("level")}"
        "JOINED" -> "$who joined the circle"
        "LEFT" -> "$who left the circle"
        "TERRITORY" -> "$who took ${i.p("name")} for the team"
        "CHALLENGE" -> if (i.p("state") == "COMPLETED") "Challenge complete: ${i.p("title")}" else "$who started a challenge: ${i.p("title")}"
        "ARRIVE" -> "$who arrived at ${i.p("placeName")}"
        "LEAVE" -> "$who left ${i.p("placeName")}"
        else -> "$who did something new"
    }
}

@Composable
fun CircleScreen(nav: NavController, vm: CircleViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val list by vm.list.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    var switcher by remember { mutableStateOf(false) }

    if (list != null && list!!.value.isEmpty()) {
        Column(Modifier.fillMaxSize().statusBarsPadding(), verticalArrangement = Arrangement.Center) {
            EmptyState("No circles yet", "Create a circle for the people you want to share with, or join one with a code.", actionLabel = "Create a circle", onAction = { nav.navigate(Routes.CIRCLE_CREATE) })
            TextButton(onClick = { nav.navigate(Routes.circleJoin()) }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Join with a code") }
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(16.dp)) {
        item {
            // Focal point: who is in this circle and who is sharing with you right now.
            val hero = LocalHero.current
            val detail = (state as? UiState.Data)?.value?.detail
            HeroPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        Row(Modifier.clickable(role = Role.Button, onClickLabel = "Switch circle") { switcher = true }, verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                list?.value?.firstOrNull { it.id == selected }?.name ?: "Circle",
                                style = MaterialTheme.typography.headlineLarge,
                                color = hero.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = hero.text)
                        }
                        DropdownMenu(expanded = switcher, onDismissRequest = { switcher = false }) {
                            list?.value.orEmpty().forEach { c -> DropdownMenuItem(text = { Text(c.name) }, onClick = { switcher = false; vm.select(c.id) }) }
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("New circle") }, onClick = { switcher = false; nav.navigate(Routes.CIRCLE_CREATE) })
                            DropdownMenuItem(text = { Text("Join with a code") }, onClick = { switcher = false; nav.navigate(Routes.circleJoin()) })
                        }
                    }
                    selected?.let { id -> IconButton(onClick = { nav.navigate(Routes.circleSettings(id)) }) { Icon(Icons.Outlined.Settings, contentDescription = "Circle settings", tint = hero.text) } }
                }
                if (detail != null) {
                    val sharing = detail.members.count { it.sharingWithMe == true }
                    val count = detail.members.size
                    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        AvatarStack(detail.members.map { it.displayName to it.avatarKey })
                        Spacer(Modifier.width(12.dp))
                        Text(
                            (if (count == 1) "1 member" else "$count members") + " · " +
                                (if (sharing == 0) "nobody sharing with you" else "$sharing sharing with you"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = hero.muted,
                        )
                    }
                }
                selected?.let { id ->
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HeroPrimaryButton("Invite", { nav.navigate(Routes.circleInvite(id)) })
                        HeroSecondaryButton("Members", { nav.navigate(Routes.circleMembers(id)) })
                        HeroSecondaryButton("Places", { nav.navigate(Routes.places(id)) })
                    }
                }
            }
        }
        when (val s = state) {
            UiState.Loading -> item { LoadingState() }
            is UiState.Error -> item { ErrorState(s.message, { vm.load() }) }
            is UiState.Data -> {
                val h = s.value
                s.cachedAtMs?.let { item { CachedNotice(it, { vm.load() }, Modifier.padding(top = 12.dp)) } }
                item {
                    SectionHeader("Who's here")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        h.detail.members.forEach { m ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
                                Avatar(m.displayName, m.avatarKey, size = 48.dp, framed = m.avatarFrameKey != null)
                                Text(if (m.userId == vm.myId) "You" else m.displayName, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    when (m.sharingWithMe) { true -> "sharing"; false -> "not sharing"; null -> "you" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                item { SectionHeader("Challenges", actionLabel = "New", onAction = { nav.navigate(Routes.challengeNew(h.detail.id)) }) }
                if (h.challenges.isEmpty()) {
                    item { Text("Set a shared goal, like 30 new tiles this week. Everyone's validated activity counts.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(h.challenges.take(3), key = { it.id }) { c ->
                    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Row { Text(c.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f)); StatusChip(if (c.status == "ACTIVE") endsIn(c.endsAt) else c.status.lowercase()) }
                            QuestProgressTrack(c.progress, c.target, c.metric.lowercase(), Modifier.padding(top = 8.dp))
                            Text("${c.progress} of ${c.target} ${c.metric.lowercase()}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
                h.exploration?.let { e ->
                    item {
                        SectionHeader("Exploring together")
                        Text("${e.totalTiles} tiles revealed by members who share their stats", style = MaterialTheme.typography.bodyMedium)
                        if (e.hiddenMembers > 0) Text("${e.hiddenMembers} member(s) keep their stats private.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { nav.navigate(Routes.leaderboard("circle")) }) { Text("Circle leaderboard") }
                    }
                }
                item { SectionHeader("Activity") }
                if (h.feed.isEmpty()) item { Text("Arrivals, level ups and achievements from your circle appear here.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(h.feed, key = { it.id }) { f -> FeedRow(f) { emoji -> vm.react(f, emoji) } }
                if (h.nextCursor != null) item { LaunchedEffect(h.feed.size) { vm.moreFeed() } }
            }
        }
    }
}

@Composable
private fun FeedRow(f: FeedItemDto, onReact: (String) -> Unit) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(f.actor.displayName, f.actor.avatarKey, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(feedText(f), style = MaterialTheme.typography.bodyMedium)
                Text(Freshness.ageLabel(Instant.parse(f.createdAt).toEpochMilli(), System.currentTimeMillis()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.padding(start = 40.dp)) {
            reactionGlyph.forEach { (key, glyph) ->
                val count = f.reactions.firstOrNull { it.emoji == key }?.count ?: 0
                val mine = f.myReaction == key
                TextButton(onClick = { onReact(key) }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                    Text(if (count > 0) "$glyph $count" else glyph, style = if (mine) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium)
                }
            }
        }
        HorizontalDivider(Modifier.padding(top = 4.dp))
    }
}

@Composable
fun CircleCreateScreen(nav: NavController, vm: CircleFormViewModel = hiltViewModel()) {
    val name by vm.name.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    BackScaffold("New circle", nav) { pad ->
        Column(Modifier.padding(pad).padding(16.dp)) {
            Text("Name it after the people in it: \"Family\", \"Ana & Budi\", \"Saturday run club\".", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Field(name, { vm.name.value = it }, "Circle name", capitalize = true)
            FormError(error)
            Spacer(Modifier.height(20.dp))
            PrimaryButton("Create circle", { vm.create { id -> nav.popBackStack(); nav.navigate(Routes.circleInvite(id)) } }, Modifier.fillMaxWidth(), loading = busy)
            Text("You'll be the owner. Nobody sees your location until you start sharing.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
fun CircleJoinScreen(nav: NavController, vm: CircleFormViewModel = hiltViewModel()) {
    val code by vm.code.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    BackScaffold("Join a circle", nav) { pad ->
        Column(Modifier.padding(pad).padding(16.dp)) {
            Text("Enter the code someone shared with you.", style = MaterialTheme.typography.bodyMedium)
            Field(code, { vm.code.value = it.uppercase() }, "Invitation code")
            FormError(error)
            Spacer(Modifier.height(20.dp))
            PrimaryButton("Join circle", { vm.join { nav.navigate(Routes.CIRCLE) { popUpTo(Routes.MAP) } } }, Modifier.fillMaxWidth(), loading = busy)
            Text(
                "Joining does not share your location. Members only see you after you start sharing with this circle.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
fun InviteScreen(nav: NavController, vm: InviteViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val created by vm.created.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var hours by remember { mutableStateOf(48) }
    var uses by remember { mutableStateOf(5) }
    BackScaffold("Invite people", nav) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp)) {
            item {
                created?.code?.let { code ->
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("New code", style = MaterialTheme.typography.labelLarge)
                            SelectionContainer { Text(code, style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Monospace)) }
                            Text("Works ${endsIn(created!!.expiresAt)}, for up to ${created!!.maxUses} people. You won't see this code again after leaving.", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(12.dp))
                            PrimaryButton("Share code", {
                                val text = "Join my circle on Locana with code $code\n${created!!.deepLink}"
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share invitation"))
                            })
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
                Text("Make a code", style = MaterialTheme.typography.titleLarge)
                Text("Valid for", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2 to "2 hours", 48 to "2 days", 168 to "7 days").forEach { (h, l) -> FilterChip(selected = hours == h, onClick = { hours = h }, label = { Text(l) }) }
                }
                Text("People who can use it", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 5, 20).forEach { u -> FilterChip(selected = uses == u, onClick = { uses = u }, label = { Text("$u") }) }
                }
                FormError(error)
                Spacer(Modifier.height(12.dp))
                PrimaryButton("Create code", { vm.create(hours, uses) }, Modifier.fillMaxWidth())
                SectionHeader("Codes")
            }
            when (val s = state) {
                UiState.Loading -> item { LoadingState() }
                is UiState.Error -> item { ErrorState(s.message, { vm.load() }) }
                is UiState.Data -> {
                    if (s.value.isEmpty()) item { Text("No codes yet.", style = MaterialTheme.typography.bodyMedium) }
                    items(s.value, key = { it.id }) { inv ->
                        ListItem(
                            headlineContent = { Text("Code ending …${inv.codeHint}") },
                            supportingContent = { Text("${inv.uses}/${inv.maxUses} used · ${if (inv.status == "ACTIVE") endsIn(inv.expiresAt) else inv.status.lowercase().replace('_', ' ')}") },
                            trailingContent = { if (inv.status == "ACTIVE") TextButton(onClick = { vm.revoke(inv.id) }) { Text("Revoke") } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MembersScreen(nav: NavController, vm: MembersViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var reporting by remember { mutableStateOf<MemberDto?>(null) }
    var removing by remember { mutableStateOf<MemberDto?>(null) }
    BackScaffold("Members", nav) { pad ->
        when (val s = state) {
            UiState.Loading -> LoadingState(Modifier.padding(pad))
            is UiState.Error -> ErrorState(s.message, { vm.load() }, Modifier.padding(pad))
            is UiState.Data -> {
                val d = s.value
                LazyColumn(Modifier.padding(pad)) {
                    message?.let { item { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp)) } }
                    items(d.members, key = { it.userId }) { m ->
                        var menu by remember { mutableStateOf(false) }
                        val isMe = m.userId == vm.myId
                        ListItem(
                            leadingContent = { Avatar(m.displayName, m.avatarKey, size = 40.dp) },
                            headlineContent = { Text(m.displayName + if (isMe) " (you)" else "") },
                            supportingContent = {
                                Text(
                                    listOfNotNull(
                                        "@${m.username}",
                                        m.role.lowercase(),
                                        "can't see you".takeIf { m.userId in d.hiddenFromViewerIds },
                                        "blocked".takeIf { m.blockedByMe },
                                    ).joinToString(" · "),
                                )
                            },
                            trailingContent = {
                                if (!isMe) Box {
                                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Actions for ${m.displayName}") }
                                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                        val hidden = m.userId in d.hiddenFromViewerIds
                                        DropdownMenuItem(text = { Text(if (hidden) "Let them see my location" else "Hide my location from them") }, onClick = { menu = false; vm.hide(m.userId, !hidden) })
                                        if (d.myRole == "OWNER" && m.role != "OWNER") {
                                            DropdownMenuItem(text = { Text(if (m.role == "ADMIN") "Make member" else "Make admin") }, onClick = { menu = false; vm.setRole(m.userId, if (m.role == "ADMIN") "MEMBER" else "ADMIN") })
                                            DropdownMenuItem(text = { Text("Transfer ownership") }, onClick = { menu = false; vm.setRole(m.userId, "OWNER") })
                                        }
                                        if ((d.myRole == "OWNER" && m.role != "OWNER") || (d.myRole == "ADMIN" && m.role == "MEMBER")) {
                                            DropdownMenuItem(text = { Text("Remove from circle") }, onClick = { menu = false; removing = m })
                                        }
                                        DropdownMenuItem(text = { Text(if (m.blockedByMe) "Unblock" else "Block") }, onClick = { menu = false; vm.block(m.userId, !m.blockedByMe) })
                                        DropdownMenuItem(text = { Text("Report") }, onClick = { menu = false; reporting = m })
                                    }
                                }
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
    removing?.let { m ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove ${m.displayName}?") },
            text = { Text("They lose access to this circle's map, places and feed right away. Their sharing with this circle ends.") },
            confirmButton = { TextButton(onClick = { vm.remove(m.userId); removing = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }
    reporting?.let { m -> ReportDialog(m.displayName, onDismiss = { reporting = null }) { reason, details -> vm.report(m.userId, reason, details); reporting = null } }
}

@Composable
private fun ReportDialog(name: String, onDismiss: () -> Unit, onSend: (String, String?) -> Unit) {
    var reason by remember { mutableStateOf("UNWANTED_TRACKING") }
    var details by remember { mutableStateOf("") }
    val reasons = listOf("UNWANTED_TRACKING" to "Tracking me without consent", "HARASSMENT" to "Harassment", "IMPERSONATION" to "Pretending to be someone", "SPAM" to "Spam", "OTHER" to "Something else")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report $name") },
        text = {
            Column {
                reasons.forEach { (k, l) ->
                    Row(Modifier.fillMaxWidth().clickable(role = Role.RadioButton) { reason = k }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = reason == k, onClick = { reason = k })
                        Text(l)
                    }
                }
                OutlinedTextField(details, { details = it.take(1000) }, label = { Text("Details (optional)") }, modifier = Modifier.fillMaxWidth())
                Text("If you feel unsafe, you can also block them and leave the circle.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(onClick = { onSend(reason, details) }) { Text("Send report") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun CircleSettingsScreen(nav: NavController, vm: CircleSettingsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val name by vm.name.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<String?>(null) }
    BackScaffold("Circle settings", nav) { pad ->
        when (val s = state) {
            UiState.Loading -> LoadingState(Modifier.padding(pad))
            is UiState.Error -> ErrorState(s.message, null, Modifier.padding(pad))
            is UiState.Data -> Column(Modifier.padding(pad).padding(16.dp)) {
                val d = s.value
                if (d.myRole != "MEMBER") {
                    Field(name, { vm.name.value = it }, "Circle name", capitalize = true)
                    SecondaryButton("Save name", { vm.rename() }, Modifier.padding(top = 8.dp))
                }
                ListItem(
                    headlineContent = { Text("Mute this circle") },
                    supportingContent = { Text("No notifications from it. You still see the map and feed.") },
                    trailingContent = { Switch(checked = d.muted, onCheckedChange = vm::mute) },
                    modifier = Modifier.clickable(role = Role.Switch) { vm.mute(!d.muted) },
                )
                FormError(error)
                Spacer(Modifier.height(24.dp))
                SecondaryButton("Leave circle", { confirm = "leave" }, Modifier.fillMaxWidth())
                if (d.myRole == "OWNER") {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { confirm = "delete" }) { Text("Delete circle for everyone", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
    confirm?.let { what ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (what == "leave") "Leave this circle?" else "Delete this circle?") },
            text = {
                Text(
                    if (what == "leave") "Your sharing with it ends now. If you're the owner, the longest-standing admin or member becomes owner."
                    else "Everyone loses the circle, its places, feed and challenges. This can't be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    val back = { nav.navigate(Routes.CIRCLE) { popUpTo(Routes.MAP) } }
                    if (what == "leave") vm.leave(back) else vm.delete(back)
                }) { Text(if (what == "leave") "Leave" else "Delete") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

@Composable
fun PlacesScreen(nav: NavController, vm: PlacesViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.load() }
    BackScaffold("Saved places", nav) { pad ->
        Column(Modifier.padding(pad)) {
            when (val s = state) {
                UiState.Loading -> LoadingState()
                is UiState.Error -> ErrorState(s.message, { vm.load() })
                is UiState.Data -> if (s.value.isEmpty()) {
                    EmptyState(
                        "No saved places",
                        "Save home, school or work to get an alert when someone who shares with you arrives or leaves.",
                        actionLabel = "Add a place",
                        onAction = { nav.navigate(Routes.placeEdit(vm.circleId)) },
                    )
                } else {
                    LazyColumn {
                        items(s.value, key = { it.id }) { p ->
                            ListItem(
                                headlineContent = { Text(p.name) },
                                supportingContent = { Text("${p.type.lowercase().replaceFirstChar { it.uppercase() }} · ${p.radiusM} m · " + listOfNotNull("arrivals".takeIf { p.notifyArrive }, "departures".takeIf { p.notifyLeave }).joinToString(" and ").ifEmpty { "no alerts" }) },
                                modifier = Modifier.clickable(role = Role.Button) { nav.navigate(Routes.placeEdit(vm.circleId, p.id)) },
                            )
                            HorizontalDivider()
                        }
                        item { PrimaryButton("Add a place", { nav.navigate(Routes.placeEdit(vm.circleId)) }, Modifier.padding(16.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaceEditScreen(nav: NavController, vm: PlaceEditViewModel = hiltViewModel()) {
    val f by vm.form.collectAsStateWithLifecycle()
    val camera = rememberCameraPositionState()
    val primary = MaterialTheme.colorScheme.primary
    LaunchedEffect(f.lat, f.lon) {
        val lat = f.lat ?: return@LaunchedEffect
        val target = LatLng(lat, f.lon!!)
        if (camera.position.target != target) camera.position = CameraPosition.fromLatLngZoom(target, 16f)
    }
    // The map center is the place: moving the map moves the pin.
    LaunchedEffect(camera.isMoving) {
        if (!camera.isMoving && camera.position.zoom > 3f) vm.edit { it.copy(lat = camera.position.target.latitude, lon = camera.position.target.longitude) }
    }
    BackScaffold(if (vm.placeId == null) "New place" else "Edit place", nav) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item {
                Box(Modifier.fillMaxWidth().height(260.dp)) {
                    if (BuildConfig.HAS_MAPS_KEY) {
                        GoogleMap(cameraPositionState = camera, uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false)) {
                            if (f.lat != null) Circle(center = LatLng(f.lat!!, f.lon!!), radius = f.radiusM.toDouble(), strokeColor = primary, fillColor = primary.copy(alpha = 0.15f), strokeWidth = 3f)
                        }
                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.align(Alignment.TopCenter).padding(8.dp)) {
                            Text("Move the map to place the circle", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                        Box(Modifier.align(Alignment.Center).size(10.dp).padding(0.dp)) { Surface(shape = MaterialTheme.shapes.extraSmall, color = primary, modifier = Modifier.fillMaxSize()) {} }
                    } else {
                        EmptyState("Map unavailable", "Without a Maps key the place uses your current location.")
                    }
                }
                Column(Modifier.padding(16.dp)) {
                    TextButton(onClick = vm::useMyLocation) { Text("Use my current location") }
                    Field(f.name, { v -> vm.edit { it.copy(name = v) } }, "Name", capitalize = true)
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("HOME" to "Home", "WORK" to "Work", "SCHOOL" to "School", "CUSTOM" to "Other").forEach { (k, l) ->
                            FilterChip(selected = f.type == k, onClick = { vm.edit { it.copy(type = k) } }, label = { Text(l) })
                        }
                    }
                    Text("Alert radius: ${f.radiusM} m", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
                    Slider(value = f.radiusM.toFloat(), onValueChange = { v -> vm.edit { it.copy(radiusM = (v / 25).toInt() * 25) } }, valueRange = 75f..1000f)
                    Text("Phone GPS can be off by 20-100 m, so small circles cause missed or false alerts.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ListItem(headlineContent = { Text("Alert on arrival") }, trailingContent = { Switch(f.notifyArrive, { v -> vm.edit { it.copy(notifyArrive = v) } }) })
                    ListItem(headlineContent = { Text("Alert on departure") }, trailingContent = { Switch(f.notifyLeave, { v -> vm.edit { it.copy(notifyLeave = v) } }) })
                    Text(
                        "Alerts go to circle members who can currently see the person arriving. Nobody gets alerts about someone who isn't sharing with them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FormError(f.error)
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton("Save place", { vm.save { nav.popBackStack() } }, Modifier.fillMaxWidth(), loading = f.busy)
                    if (vm.placeId != null) TextButton(onClick = { vm.delete { nav.popBackStack() } }) { Text("Delete place", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

@Composable
fun ChallengeNewScreen(nav: NavController, vm: ChallengeViewModel = hiltViewModel()) {
    val title by vm.title.collectAsStateWithLifecycle()
    val metric by vm.metric.collectAsStateWithLifecycle()
    val target by vm.target.collectAsStateWithLifecycle()
    val days by vm.days.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    BackScaffold("New challenge", nav) { pad ->
        Column(Modifier.padding(pad).padding(16.dp)) {
            Field(title, { vm.title.value = it }, "Name, e.g. \"Map our neighborhood\"", capitalize = true)
            Text("What counts", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("TILES" to "New tiles", "LANDMARKS" to "Landmarks", "QUESTS" to "Quests").forEach { (k, l) -> FilterChip(selected = metric == k, onClick = { vm.metric.value = k }, label = { Text(l) }) }
            }
            Text("Goal: $target", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
            Slider(value = target.toFloat(), onValueChange = { vm.target.value = it.toInt().coerceAtLeast(1) }, valueRange = 1f..200f)
            Text("Duration: $days days", style = MaterialTheme.typography.labelLarge)
            Slider(value = days.toFloat(), onValueChange = { vm.days.value = it.toInt().coerceIn(1, 30) }, valueRange = 1f..30f)
            Text("Pick something everyone can reach at their own pace.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FormError(error)
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Start challenge", { vm.create { nav.popBackStack() } }, Modifier.fillMaxWidth())
        }
    }
}
