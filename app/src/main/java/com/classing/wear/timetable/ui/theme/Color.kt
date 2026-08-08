package com.classing.wear.timetable.ui.theme

import androidx.compose.ui.graphics.Color

// Brand / Wear palette (design spec §40)
val IndigoPrimary = Color(0xFF7C79F7)
val IndigoOnPrimary = Color(0xFF19175F)
val IndigoPrimaryContainer = Color(0xFF36346F)
val IndigoOnPrimaryContainer = Color(0xFFE4E2FF)
val IndigoSecondary = Color(0xFF22C3D6)
val IndigoOnSecondary = Color(0xFF00363D)
val IndigoSecondaryContainer = Color(0xFF173A40)
val IndigoOnSecondaryContainer = Color(0xFFB4F1F7)
val IndigoTertiary = Color(0xFFF59E0B)
val IndigoOnTertiary = Color(0xFF422B00)
val IndigoBackground = Color(0xFF000000)
val IndigoOnBackground = Color(0xFFF4F4F7)
val IndigoSurface = Color(0xFF161821)
val IndigoOnSurface = Color(0xFFF4F4F7)
val IndigoSurfaceVariant = Color(0xFF1B1E28)
val IndigoOnSurfaceVariant = Color(0xFFB7B7C0)
val IndigoOutline = Color(0xFF858995)
val IndigoOutlineVariant = Color(0xFF292E3B)
val IndigoSurfaceContainerLow = Color(0xFF0B0D14)
val IndigoSurfaceContainer = Color(0xFF161821)
val IndigoSurfaceContainerHigh = Color(0xFF1E2230)
val IndigoError = Color(0xFFFF8A86)

// Semantic status colors (design spec §2.2).
// Secondary (cyan) is reserved for sync / connectivity states only.
val IndigoSuccess = Color(0xFF22C55E)
val IndigoWarning = Color(0xFFF59E0B)
val IndigoInfo = Color(0xFF38BDF8)

// Course palette (design spec §5). Must stay identical to the mobile palette
// declared in mobile/.../screen/CourseColors.kt (design spec §43).
val CourseMath = Color(0xFF7C79F7)
val CourseEnglish = Color(0xFF22C55E)
val CoursePhysics = Color(0xFF38BDF8)
val CourseProgramming = Color(0xFFF59E0B)
val CoursePolitics = Color(0xFFF472B6)
val CourseLinearAlgebra = Color(0xFFFACC15)
val CourseSports = Color(0xFF60A5FA)
val CourseOther = Color(0xFF64748B)

// Course block emphasis (design spec §14): fill 18%, border 32%.
const val COURSE_BLOCK_ALPHA = 0.18f
const val COURSE_BLOCK_BORDER_ALPHA = 0.32f
