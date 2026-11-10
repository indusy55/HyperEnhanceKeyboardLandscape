package com.hyperenhance.keyboardlandscape

import android.app.Application
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.CopyOnWriteArraySet

class ModuleApp : Application(), XposedServiceHelper.OnServiceListener {

    override fun onCreate() {
        super.onCreate()
        XposedServiceHelper.registerListener(this)
    }

    override fun onServiceBind(service: XposedService) {
        FrameworkService.set(service)
    }

    override fun onServiceDied(service: XposedService) {
        FrameworkService.set(null)
    }
}

/** Holds the framework service bound into this module app. */
object FrameworkService {
    @Volatile
    var service: XposedService? = null
        private set

    private val listeners = CopyOnWriteArraySet<(XposedService?) -> Unit>()

    fun addListener(listener: (XposedService?) -> Unit) {
        listeners.add(listener)
        listener(service)
    }

    fun removeListener(listener: (XposedService?) -> Unit) {
        listeners.remove(listener)
    }

    fun set(service: XposedService?) {
        this.service = service
        listeners.forEach { it(service) }
    }
}
