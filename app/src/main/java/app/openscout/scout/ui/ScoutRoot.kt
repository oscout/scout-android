package app.openscout.scout.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import app.openscout.scout.ui.components.LocalOpenDrawer
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.openscout.scout.ui.components.ScoutCanvas
import app.openscout.scout.ui.screens.AgentDetailScreen
import app.openscout.scout.ui.screens.AgentsScreen
import app.openscout.scout.ui.screens.AlertsScreen
import app.openscout.scout.ui.screens.ChatsScreen
import app.openscout.scout.ui.screens.HomeScreen
import app.openscout.scout.ui.screens.NewSessionScreen
import app.openscout.scout.ui.screens.PairScreen
import app.openscout.scout.ui.screens.ScanScreen
import app.openscout.scout.ui.screens.SettingsScreen
import app.openscout.scout.ui.screens.TailScreen
import app.openscout.scout.ui.screens.ThreadScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable

// -- Routes -------------------------------------------------------------------

@Serializable data class PairRoute(val adding: Boolean = false)
@Serializable data object ScanRoute
@Serializable data object HomeRoute
@Serializable data object ChatsRoute
@Serializable data object AgentsRoute
@Serializable data object TailRoute
@Serializable data object AlertsRoute
@Serializable data object SettingsRoute
@Serializable data object NewSessionRoute
@Serializable data class AgentRoute(val agentId: String)
@Serializable data class ThreadRoute(val conversationId: String? = null, val title: String, val agentId: String? = null)

