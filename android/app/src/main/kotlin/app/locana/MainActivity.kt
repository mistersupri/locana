package app.locana

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import app.locana.data.auth.SessionManager
import app.locana.data.auth.SessionState
import app.locana.ui.LocanaRoot
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_ROUTE = "route"
    }

    @Inject lateinit var session: SessionManager
    @Inject lateinit var appSession: AppSession

    /** Pending deep link (invitation code or notification route), consumed by the nav graph. */
    val deepLink = MutableStateFlow<DeepLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Keep the system splash only until we know whether someone is signed in (no fake delay).
        splash.setKeepOnScreenCondition { session.state.value is SessionState.Loading }
        enableEdgeToEdge()
        lifecycleScope.launch {
            session.restore()
            appSession.start()
        }
        handle(intent)
        setContent { LocanaRoot(deepLink) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        val data = intent?.data
        if (data?.scheme == "locana" && data.host == "join") {
            data.getQueryParameter("code")?.let { deepLink.value = DeepLink.Join(it) }
        }
        intent?.getStringExtra(EXTRA_ROUTE)?.let { deepLink.value = DeepLink.Route(it) }
    }
}

sealed interface DeepLink {
    data class Join(val code: String) : DeepLink
    data class Route(val route: String) : DeepLink
}
