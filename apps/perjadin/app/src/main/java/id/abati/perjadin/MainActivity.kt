package id.abati.perjadin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import id.abati.perjadin.data.Repository
import id.abati.perjadin.ui.PerjadinApp
import id.abati.perjadin.ui.theme.PerjadinTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = Repository.get(applicationContext)
        setContent {
            PerjadinTheme {
                PerjadinApp(repo)
            }
        }
    }
}
