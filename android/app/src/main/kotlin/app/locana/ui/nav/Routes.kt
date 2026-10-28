package app.locana.ui.nav

import android.net.Uri

/** Every destination in the app. Bottom bar shows only the five tabs. */
object Routes {
    const val ONBOARDING = "onboarding"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val FORGOT = "forgot"
    const val PROFILE_SETUP = "profile-setup"
    const val PERMISSIONS = "permissions"

    const val MAP = "map"
    const val EXPLORE = "explore"
    const val PLAY = "play"
    const val CIRCLE = "circle"
    const val PROFILE = "profile"

    const val CIRCLE_CREATE = "circle/create"
    const val CIRCLE_JOIN = "circle/join?code={code}"
    fun circleJoin(code: String? = null) = "circle/join?code=${Uri.encode(code ?: "")}"
    const val CIRCLE_INVITE = "circle/{id}/invite"
    fun circleInvite(id: String) = "circle/$id/invite"
    const val CIRCLE_MEMBERS = "circle/{id}/members"
    fun circleMembers(id: String) = "circle/$id/members"
    const val CIRCLE_SETTINGS = "circle/{id}/settings"
    fun circleSettings(id: String) = "circle/$id/settings"
    const val PLACES = "circle/{id}/places"
    fun places(id: String) = "circle/$id/places"
    const val PLACE_EDIT = "circle/{id}/place?placeId={placeId}"
    fun placeEdit(circleId: String, placeId: String? = null) = "circle/$circleId/place?placeId=${placeId ?: ""}"
    const val CHALLENGE_NEW = "circle/{id}/challenge"
    fun challengeNew(id: String) = "circle/$id/challenge"

    const val QUEST = "quest/{id}"
    fun quest(id: String) = "quest/$id"
    const val QUEST_HISTORY = "quests/history"
    const val EXPLORATION_MAP = "explore/map"
    const val TERRITORY = "territory/{id}"
    fun territory(id: String) = "territory/$id"
    const val LANDMARKS = "landmarks"

    const val ACHIEVEMENTS = "play/achievements"
    const val COLLECTIBLES = "play/collectibles"
    const val LEADERBOARD = "play/leaderboard?scope={scope}"
    fun leaderboard(scope: String = "friends") = "play/leaderboard?scope=$scope"
    const val LEVELS = "play/levels"

    const val NOTIFICATIONS = "notifications"
    const val PROFILE_EDIT = "profile/edit"
    const val PRIVACY = "settings/privacy"
    const val SHARING = "sharing"
    const val ACCOUNT = "settings/account"
    const val BLOCKED = "settings/blocked"

    val tabs = listOf(MAP, EXPLORE, PLAY, CIRCLE, PROFILE)
}
