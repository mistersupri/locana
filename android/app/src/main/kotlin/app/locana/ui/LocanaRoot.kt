package app.locana.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.locana.DeepLink
import app.locana.data.auth.SessionState
import app.locana.ui.game.LevelUpHost
import app.locana.ui.nav.Routes
import app.locana.ui.theme.LocanaTheme
import kotlinx.coroutines.flow.MutableStateFlow

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab(Routes.MAP, "Map", Icons.Outlined.Map, Icons.Filled.Map),
    Tab(Routes.EXPLORE, "Explore", Icons.Outlined.TravelExplore, Icons.Filled.TravelExplore),
    Tab(Routes.PLAY, "Play", Icons.Outlined.EmojiEvents, Icons.Filled.EmojiEvents),
    Tab(Routes.CIRCLE, "Circle", Icons.Outlined.Groups, Icons.Filled.Groups),
)

@Composable
fun LocanaRoot(deepLink: MutableStateFlow<DeepLink?>, vm: RootViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    androidx.compose.runtime.CompositionLocalProvider(app.locana.ui.components.LocalPhotoStore provides vm.photos) {
    LocanaTheme(state.theme) {
        val nav = rememberNavController()
        val snackbar = remember { SnackbarHostState() }
        val link by deepLink.collectAsStateWithLifecycle()

        when (state.session) {
            SessionState.Loading -> Box(Modifier.fillMaxSize())
            SessionState.SignedOut -> AuthGraph(nav, onboardingSeen = state.onboardingSeen, expiredReason = vm.expiredReason)
            is SessionState.SignedIn -> {
                if (state.needsProfileSetup) {
                    SetupGraph(nav)
                } else {
                    MainScaffold(nav, snackbar)
                    LaunchedEffect(link) {
                        when (val l = link) {
                            is DeepLink.Join -> nav.navigate(Routes.circleJoin(l.code))
                            is DeepLink.Route -> nav.navigate(l.route)
                            null -> Unit
                        }
                        if (link != null) deepLink.value = null
                    }
                    LevelUpHost()
                }
            }
        }
    }
    }
}

@Composable
private fun MainScaffold(nav: NavHostController, snackbar: SnackbarHostState) {
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = route in Routes.tabs
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showBar) {
                FloatingTabBar(route) { t ->
                    // Tab switches are frequent: no transition, restore each tab's state.
                    nav.navigate(t.route) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        },
    ) { padding ->
        MainGraph(nav, snackbar, Modifier.padding(padding))
    }
}

/** The mockup's capsule: white, floating, the active tab sits in a soft violet pill. */
@Composable
private fun FloatingTabBar(route: String?, onSelect: (Tab) -> Unit) {
    val tokens = app.locana.ui.theme.LocalTokens.current
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp).navigationBarsPadding(),
    ) {
        androidx.compose.material3.Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(34.dp),
            color = tokens.card.copy(alpha = 0.96f),
            shadowElevation = 10.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            androidx.compose.foundation.layout.Row(Modifier.padding(8.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
                tabs.forEach { t ->
                    val selected = route == t.route
                    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    val tint = if (selected) MaterialTheme.colorScheme.primary else tokens.ink3
                    androidx.compose.foundation.layout.Column(
                        Modifier
                            .weight(1f)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(26.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable(interactionSource = source, indication = null, role = androidx.compose.ui.semantics.Role.Tab, onClick = { onSelect(t) })
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(if (selected) t.selectedIcon else t.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                        Text(t.label, style = MaterialTheme.typography.labelSmall, color = tint, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
    }
}

/** Screens fade quickly on push; tabs and back do not animate beyond this. */
internal val enterFade: EnterTransition = fadeIn(app.locana.ui.theme.Motion.enter(160))
internal val exitFade: ExitTransition = fadeOut(app.locana.ui.theme.Motion.enter(120))
