package com.pact.app.core

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Per-app language, chosen on first launch with no system settings trip. The
 * pick is stored locally and applied by wrapping the Activity's base context,
 * so it works all the way back to API 26 without AppCompat.
 */
object LocaleHelper {

    private const val PREFS = "pact_locale"
    private const val KEY = "lang"

    /** Language tag the user picked (e.g. "en", "es"), or null on first ever launch. */
    fun stored(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)

    fun hasChosen(context: Context): Boolean = stored(context) != null

    fun set(context: Context, langTag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, langTag).apply()
    }

    /** Wrap a base context so all resources resolve in the chosen language. */
    fun wrap(context: Context): Context {
        val tag = stored(context) ?: return context
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
