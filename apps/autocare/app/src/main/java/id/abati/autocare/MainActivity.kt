package id.abati.autocare

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import id.abati.autocare.reminder.Reminders
import id.abati.autocare.ui.AutoCareRoot
import id.abati.autocare.ui.theme.AutoCareTheme

class MainActivity : ComponentActivity() {
    // Layar tujuan dari notifikasi yang belum diproses UI
    private val openTarget = mutableStateOf<String?>(null)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* ditolak pun aplikasi tetap berjalan */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) openTarget.value = intent?.getStringExtra(Reminders.EXTRA_OPEN)
        if (Build.VERSION.SDK_INT >= 33 && savedInstanceState == null && !Reminders.hasPermission(this)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            AutoCareTheme {
                AutoCareRoot(app.repository, app.settings, openTarget.value, onOpenHandled = { openTarget.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openTarget.value = intent.getStringExtra(Reminders.EXTRA_OPEN)
    }
}