@Composable
fun ScoutRoot(vm: AppViewModel, pendingLink: MutableStateFlow<String?>) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val link by pendingLink.collectAsStateWithLifecycle()
    val inbox by vm.inbox.collectAsStateWithLifecycle()
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    val agents by vm.agents.collectAsStateWithLifecycle()
    val machines by vm.machines.collectAsStateWithLifecycle()
    val activeKey by vm.activeKey.collectAsStateWithLifecycle()
    val linkState by vm.linkState.collectAsStateWithLifecycle()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(link) {
        val l = link ?: return@LaunchedEffect
        pendingLink.value = null
        vm.pair(l)
        nav.navigate(PairRoute(adding = vm.isPaired)) { launchSingleTop = true }
    }

    val topLevel = listOf(HomeRoute, ChatsRoute, AgentsRoute, TailRoute, AlertsRoute)
    val atTopLevel = topLevel.any { r -> destination?.hierarchy?.any { it.hasRoute(r::class) } == true }
    fun isAt(route: Any) = destination?.hierarchy?.any { it.hasRoute(route::class) } == true

    val openDrawer: () -> Unit = { scope.launch { drawer.open() } }
    fun go(action: () -> Unit) {
        scope.launch { drawer.close() }
        action()
    }

    ScoutCanvas {
        ModalNavigationDrawer(
            drawerState = drawer,
            gesturesEnabled = atTopLevel || drawer.isOpen,
            scrimColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f),
            drawerContent = {
                ScoutDrawer(
                    isAt = ::isAt,
                    alerts = inbox.data?.size ?: 0,
                    unreadChats = conversations.data?.sumOf { it.unreadCount ?: 0 } ?: 0,
                    agentCount = agents.data?.size,
                    machines = machines,
                    activeKey = activeKey,
                    link = linkState,
                    onNewSession = { go { nav.navigate(NewSessionRoute) } },
                    onNavigate = { route -> go { nav.navigateToTab(route) } },
                    onSwitchHost = { key -> go { vm.switchMachine(key) } },
                    onPairHost = { go { nav.navigate(PairRoute(adding = true)) } },
                    onSettings = { go { nav.navigate(SettingsRoute) } },
                )
            },
        ) {
            CompositionLocalProvider(LocalOpenDrawer provides openDrawer) {
                NavHost(
                    navController = nav,
                    startDestination = if (vm.isPaired) HomeRoute else PairRoute(),
                    enterTransition = { fadeIn(tween(160)) },
                    exitTransition = { fadeOut(tween(100)) },
                    popEnterTransition = { fadeIn(tween(160)) },
                    popExitTransition = { fadeOut(tween(100)) },
                ) {
                    composable<PairRoute> { entry ->
                        val route = entry.toRoute<PairRoute>()
                        PairScreen(
                            vm = vm,
                            adding = route.adding,
                            onScan = { nav.navigate(ScanRoute) },
                            onPaired = {
                                nav.navigate(HomeRoute) { popUpTo(0) { inclusive = true } }
                            },
                            onBack = if (route.adding) ({ nav.popBackStack() }) else null,
                        )
                    }
                    composable<ScanRoute> {
                        ScanScreen(
                            onResult = { value ->
                                nav.popBackStack()
                                vm.pair(value)
                            },
                            onBack = { nav.popBackStack() },
                        )
                    }
                    composable<HomeRoute> {
                        HomeScreen(
                            vm = vm,
                            onOpenSettings = { nav.navigate(SettingsRoute) },
                            onNewSession = { nav.navigate(NewSessionRoute) },
                            onOpenThread = { id, title, agentId -> nav.navigate(ThreadRoute(id, title, agentId)) },
                            onOpenAlerts = { nav.navigateToTab(AlertsRoute) },
                            onOpenAgents = { nav.navigateToTab(AgentsRoute) },
                            onOpenChats = { nav.navigateToTab(ChatsRoute) },
                            onOpenTail = { nav.navigateToTab(TailRoute) },
                        )
                    }
                    composable<ChatsRoute> {
                        ChatsScreen(vm = vm, onOpen = { c -> nav.navigate(ThreadRoute(c.id, c.title)) }, onNewSession = { nav.navigate(NewSessionRoute) })
                    }
                    composable<AgentsRoute> {
                        AgentsScreen(vm = vm, onOpen = { agent -> nav.navigate(AgentRoute(agent.id)) }, onNewSession = { nav.navigate(NewSessionRoute) })
                    }
                    composable<TailRoute> { TailScreen(vm = vm) }
                    composable<AlertsRoute> {
                        AlertsScreen(vm = vm, onOpenThread = { id, title -> nav.navigate(ThreadRoute(id, title)) })
                    }
                    composable<AgentRoute> { entry ->
                        val route = entry.toRoute<AgentRoute>()
                        AgentDetailScreen(
                            vm = vm,
                            agentId = route.agentId,
                            onBack = { nav.popBackStack() },
                            onMessage = { agent -> nav.navigate(ThreadRoute(agent.conversationId, agent.title, agent.id)) },
                        )
                    }
                    composable<ThreadRoute> { entry ->
                        val route = entry.toRoute<ThreadRoute>()
                        ThreadScreen(vm = vm, conversationId = route.conversationId, title = route.title, agentId = route.agentId, onBack = { nav.popBackStack() })
                    }
                    composable<SettingsRoute> {
                        SettingsScreen(
                            vm = vm,
                            onBack = { nav.popBackStack() },
                            onPairAnother = { nav.navigate(PairRoute(adding = true)) },
                            onAllForgotten = { nav.navigate(PairRoute()) { popUpTo(0) { inclusive = true } } },
                        )
                    }
                    composable<NewSessionRoute> {
                        NewSessionScreen(
                            vm = vm,
                            onBack = { nav.popBackStack() },
                            onStarted = { id, title, agentId ->
                                nav.navigate(ThreadRoute(id, title, agentId)) { popUpTo<NewSessionRoute> { inclusive = true } }
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun NavHostController.navigateToTab(route: Any) {
    navigate(route) {
        // Home is always the root of the signed-in stack (pairing replaces the
        // whole stack with it), so tabs pop back to Home and Back from any tab
        // lands there before leaving the app.
        popUpTo<HomeRoute> { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
