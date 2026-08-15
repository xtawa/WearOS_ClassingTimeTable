package com.xtawa.classingtime.ui.theme

import android.content.Context
import androidx.compose.runtime.Immutable

internal enum class ClassingThemeMode {
    System,
    Light,
    Dark,
    ;

    companion object {
        fun fromRaw(raw: String?): ClassingThemeMode =
            entries.firstOrNull { it.name == raw } ?: System
    }
}

@Immutable
internal data class ClassingAppearanceState(
    val themeMode: ClassingThemeMode = ClassingThemeMode.System,
    val dynamicColor: Boolean = false,
)

internal object ClassingAppearanceStore {
    private const val PrefsName = "classing_appearance"
    private const val ThemeModeKey = "theme_mode"
    private const val DynamicColorKey = "dynamic_color"

    fun load(context: Context): ClassingAppearanceState {
        val preferences = context.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
        return ClassingAppearanceState(
            themeMode = ClassingThemeMode.fromRaw(preferences.getString(ThemeModeKey, null)),
            dynamicColor = preferences.getBoolean(DynamicColorKey, false),
        )
    }

    fun save(context: Context, state: ClassingAppearanceState) {
        context.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
            .edit()
            .putString(ThemeModeKey, state.themeMode.name)
            .putBoolean(DynamicColorKey, state.dynamicColor)
            .apply()
    }
}
