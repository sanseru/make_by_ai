package id.abati.perjadin.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import id.abati.perjadin.data.Repository
import id.abati.perjadin.ui.screens.FaqScreen
import id.abati.perjadin.ui.screens.HomeScreen
import id.abati.perjadin.ui.screens.OnboardingScreen
import id.abati.perjadin.ui.screens.ReportScreen
import id.abati.perjadin.ui.screens.SettingsScreen
import id.abati.perjadin.ui.screens.TripDetailScreen
import id.abati.perjadin.ui.screens.TripEditScreen

sealed interface Screen {
    data class Onboarding(val firstRun: Boolean) : Screen
    data object Home : Screen
    data object Faq : Screen
    data object Settings : Screen
    data class TripEdit(val tripId: String?) : Screen
    data class TripDetail(val tripId: String) : Screen
    data class Report(val tripId: String) : Screen
}

/** Aksi yang memicu aplikasi dari luar (widget layar utama / shortcut ikon). */
sealed interface LaunchAction {
    data class QuickExpense(val tripId: String?) : LaunchAction
    data class OpenTrip(val tripId: String) : LaunchAction
    data object NewTrip : LaunchAction
}

/** Permintaan membuka form catat cepat di Beranda; [nonce] agar permintaan berulang tetap terproses. */
data class QuickRequest(val tripId: String?, val nonce: Long = System.nanoTime())

/** Back stack sederhana berbasis state Compose (tanpa library navigasi). */
class Navigator(start: Screen) {
    private val stack = mutableStateListOf(start)
    val current: Screen get() = stack.last()
    val canPop: Boolean get() = stack.size > 1

    fun push(screen: Screen) { stack.add(screen) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun replace(screen: Screen) { stack[stack.lastIndex] = screen }
    fun resetTo(screen: Screen) {
        stack.clear()
        stack.add(screen)
    }
}

@Composable
fun PerjadinApp(repo: Repository, launchAction: LaunchAction? = null, onLaunchHandled: () -> Unit = {}) {
    val data by repo.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    // Bersihkan foto struk yatim (mis. foto diambil lalu batal disimpan) sekali saat app dibuka
    androidx.compose.runtime.LaunchedEffect(Unit) {
        repo.cleanupReceipts(id.abati.perjadin.util.ReceiptStore.dir(context))
        // Pastikan jadwal pengingat tetap ada (mis. setelah update aplikasi)
        val d = repo.state.value
        id.abati.perjadin.reminder.Reminders.schedule(context, d.reminderEnabled, d.reminderHour, replace = false)
        // Perjalanan "aktif" bisa berganti karena tanggal berganti
        id.abati.perjadin.widget.TripWidget.updateAll(context)
    }
    val nav = remember {
        Navigator(if (repo.state.value.onboardingDone) Screen.Home else Screen.Onboarding(firstRun = true))
    }
    BackHandler(enabled = nav.canPop) { nav.pop() }

    var quickRequest by remember { androidx.compose.runtime.mutableStateOf<QuickRequest?>(null) }
    androidx.compose.runtime.LaunchedEffect(launchAction) {
        val action = launchAction ?: return@LaunchedEffect
        // Abaikan aksi luar sebelum onboarding selesai
        if (repo.state.value.onboardingDone) {
            when (action) {
                is LaunchAction.QuickExpense -> {
                    nav.resetTo(Screen.Home)
                    quickRequest = QuickRequest(action.tripId)
                }
                is LaunchAction.OpenTrip -> {
                    nav.resetTo(Screen.Home)
                    if (repo.state.value.trip(action.tripId) != null) nav.push(Screen.TripDetail(action.tripId))
                }
                LaunchAction.NewTrip -> {
                    nav.resetTo(Screen.Home)
                    nav.push(Screen.TripEdit(null))
                }
            }
        }
        onLaunchHandled()
    }

    // Surface menetapkan LocalContentColor = onBackground, sehingga teks tanpa warna
    // eksplisit tetap terbaca di mode gelap.
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    // Pemberitahuan masalah data saat dibuka (file rusak / versi lebih baru)
    val notice by repo.notice.collectAsState()
    notice?.let { msg ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = repo::dismissNotice,
            title = { androidx.compose.material3.Text("Perhatian: data aplikasi") },
            text = { androidx.compose.material3.Text(msg) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = repo::dismissNotice) { androidx.compose.material3.Text("Mengerti") } },
        )
    }

    AnimatedContent(
        targetState = nav.current,
        transitionSpec = {
            (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 10 }) togetherWith fadeOut(tween(150))
        },
        label = "nav",
    ) { screen ->
        when (screen) {
            is Screen.Onboarding -> OnboardingScreen(
                firstRun = screen.firstRun,
                onFinish = { loadDemo ->
                    if (screen.firstRun) {
                        repo.setOnboardingDone()
                        if (loadDemo) repo.loadDemo()
                        nav.resetTo(Screen.Home)
                    } else {
                        nav.pop()
                    }
                },
            )

            Screen.Home -> HomeScreen(
                data = data,
                onOpenTrip = { nav.push(Screen.TripDetail(it)) },
                onNewTrip = { nav.push(Screen.TripEdit(null)) },
                onFaq = { nav.push(Screen.Faq) },
                onSettings = { nav.push(Screen.Settings) },
                onTutorial = { nav.push(Screen.Onboarding(firstRun = false)) },
                onLoadDemo = repo::loadDemo,
                onSaveExpense = repo::upsertExpense,
                quickRequest = quickRequest,
                onQuickHandled = { quickRequest = null },
                onCreateCategory = repo::upsertCategory,
            )

            Screen.Faq -> FaqScreen(
                onBack = nav::pop,
                onTutorial = { nav.push(Screen.Onboarding(firstRun = false)) },
            )

            Screen.Settings -> SettingsScreen(
                data = data,
                repo = repo,
                onBack = nav::pop,
                onTutorial = { nav.push(Screen.Onboarding(firstRun = false)) },
            )

            is Screen.TripEdit -> TripEditScreen(
                trip = screen.tripId?.let { data.trip(it) },
                profile = data.profile,
                existingOdr = data.trips.filter { it.id != screen.tripId }.map { it.odrNumber.trim().lowercase() }.toSet(),
                onBack = nav::pop,
                onSave = { trip ->
                    repo.upsertTrip(trip)
                    if (screen.tripId == null) nav.replace(Screen.TripDetail(trip.id)) else nav.pop()
                },
            )

            is Screen.TripDetail -> {
                val trip = data.trip(screen.tripId)
                if (trip == null) {
                    Box(Modifier.fillMaxSize())
                } else {
                    TripDetailScreen(
                        trip = trip,
                        categories = data.categories,
                        repo = repo,
                        onBack = nav::pop,
                        onEdit = { nav.push(Screen.TripEdit(trip.id)) },
                        history = data.expenseHistory,
                        existingOdr = data.trips.map { it.odrNumber.trim().lowercase() }.toSet(),
                        onDuplicated = { newId -> nav.replace(Screen.TripDetail(newId)) },
                        onReport = { nav.push(Screen.Report(trip.id)) },
                        onDeleted = nav::pop,
                    )
                }
            }

            is Screen.Report -> {
                val trip = data.trip(screen.tripId)
                if (trip == null) {
                    Box(Modifier.fillMaxSize())
                } else {
                    ReportScreen(
                        trip = trip,
                        profile = data.profile,
                        categories = data.categories,
                        onBack = nav::pop,
                        onMarkReported = { repo.setStatus(trip.id, it) },
                        onOpenSettings = { nav.push(Screen.Settings) },
                    )
                }
            }
        }
    }
    }
}
