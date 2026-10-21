package id.abati.autocare.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import id.abati.autocare.data.AppData
import id.abati.autocare.data.AppSettings
import id.abati.autocare.data.Repository
import id.abati.autocare.data.SettingsStore
import id.abati.autocare.ui.components.LoadingBox
import id.abati.autocare.ui.components.LocalActions
import id.abati.autocare.ui.components.UiActions
import id.abati.autocare.ui.screens.BackupScreen
import id.abati.autocare.ui.screens.CatalogScreen
import id.abati.autocare.ui.screens.ComponentDetailScreen
import id.abati.autocare.ui.screens.ComponentEditScreen
import id.abati.autocare.ui.screens.CostsScreen
import id.abati.autocare.ui.screens.ExpenseEditScreen
import id.abati.autocare.ui.screens.HistoryScreen
import id.abati.autocare.ui.screens.HomeScreen
import id.abati.autocare.ui.screens.InspectionDetailScreen
import id.abati.autocare.ui.screens.InspectionEditScreen
import id.abati.autocare.ui.screens.InstallationEditScreen
import id.abati.autocare.ui.screens.OdometerScreen
import id.abati.autocare.ui.screens.ReminderEditScreen
import id.abati.autocare.ui.screens.RemindersScreen
import id.abati.autocare.ui.screens.ReportScreen
import id.abati.autocare.ui.screens.SettingsScreen
import id.abati.autocare.ui.screens.VehicleDetailScreen
import id.abati.autocare.ui.screens.VehicleEditScreen
import id.abati.autocare.ui.screens.VehiclesScreen
import id.abati.autocare.ui.screens.VisitDetailScreen
import id.abati.autocare.ui.screens.VisitEditScreen
import id.abati.autocare.ui.screens.WorkshopsScreen

/** Navigasi antar layar. Parameter opsional dikirim sebagai query string. */
class Nav(private val nav: NavHostController) {
    fun back() {
        nav.popBackStack()
    }

    private fun go(route: String) = nav.navigate(route) { launchSingleTop = true }

    private fun q(vararg pairs: Pair<String, String?>): String =
        pairs.filter { it.second != null }.joinToString("&") { "${it.first}=${android.net.Uri.encode(it.second)}" }.let { if (it.isEmpty()) "" else "?$it" }

    fun tab(route: String) = nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    fun vehicle(id: String) = go("vehicle/$id")
    fun editVehicle(id: String? = null) = go("vehicle_edit" + q("id" to id))
    fun odometer(vehicleId: String) = go("odometer/$vehicleId")
    fun visit(id: String) = go("visit/$id")
    fun editVisit(id: String? = null, vehicleId: String? = null, reminderId: String? = null) =
        go("visit_edit" + q("id" to id, "vehicleId" to vehicleId, "reminderId" to reminderId))
    fun editReminder(id: String? = null, vehicleId: String? = null) = go("reminder_edit" + q("id" to id, "vehicleId" to vehicleId))
    fun component(id: String) = go("component/$id")
    fun editComponent(id: String? = null, vehicleId: String? = null) = go("component_edit" + q("id" to id, "vehicleId" to vehicleId))
    fun editInstallation(id: String? = null, componentId: String? = null) = go("installation_edit" + q("id" to id, "componentId" to componentId))
    fun inspection(id: String) = go("inspection/$id")
    fun editInspection(id: String? = null, vehicleId: String? = null) = go("inspection_edit" + q("id" to id, "vehicleId" to vehicleId))
    fun editExpense(id: String? = null, vehicleId: String? = null) = go("expense_edit" + q("id" to id, "vehicleId" to vehicleId))
    fun report(vehicleId: String? = null) = go("report" + q("vehicleId" to vehicleId))
    fun settings() = go("settings")
    fun backup() = go("backup")
    fun catalog() = go("catalog")
    fun workshops() = go("workshops")
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Beranda", Icons.Default.Home),
    Tab("vehicles", "Kendaraan", Icons.Default.DirectionsCar),
    Tab("reminders", "Pengingat", Icons.Default.Notifications),
    Tab("history", "Riwayat", Icons.Default.History),
    Tab("costs", "Biaya", Icons.Default.Payments),
)

