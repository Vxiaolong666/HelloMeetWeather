package com.HelloMeet.weather.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** Switches manifest launcher aliases without restarting the app. */
object AppIconManager {
    private fun alias(context: Context, name: String) =
        ComponentName(context.packageName, "${context.packageName}.$name")

    fun apply(context: Context, style: AppIconStyle): Boolean = runCatching {
        val appContext = context.applicationContext
        val packageManager = appContext.packageManager
        val aliases = mapOf(
            AppIconStyle.DEFAULT to alias(appContext, "IconDefault"),
            AppIconStyle.COLORFUL to alias(appContext, "IconColorful"),
            AppIconStyle.MINIMAL to alias(appContext, "IconMinimal"),
        )
        val selected = aliases.getValue(style)

        // Always expose the new launcher entry before hiding the old one. Some OEM
        // launchers otherwise briefly remove the app from the desktop/app drawer.
        setState(
            packageManager = packageManager,
            component = selected,
            state = PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
        )
        aliases.values.filterNot { it == selected }.forEach { previous ->
            setState(
                packageManager = packageManager,
                component = previous,
                state = PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            )
        }
    }.isSuccess

    private fun setState(
        packageManager: PackageManager,
        component: ComponentName,
        state: Int,
    ) {
        if (packageManager.getComponentEnabledSetting(component) == state) return
        packageManager.setComponentEnabledSetting(
            component,
            state,
            PackageManager.DONT_KILL_APP,
        )
    }
}
