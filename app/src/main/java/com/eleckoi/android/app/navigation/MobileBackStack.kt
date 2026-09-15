package com.eleckoi.android.app.navigation

/**
 * Replaces the current destination without duplicating an identical destination directly below it.
 * This keeps edit-and-return flows from growing a cycle in the navigation history.
 */
internal fun <T> MutableList<T>.replaceTopWith(destination: T) {
    when {
        isEmpty() -> add(destination)
        size >= 2 && this[lastIndex - 1] == destination -> removeAt(lastIndex)
        else -> this[lastIndex] = destination
    }
}
