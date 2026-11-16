package com.HelloMeet.weather.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetProviderMatchingTest {
    private val klass = "com.HelloMeet.weather.widget.VistaComboWidgetProvider"

    @Test fun acceptsFrameworkAndLauncherNamesWithinTheSameApplication() {
        for (applicationId in listOf("com.HelloMeet.weather", "com.HelloMeet.weather.preview", "com.HelloMeet.weather.badgeexperience")) {
            for (name in listOf(klass, ".widget.VistaComboWidgetProvider", "VistaComboWidgetProvider")) {
                assertTrue("$applicationId: $name", widgetProviderClassMatches(applicationId, klass, applicationId, name))
            }
        }
    }

    @Test fun rejectsAnotherVariantAndUnrelatedProviderClasses() {
        assertFalse(widgetProviderClassMatches("com.HelloMeet.weather", klass, "com.HelloMeet.weather.preview", klass))
        assertFalse(widgetProviderClassMatches("com.HelloMeet.weather", klass, "com.HelloMeet.weather", "other.package.VistaComboWidgetProvider"))
        assertFalse(widgetProviderClassMatches("com.HelloMeet.weather", klass, "com.HelloMeet.weather", ".widget.ClassicComboWidgetProvider"))
        assertFalse(widgetProviderClassMatches("com.HelloMeet.weather", klass, "com.HelloMeet.weather", ""))
    }
}