@Composable
fun AutoCareRoot(repo: Repository, settingsStore: SettingsStore, openTarget: String?, onOpenHandled: () -> Unit) {
    val data by repo.state.collectAsStateWithLifecycle()
    val settings by settingsStore.flow.collectAsStateWithLifecycle<AppSettings?>(initialValue = null)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val actions = remember { UiActions(repo, settingsStore, scope, snackbar) }
    val navController = rememberNavController()
    val nav = remember(navController) { Nav(navController) }

    val d = data
    val s = settings
    CompositionLocalProvider(LocalActions provides actions) {
        if (d == null || s == null) {
            LoadingBox()
        } else {
            AppNavHost(navController, nav, d, s)
            // Buka layar dari notifikasi
            LaunchedEffect(openTarget) {
                if (openTarget != null) {
                    when (openTarget) {
                        "backup" -> nav.backup()
                        "reminders" -> nav.tab("reminders")
                        else -> nav.tab("home")
                    }
                    onOpenHandled()
                }
            }
        }
    }
}

@Composable
private fun BottomBar(navController: NavHostController, nav: Nav) {
    val entry by navController.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    NavigationBar {
        tabs.forEach { t ->
            NavigationBarItem(
                selected = current == t.route,
                onClick = { nav.tab(t.route) },
                icon = { Icon(t.icon, null) },
                label = { Text(t.label) },
            )
        }
    }
}

@Composable
private fun AppNavHost(navController: NavHostController, nav: Nav, data: AppData, settings: AppSettings) {
    val bottomBar: @Composable () -> Unit = { BottomBar(navController, nav) }
    NavHost(navController, startDestination = "home") {
        composable("home") { HomeScreen(data, settings, nav, bottomBar) }
        composable("vehicles") { VehiclesScreen(data, nav, bottomBar) }
        composable("reminders") { RemindersScreen(data, settings, nav, bottomBar) }
        composable("history") { HistoryScreen(data, settings, nav, bottomBar) }
        composable("costs") { CostsScreen(data, settings, nav, bottomBar) }

        screen("vehicle/{id}") { VehicleDetailScreen(data, it.req("id"), nav) }
        screen("vehicle_edit", "id") { VehicleEditScreen(data, it["id"], nav) }
        screen("odometer/{vehicleId}") { OdometerScreen(data, it.req("vehicleId"), nav) }
        screen("visit/{id}") { VisitDetailScreen(data, it.req("id"), nav) }
        screen("visit_edit", "id", "vehicleId", "reminderId") { VisitEditScreen(data, settings, it["id"], it["vehicleId"], it["reminderId"], nav) }
        screen("reminder_edit", "id", "vehicleId") { ReminderEditScreen(data, settings, it["id"], it["vehicleId"], nav) }
        screen("component/{id}") { ComponentDetailScreen(data, it.req("id"), nav) }
        screen("component_edit", "id", "vehicleId") { ComponentEditScreen(data, settings, it["id"], it["vehicleId"], nav) }
        screen("installation_edit", "id", "componentId") { InstallationEditScreen(data, it["id"], it["componentId"], nav) }
        screen("inspection/{id}") { InspectionDetailScreen(data, it.req("id"), nav) }
        screen("inspection_edit", "id", "vehicleId") { InspectionEditScreen(data, settings, it["id"], it["vehicleId"], nav) }
        screen("expense_edit", "id", "vehicleId") { ExpenseEditScreen(data, settings, it["id"], it["vehicleId"], nav) }
        screen("report", "vehicleId") { ReportScreen(data, settings, it["vehicleId"], nav) }
        screen("settings") { SettingsScreen(data, settings, nav) }
        screen("backup") { BackupScreen(data, settings, nav) }
        screen("catalog") { CatalogScreen(data, nav) }
        screen("workshops") { WorkshopsScreen(data, nav) }
    }
}

private fun Map<String, String?>.req(key: String): String = this[key] ?: ""

/**
 * Mendaftarkan layar dengan argumen path ({x} di [base]) dan argumen query opsional [optional].
 */
private fun NavGraphBuilder.screen(base: String, vararg optional: String, content: @Composable (Map<String, String?>) -> Unit) {
    val pathArgs = Regex("\\{(\\w+)\\}").findAll(base).map { it.groupValues[1] }.toList()
    val route = base + if (optional.isEmpty()) "" else optional.joinToString("&", prefix = "?") { "$it={$it}" }
    composable(
        route,
        arguments = pathArgs.map { navArgument(it) { type = NavType.StringType } } +
            optional.map { navArgument(it) { type = NavType.StringType; nullable = true; defaultValue = null } },
    ) { entry ->
        val args = (pathArgs + optional).associateWith { entry.arguments?.getString(it) }
        content(args)
    }
}
