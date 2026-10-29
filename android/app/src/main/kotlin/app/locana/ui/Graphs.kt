package app.locana.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import app.locana.ui.auth.AuthViewModel
import app.locana.ui.auth.ForgotPasswordScreen
import app.locana.ui.auth.LoginScreen
import app.locana.ui.auth.OnboardingScreen
import app.locana.ui.auth.RegisterScreen
import app.locana.ui.circle.ChallengeNewScreen
import app.locana.ui.circle.CircleCreateScreen
import app.locana.ui.circle.CircleJoinScreen
import app.locana.ui.circle.CircleScreen
import app.locana.ui.circle.CircleSettingsScreen
import app.locana.ui.circle.InviteScreen
import app.locana.ui.circle.MembersScreen
import app.locana.ui.circle.PlaceEditScreen
import app.locana.ui.circle.PlacesScreen
import app.locana.ui.explore.ExplorationMapScreen
import app.locana.ui.explore.ExploreScreen
import app.locana.ui.explore.LandmarksScreen
import app.locana.ui.explore.QuestHistoryScreen
import app.locana.ui.explore.QuestScreen
import app.locana.ui.explore.TerritoryScreen
import app.locana.ui.map.MapScreen
import app.locana.ui.nav.Routes
import app.locana.ui.play.AchievementsScreen
import app.locana.ui.play.CollectiblesScreen
import app.locana.ui.play.LeaderboardScreen
import app.locana.ui.play.LevelsScreen
import app.locana.ui.play.PlayScreen
import app.locana.ui.profile.AccountScreen
import app.locana.ui.profile.BlockedScreen
import app.locana.ui.profile.NotificationsScreen
import app.locana.ui.profile.PrivacyScreen
import app.locana.ui.profile.ProfileEditScreen
import app.locana.ui.profile.ProfileScreen
import app.locana.ui.profile.SharingScreen
import app.locana.ui.setup.PermissionsScreen
import app.locana.ui.setup.ProfileSetupScreen
import app.locana.ui.setup.SetupViewModel

@Composable
fun AuthGraph(nav: NavHostController, onboardingSeen: Boolean, expiredReason: String?) {
    // One AuthViewModel for the whole sign-in flow, so typed values survive moving between screens.
    val vm: AuthViewModel = hiltViewModel()
    // Fixed for this visit: marking onboarding as seen must not swap the graph under the user.
    val start = remember { if (onboardingSeen) Routes.LOGIN else Routes.ONBOARDING }
    NavHost(nav, startDestination = start) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onCreate = { vm.markOnboardingSeen(); nav.navigate(Routes.REGISTER) { popUpTo(Routes.ONBOARDING) { inclusive = true } } },
                onSignIn = { vm.markOnboardingSeen(); nav.navigate(Routes.LOGIN) { popUpTo(Routes.ONBOARDING) { inclusive = true } } },
            )
        }
        composable(Routes.LOGIN) { LoginScreen(vm, expiredReason, onRegister = { nav.navigate(Routes.REGISTER) }, onForgot = { nav.navigate(Routes.FORGOT) }) }
        composable(Routes.REGISTER) { RegisterScreen(vm, onLogin = { nav.navigate(Routes.LOGIN) { popUpTo(Routes.LOGIN) { inclusive = true } } }) }
        composable(Routes.FORGOT) { ForgotPasswordScreen(vm, onDone = { nav.popBackStack(Routes.LOGIN, false) }, onBack = { nav.popBackStack() }) }
    }
}

@Composable
fun SetupGraph(nav: NavHostController) {
    val vm: SetupViewModel = hiltViewModel()
    NavHost(nav, startDestination = Routes.PROFILE_SETUP) {
        composable(Routes.PROFILE_SETUP) { ProfileSetupScreen(vm, onNext = { nav.navigate(Routes.PERMISSIONS) }) }
        composable(Routes.PERMISSIONS) { PermissionsScreen(vm, onDone = {}) }
    }
}

@Composable
fun MainGraph(nav: NavHostController, @Suppress("UNUSED_PARAMETER") snackbar: SnackbarHostState, modifier: Modifier) {
    val id = listOf(navArgument("id") { type = NavType.StringType })
    NavHost(nav, startDestination = Routes.MAP, modifier = modifier, enterTransition = { enterFade }, exitTransition = { exitFade }) {
        composable(Routes.MAP) { MapScreen(nav) }
        composable(Routes.EXPLORE) { ExploreScreen(nav) }
        composable(Routes.PLAY) { PlayScreen(nav) }
        composable(Routes.CIRCLE) { CircleScreen(nav) }
        composable(Routes.PROFILE) { ProfileScreen(nav) }

        composable(Routes.CIRCLE_CREATE) { CircleCreateScreen(nav) }
        composable(Routes.CIRCLE_JOIN, arguments = listOf(navArgument("code") { type = NavType.StringType; defaultValue = "" })) { CircleJoinScreen(nav) }
        composable(Routes.CIRCLE_INVITE, arguments = id) { InviteScreen(nav) }
        composable(Routes.CIRCLE_MEMBERS, arguments = id) { MembersScreen(nav) }
        composable(Routes.CIRCLE_SETTINGS, arguments = id) { CircleSettingsScreen(nav) }
        composable(Routes.PLACES, arguments = id) { PlacesScreen(nav) }
        composable(Routes.PLACE_EDIT, arguments = id + navArgument("placeId") { type = NavType.StringType; defaultValue = "" }) { PlaceEditScreen(nav) }
        composable(Routes.CHALLENGE_NEW, arguments = id) { ChallengeNewScreen(nav) }

        composable(Routes.QUEST, arguments = id) { QuestScreen(nav) }
        composable(Routes.QUEST_HISTORY) { QuestHistoryScreen(nav) }
        composable(Routes.EXPLORATION_MAP) { ExplorationMapScreen(nav) }
        composable(Routes.TERRITORY, arguments = id) { TerritoryScreen(nav) }
        composable(Routes.LANDMARKS) { LandmarksScreen(nav) }

        composable(Routes.ACHIEVEMENTS) { AchievementsScreen(nav) }
        composable(Routes.COLLECTIBLES) { CollectiblesScreen(nav) }
        composable(Routes.LEADERBOARD, arguments = listOf(navArgument("scope") { type = NavType.StringType; defaultValue = "friends" })) { LeaderboardScreen(nav) }
        composable(Routes.LEVELS) { LevelsScreen(nav) }

        composable(Routes.NOTIFICATIONS) { NotificationsScreen(nav) }
        composable(Routes.PROFILE_EDIT) { ProfileEditScreen(nav) }
        composable(Routes.PRIVACY) { PrivacyScreen(nav) }
        composable(Routes.SHARING) { SharingScreen(nav) }
        composable(Routes.ACCOUNT) { AccountScreen(nav) }
        composable(Routes.BLOCKED) { BlockedScreen(nav) }
        // Reached from Map (sharing without permission) and Profile; does not end onboarding again.
        composable(Routes.PERMISSIONS) { PermissionsScreen(hiltViewModel(), onDone = { nav.popBackStack() }, finishOnboarding = false) }
    }
}
