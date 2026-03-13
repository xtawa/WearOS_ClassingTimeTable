package com.classing.wear.timetable.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.classing.wear.timetable.ClassingTimetableApplication
import com.classing.wear.timetable.core.AppContainer

class AppViewModelFactory(
    private val appContainer: AppContainer,
    private val create: (AppContainer) -> ViewModel,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        @Suppress("UNCHECKED_CAST")
        return create(appContainer) as T
    }

    companion object {
        fun fromExtras(extras: CreationExtras, create: (AppContainer) -> ViewModel): AppViewModelFactory {
            val app = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as ClassingTimetableApplication
            return AppViewModelFactory(app.appContainer, create)
        }
    }
}
