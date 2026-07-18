package com.classing.wear.timetable.sync

import kotlinx.coroutines.sync.Mutex

/** Serializes every in-process writer of the Wear timetable database and its sync stamp. */
internal object WearTimetableApplyLock {
    val mutex = Mutex()
}
