package com.nawzblaze.momentwise

import android.app.Application
import android.content.Context
import com.nawzblaze.momentwise.data.AppRuleRepository
import com.nawzblaze.momentwise.data.AppSettingsRepository
import com.nawzblaze.momentwise.data.GuardSessionRepository
import com.nawzblaze.momentwise.data.local.LocalPersistence
import com.nawzblaze.momentwise.monitoring.InstalledAppsSource
import com.nawzblaze.momentwise.monitoring.MonitoringCoordinator
import com.nawzblaze.momentwise.monitoring.NotificationChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Minimal hand-rolled dependency container. No DI framework is warranted at
 * this scale, and there is still no backend.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val appRuleRepository: AppRuleRepository = LocalPersistence.appRuleRepository(appContext)
    val appSettingsRepository: AppSettingsRepository = LocalPersistence.appSettingsRepository(appContext)
    val guardSessions: GuardSessionRepository = LocalPersistence.guardSessions(appContext)
    val installedApps: InstalledAppsSource = InstalledAppsSource(appContext)
}

class MomentwiseApplication : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationChannels.create(this)
        applicationScope.launch { LocalPersistence.migrateFoundationSamples(this@MomentwiseApplication) }
        MonitoringCoordinator.install(this)
    }
}
