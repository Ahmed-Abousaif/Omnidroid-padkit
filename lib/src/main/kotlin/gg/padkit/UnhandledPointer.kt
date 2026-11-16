package gg.padkit

import androidx.compose.ui.geometry.Offset

/** A pressed pointer in root coordinates. [isNew] is true only on its initial down event. */
data class UnhandledPointer(val id: Long, val position: Offset, val isNew: Boolean = false)
