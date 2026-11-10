package com.subradar.app.feature

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.subradar.app.R
import com.subradar.app.domain.Theme
import kotlinx.coroutines.flow.StateFlow

private val dark = darkColorScheme(
    primary = Color(0xFFCBB3FF), onPrimary = Color(0xFF35215F),
    primaryContainer = Color(0xFF493278), onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFF2B8FF), background = Color(0xFF151220),
    surface = Color(0xFF151220), surfaceContainer = Color(0xFF241D35),
    surfaceContainerLow = Color(0xFF201A2E), onSurface = Color(0xFFF1EBFF),
    onSurfaceVariant = Color(0xFFCCC2DC), outline = Color(0xFF9689AB)
)
private val light = lightColorScheme(
    primary = Color(0xFF6043A3), primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF241248), background = Color(0xFFFBF8FF),
    surface = Color(0xFFFBF8FF), surfaceContainer = Color(0xFFF0E8FA),
    surfaceContainerLow = Color(0xFFF7F0FD)
)
private val tabs = listOf(
    Triple("home", R.string.home, Icons.Default.Home),
    Triple("subscriptions", R.string.subscriptions, Icons.AutoMirrored.Filled.List),
    Triple("calendar", R.string.calendar, Icons.Default.DateRange),
    Triple("analytics", R.string.analytics, Icons.Default.Info)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarApp(vm: RadarViewModel, pending: StateFlow<String?>, onOpened: () -> Unit) {
    val snapshot by vm.data.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val language by vm.language.collectAsStateWithLifecycle()
    val openId by pending.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val nav = rememberNavController()
    val current by nav.currentBackStackEntryAsState()
    val route = current?.destination?.route
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(vm, context) { vm.events.collect { snackbar.showSnackbar(context.getString(it)) } }
    LaunchedEffect(openId, snapshot != null) {
        val id = openId
        if (id != null && snapshot != null) {
            if (snapshot?.subscriptions?.any { it.id == id } == true) {
                nav.navigate("detail/$id") { launchSingleTop = true }
            }
            onOpened()
        }
    }

    MaterialTheme(colorScheme = if (prefs.theme == Theme.DARK) dark else light) {
        Scaffold(
            topBar = {
                val title = tabs.firstOrNull { it.first == route }?.second ?: when (route) {
                    "settings" -> R.string.settings
                    "edit/{id}" -> R.string.edit_subscription
                    "new" -> R.string.add_subscription
                    "detail/{id}" -> R.string.details
                    else -> R.string.home
                }
                TopAppBar(
                    title = { Text(stringResource(if (route == "home") R.string.brand else title)) },
                    navigationIcon = {
                        if (route != null && tabs.none { it.first == route }) {
                            IconButton(onClick = { nav.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                            }
                        }
                    },
                    actions = {
                        if (route == "home") IconButton(onClick = { nav.navigate("settings") }) {
                            Icon(Icons.Default.Settings, stringResource(R.string.settings))
                        }
                    }
                )
            },
            bottomBar = {
                if (tabs.any { it.first == route }) NavigationBar {
                    tabs.forEach { (path, label, icon) ->
                        NavigationBarItem(selected = route == path, onClick = {
                            nav.navigate(path) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }, icon = { Icon(icon, null) }, label = { Text(stringResource(label)) })
                    }
                }
            },
            floatingActionButton = {
                if (snapshot != null && tabs.any { it.first == route }) {
                    FloatingActionButton(onClick = { nav.navigate("new") }) {
                        Icon(Icons.Default.Add, stringResource(R.string.add_subscription))
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
                NavHost(nav, "home", Modifier.widthIn(max = 840.dp).fillMaxSize()) {
                    composable("home") { snapshot?.let { Dashboard(it) { id -> nav.navigate("detail/$id") } } ?: Loading() }
                    composable("subscriptions") {
                        snapshot?.let { SubscriptionList(it) { id -> nav.navigate("detail/$id") } } ?: Loading()
                    }
                    composable("calendar") {
                        snapshot?.let { CalendarScreen(it, prefs.mondayFirst, vm) { id -> nav.navigate("detail/$id") } } ?: Loading()
                    }
                    composable("analytics") { snapshot?.let { Analytics(it) } ?: Loading() }
                    composable("settings") { SettingsScreen(vm, prefs, language.orEmpty()) }
                    composable("new") {
                        snapshot?.let {
                            SubscriptionEditor(null, it.categories, prefs.currency, vm) { nav.popBackStack() }
                        } ?: Loading()
                    }
                    composable("edit/{id}") { entry ->
                        val id = entry.arguments?.getString("id")
                        val sub = snapshot?.subscriptions?.firstOrNull { it.id == id }
                        when {
                            sub != null -> SubscriptionEditor(sub, snapshot?.categories.orEmpty(), prefs.currency, vm) {
                                nav.popBackStack()
                            }
                            snapshot == null -> Loading()
                            else -> Missing()
                        }
                    }
                    composable("detail/{id}") { entry ->
                        val id = entry.arguments?.getString("id")
                        val sub = snapshot?.subscriptions?.firstOrNull { it.id == id }
                        when {
                            sub != null -> SubscriptionDetails(sub, checkNotNull(snapshot), vm,
                                edit = { nav.navigate("edit/$id") }, deleted = { nav.popBackStack() })
                            snapshot == null -> Loading()
                            else -> Missing()
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}
@Composable private fun Missing() { Text(stringResource(R.string.missing), Modifier.padding(24.dp)) }
