package com.nawzblaze.momentwise

import android.content.Context
import com.nawzblaze.momentwise.data.AppRuleRepository
import com.nawzblaze.momentwise.data.AppSettingsRepository
import com.nawzblaze.momentwise.data.local.LocalPersistence

/**
 * Minimal hand-rolled dependency container — proportionate for an MVP with two
 * repositories and no backend. No DI framework is warranted at this scale.
 */
class AppContainer(context: Context) {
    val appRuleRepository: AppRuleRepository = LocalPersistence.appRuleRepository(context)
    val appSettingsRepository: AppSettingsRepository = LocalPersistence.appSettingsRepository(context)
}

class MomentwiseApplication : android.app.Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
