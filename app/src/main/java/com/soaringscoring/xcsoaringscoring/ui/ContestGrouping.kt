package com.soaringscoring.xcsoaringscoring.ui

import com.soaringscoring.xcsoaringscoring.api.Contest
import com.soaringscoring.xcsoaringscoring.api.ContestClass
import com.soaringscoring.xcsoaringscoring.api.TaskRow
import com.soaringscoring.xcsoaringscoring.util.dateOnly
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

enum class ContestTimeFrame(val label: String) {
    CURRENT("Current"),
    FUTURE("Future"),
    PAST("Past")
}

/** A month-grouped section of contests, e.g. "SEPTEMBER 2026" -> [Test weekend, ...]. */
data class ContestMonthGroup(val label: String, val contests: List<Contest>)

object ContestGrouping {

    /** Today as an ISO date string (yyyy-MM-dd) - contest/task dates are the same
     * format, so plain string comparison sorts/compares correctly, no date parsing
     * library needed. */
    fun todayIso(): String = LocalDate.now().toString()

    fun categorize(contest: Contest, today: String = todayIso()): ContestTimeFrame {
        val start = dateOnly(contest.startDate)
        val end = dateOnly(contest.endDate)
        return when {
            today in start..end -> ContestTimeFrame.CURRENT
            start > today -> ContestTimeFrame.FUTURE
            else -> ContestTimeFrame.PAST
        }
    }

    /**
     * Filters [contests] to [timeFrame], then groups by the month of startDate and
     * sorts: Future soonest-first (ascending), Past most-recent-first (descending),
     * matching SoaringScoring's own site. Current isn't grouped by month - there's
     * only ever a handful of contests running at once.
     */
    fun groupedFor(contests: List<Contest>, timeFrame: ContestTimeFrame, today: String = todayIso()): List<ContestMonthGroup> {
        val filtered = contests.filter { categorize(it, today) == timeFrame }

        if (timeFrame == ContestTimeFrame.CURRENT) {
            return if (filtered.isEmpty()) emptyList() else listOf(ContestMonthGroup("", filtered))
        }

        val byMonth = filtered.groupBy { monthKey(it.startDate) }
        val sortedKeys = if (timeFrame == ContestTimeFrame.FUTURE) {
            byMonth.keys.sorted()
        } else {
            byMonth.keys.sortedDescending()
        }

        return sortedKeys.map { key ->
            val monthContests = byMonth.getValue(key).sortedBy { dateOnly(it.startDate) }
            val ordered = if (timeFrame == ContestTimeFrame.PAST) monthContests.reversed() else monthContests
            ContestMonthGroup(monthLabel(key), ordered)
        }
    }

    /** yyyy-MM sort key so groups order correctly regardless of month name. */
    private fun monthKey(isoDate: String): String = dateOnly(isoDate).take(7)

    private fun monthLabel(key: String): String {
        val (year, month) = key.split("-").let { it[0] to it[1].toInt() }
        val monthName = LocalDate.of(year.toInt(), month, 1)
            .month
            .getDisplayName(TextStyle.FULL, Locale.getDefault())
            .uppercase(Locale.getDefault())
        return "$monthName $year"
    }
}

object TaskFiltering {

    /**
     * Which tasks to actually show for [selectedClass], given the contest's
     * [timeFrame]: for Future/Past, every task in that class. For Current, only
     * today's task if one's published, else the most recent day before today -
     * so a live contest shows "the task", not every day flown so far.
     */
    fun visibleTasks(
        tasks: List<TaskRow>,
        selectedClass: ContestClass?,
        timeFrame: ContestTimeFrame,
        today: String = ContestGrouping.todayIso()
    ): List<TaskRow> {
        if (selectedClass == null) return emptyList()

        val forClass = tasks
            .filter { it.classId == selectedClass.id }
            .sortedWith(compareBy({ it.dayNumber }, { it.dhtHandicap ?: 0.0 }))

        if (timeFrame != ContestTimeFrame.CURRENT) return forClass

        val targetDate = forClass
            .map { dateOnly(it.date) }
            .distinct()
            .filter { it <= today }
            .maxOrNull()
            ?: return emptyList()

        return forClass.filter { dateOnly(it.date) == targetDate }
    }
}
