package app.openscout.scout

import android.app.Application
import android.content.Context
import android.os.Build
import app.openscout.scout.core.ScoutClient
import app.openscout.scout.core.bridge.BridgeConnection
import app.openscout.scout.core.identity.IdentityStore
import app.openscout.scout.ui.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ScoutApplication : Application() {
    val appScope = CoroutineScope(SupervisorJob())
    lateinit var identity: IdentityStore private set
    lateinit var connection: BridgeConnection private set
    lateinit var client: ScoutClient private set
    lateinit var settings: SettingsStore private set

    override fun onCreate() {
        super.onCreate()
        identity = IdentityStore(this)
        connection = BridgeConnection(identity, appScope, isEmulator = isProbablyEmulator())
        client = ScoutClient(connection)
        settings = SettingsStore(this)
    }

    companion object {
        fun isProbablyEmulator(): Boolean =
            Build.FINGERPRINT.startsWith("generic") || Build.FINGERPRINT.contains("emulator") ||
                Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish") ||
                Build.PRODUCT.contains("sdk") || Build.MODEL.contains("Emulator") ||
                Build.MODEL.contains("Android SDK built for")
    }
}

/** Per-device UI preferences. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("scout.settings", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        runCatching { ThemeMode.valueOf(prefs.getString("themeMode", null) ?: "System") }.getOrDefault(ThemeMode.System),
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _wallpaperColor = MutableStateFlow(prefs.getBoolean("wallpaperColor", false))
    val wallpaperColor: StateFlow<Boolean> = _wallpaperColor.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString("themeMode", mode.name).apply()
        _themeMode.value = mode
    }

    fun setWallpaperColor(enabled: Boolean) {
        prefs.edit().putBoolean("wallpaperColor", enabled).apply()
        _wallpaperColor.value = enabled
    }
}
