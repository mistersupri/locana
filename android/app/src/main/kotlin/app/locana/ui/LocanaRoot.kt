package app.locana.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
    Tab(Routes.PROFILE, "Profile", Icons.Outlined.Person, Icons.Filled.Person),
)

@Composable
fun LocanaRoot(deepLink: MutableStateFlow<DeepLink?>, vm: RootViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
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

@Composable
private fun MainScaffold(nav: NavHostController, snackbar: SnackbarHostState) {
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = route in Routes.tabs
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    tabs.forEach { t ->
                        val selected = route == t.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                // Tab switches are frequent: no transition, restore each tab's state.
                                nav.navigate(t.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(if (selected) t.selectedIcon else t.icon, contentDescription = null) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        MainGraph(nav, snackbar, Modifier.padding(padding))
    }
}

/** Screens fade quickly on push; tabs and back do not animate beyond this. */
internal val enterFade: EnterTransition = fadeIn(app.locana.ui.theme.Motion.enter(160))
internal val exitFade: ExitTransition = fadeOut(app.locana.ui.theme.Motion.enter(120))
