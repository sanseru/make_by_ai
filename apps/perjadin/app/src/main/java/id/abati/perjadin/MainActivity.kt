package id.abati.perjadin

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import id.abati.perjadin.data.Repository
import id.abati.perjadin.ui.LaunchAction
import id.abati.perjadin.ui.PerjadinApp
import id.abati.perjadin.ui.theme.PerjadinTheme
import id.abati.perjadin.widget.TripWidget

class MainActivity : ComponentActivity() {
    // Aksi dari widget / shortcut yang belum diproses UI
    private val launchAction = mutableStateOf<LaunchAction?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Saat activity dibuat ulang (mis. rotasi), jangan ulangi aksi lama
        if (savedInstanceState == null) launchAction.value = parse(intent)
        val repo = Repository.get(applicationContext)
        setContent {
            PerjadinTheme {
                PerjadinApp(repo, launchAction.value, onLaunchHandled = { launchAction.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchAction.value = parse(intent)
    }

    private fun parse(intent: Intent?): LaunchAction? {
        val tripId = intent?.getStringExtra(TripWidget.EXTRA_TRIP_ID)
        return when (intent?.action) {
            TripWidget.ACTION_QUICK_EXPENSE -> LaunchAction.QuickExpense(tripId)
            TripWidget.ACTION_OPEN_TRIP -> tripId?.let { LaunchAction.OpenTrip(it) }
            TripWidget.ACTION_NEW_TRIP -> LaunchAction.NewTrip
            else -> null
        }
    }
}
