package com.soaringscoring.xcsoaringscoring.util

/**
 * The API docs show plain dates ("2025-01-11"), but some responses come back
 * as full timestamps ("2025-01-11T00:00:00Z"). Contest/task dates in this app
 * are always displayed as a day, never a time, so just chop off anything
 * from "T" onward if it's there.
 */
fun dateOnly(raw: String): String {
    val tIndex = raw.indexOf('T')
    return if (tIndex > 0) raw.substring(0, tIndex) else raw
}
