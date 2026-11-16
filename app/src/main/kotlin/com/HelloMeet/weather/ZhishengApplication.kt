package com.HelloMeet.weather

import android.app.Application
import kotlinx.coroutines.launch
import com.HelloMeet.weather.data.CityRepository
import com.HelloMeet.weather.data.HistoricalWeatherRepository
import com.HelloMeet.weather.data.KnownPlaceStore
import com.HelloMeet.weather.data.RadarRepository
import com.HelloMeet.weather.data.SecretStore
import com.HelloMeet.weather.data.SettingsRepository
import android.content.ComponentCallbacks2
import com.HelloMeet.weather.widget.WidgetIcons
import com.HelloMeet.weather.widget.WidgetSkyBitmap

class ZhishengApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        com.HelloMeet.weather.ui.UiRefreshRate.install(this)
        // Compose controls app palettes. Keep platform resources on the system
        // configuration, including widgets and notification RemoteViews.
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            getSystemService(android.app.UiModeManager::class.java)
                ?.setApplicationNightMode(android.app.UiModeManager.MODE_NIGHT_AUTO)
        }
        SecretStore.init(this)
        SettingsRepository.init(this)
        com.HelloMeet.weather.data.AutomationSettingsRepository.init(this)
        com.HelloMeet.weather.data.SceneWeatherRepository.init(this)
        CityRepository.init(this)
        HistoricalWeatherRepository.init(this)
        RadarRepository.init(this)
        KnownPlaceStore.init(this)
        com.HelloMeet.weather.data.WeatherComCnApi.init(this)
        com.HelloMeet.weather.data.FusionScoreboard.init(this)
        com.HelloMeet.weather.widget.WidgetRuntime.start(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Android 17 enforces per-process memory limits. These are rebuildable
        // images only; keep city weather snapshots so paging stays populated.
        if (level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN || level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            WidgetIcons.clearMemoryCache()
            WidgetSkyBitmap.clearMemoryCache()
        }
    }
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        com.HelloMeet.weather.widget.WidgetRuntime.scope.launch {
            com.HelloMeet.weather.widget.WidgetRuntime.renderAll(this@ZhishengApplication)
            com.HelloMeet.weather.widget.WidgetNotification.refresh(this@ZhishengApplication)
        }
    }
}
