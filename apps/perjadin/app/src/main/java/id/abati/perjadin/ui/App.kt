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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import id.abati.perjadin.data.Repository
import id.abati.perjadin.ui.screens.ExpenseEditScreen
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
    data class ExpenseEdit(val tripId: String, val expenseId: String?) : Screen
    data class Report(val tripId: String) : Screen
}

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
fun PerjadinApp(repo: Repository) {
    val data by repo.state.collectAsState()
    val nav = remember {
        Navigator(if (repo.state.value.onboardingDone) Screen.Home else Screen.Onboarding(firstRun = true))
    }
    BackHandler(enabled = nav.canPop) { nav.pop() }

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
                        repo = repo,
                        onBack = nav::pop,
                        onEdit = { nav.push(Screen.TripEdit(trip.id)) },
                        onAddExpense = { nav.push(Screen.ExpenseEdit(trip.id, null)) },
                        onEditExpense = { nav.push(Screen.ExpenseEdit(trip.id, it)) },
                        onReport = { nav.push(Screen.Report(trip.id)) },
                        onDeleted = nav::pop,
                    )
                }
            }

            is Screen.ExpenseEdit -> {
                val trip = data.trip(screen.tripId)
                if (trip == null) {
                    Box(Modifier.fillMaxSize())
                } else {
                    ExpenseEditScreen(
                        trip = trip,
                        expense = screen.expenseId?.let { id -> trip.expenses.firstOrNull { it.id == id } },
                        onBack = nav::pop,
                        onSave = { e ->
                            repo.upsertExpense(trip.id, e)
                            nav.pop()
                        },
                        onDelete = { id ->
                            repo.deleteExpense(trip.id, id)
                            nav.pop()
                        },
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
                        onBack = nav::pop,
                        onMarkReported = { repo.setStatus(trip.id, it) },
                        onOpenSettings = { nav.push(Screen.Settings) },
                    )
                }
            }
        }
    }
}
