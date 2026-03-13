package com.classing.wear.timetable.domain.model

data class Course(
    val localId: Long,
    val remoteId: String?,
    val semesterId: Long,
    val name: String,
    val teacher: String,
    val classroom: String,
    val note: String,
    val colorLabel: String,
    val isFavorite: Boolean,
    val version: Long,
)
