package com.xtawa.classingtime.screen

internal data class ImportMethodFeedback(val message: String = "", val warnings: List<String> = emptyList())

/** Feedback belongs to a method; only the currently parsed method can confirm a preview. */
internal data class ImportFeedbackState(
    val sections: Map<ImportFocusMethod, ImportMethodFeedback> = emptyMap(),
    val pendingMethod: ImportFocusMethod? = null,
) {
    fun forMethod(method: ImportFocusMethod) = sections[method] ?: ImportMethodFeedback()

    fun parsed(method: ImportFocusMethod, message: String, warnings: List<String>, hasLessons: Boolean) = copy(
        sections = sections + (method to ImportMethodFeedback(message, warnings)),
        pendingMethod = method.takeIf { hasLessons },
    )

    fun message(method: ImportFocusMethod, message: String) = copy(
        sections = sections + (method to forMethod(method).copy(message = message)),
    )

    fun editing(method: ImportFocusMethod) = copy(
        sections = sections - method,
        pendingMethod = pendingMethod.takeUnless { it == method },
    )

    fun clearPreview() = copy(pendingMethod = null)
    fun canConfirm(method: ImportFocusMethod) = pendingMethod == method
}
