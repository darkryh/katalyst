package io.github.darkryh.katalyst.idea.scheduler

/**
 * A pure-string mirror of Katalyst's cron engine, used by the IDE to validate and explain cron
 * expressions *without* loading the framework or resolving symbols.
 *
 * This intentionally re-implements the grammar of
 * `io.github.darkryh.katalyst.scheduler.cron.CronValidator` / `CronField` (the closed 6-field form
 * `second minute hour dayOfMonth month dayOfWeek`, Quartz `?`, `*`, `/`, `,`, `-`). It is vendored
 * here for the same reason as [io.github.darkryh.katalyst.idea.convention.PluginConventions]: the
 * plugin is a separate composite build and cannot depend on katalyst-scheduler. A golden-table test
 * (`KatalystCronTest`) pins parity with the canonical validator's accept/reject behaviour.
 *
 * Parity-critical detail: day-of-week is `0=Sunday .. 6=Saturday` (the engine matches
 * `DayOfWeek.value % 7`), so the describer must use that mapping.
 */
object KatalystCron {

    /** A parsed cron field, retained only as far as the describer needs it. */
    private sealed interface Field {
        object Every : Field                                  // "*" or (where allowed) "?"
        data class Exact(val value: Int) : Field
        data class Step(val step: Int) : Field                // "*/n", "m/n", "a-b/n"
        data class Range(val from: Int, val to: Int) : Field
        data class Listed(val values: List<Int>) : Field
    }

    private const val SECOND = 0
    private const val MINUTE = 1
    private const val HOUR = 2
    private const val DAY_OF_MONTH = 3
    private const val MONTH = 4
    private const val DAY_OF_WEEK = 5

    private val dayNames = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    private val monthNames = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )

    /** Validation errors for [expression]; empty if it is a valid Katalyst cron expression. */
    fun validate(expression: String): List<String> {
        if (expression.isBlank()) return listOf("Cron expression cannot be empty")

        val parts = expression.trim().split(Regex("\\s+"))
        if (parts.size != 6) {
            return listOf("Cron expression must have exactly 6 fields: second minute hour dayOfMonth month dayOfWeek")
        }

        val errors = mutableListOf<String>()
        fieldError(parts[SECOND], 0..59, "second", allowQuestion = false)?.let(errors::add)
        fieldError(parts[MINUTE], 0..59, "minute", allowQuestion = false)?.let(errors::add)
        fieldError(parts[HOUR], 0..23, "hour", allowQuestion = false)?.let(errors::add)
        fieldError(parts[DAY_OF_MONTH], 1..31, "day of month", allowQuestion = true)?.let(errors::add)
        fieldError(parts[MONTH], 1..12, "month", allowQuestion = false)?.let(errors::add)
        fieldError(parts[DAY_OF_WEEK], 0..6, "day of week", allowQuestion = true)?.let(errors::add)

        if (parts[DAY_OF_MONTH] == "?" && parts[DAY_OF_WEEK] == "?") {
            errors.add("At least one of day-of-month or day-of-week must be restricted (not both '?')")
        }
        return errors
    }

    fun isValid(expression: String): Boolean = validate(expression).isEmpty()

    /**
     * A human-readable description of [expression] (e.g. `"Every day at 02:00"`), or null when the
     * expression is invalid. Common patterns are phrased naturally; anything exotic still yields an
     * accurate—if terser—reading rather than a guess.
     */
    fun describe(expression: String): String? {
        val fields = parse(expression) ?: return null
        return describeParsed(fields)
    }

    // --- parsing ----------------------------------------------------------------------------------

    private fun parse(expression: String): List<Field>? {
        if (validate(expression).isNotEmpty()) return null
        val parts = expression.trim().split(Regex("\\s+"))
        return try {
            listOf(
                parseField(parts[SECOND], 0..59, allowQuestion = false),
                parseField(parts[MINUTE], 0..59, allowQuestion = false),
                parseField(parts[HOUR], 0..23, allowQuestion = false),
                parseField(parts[DAY_OF_MONTH], 1..31, allowQuestion = true),
                parseField(parts[MONTH], 1..12, allowQuestion = false),
                parseField(parts[DAY_OF_WEEK], 0..6, allowQuestion = true),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun fieldError(expr: String, range: IntRange, name: String, allowQuestion: Boolean): String? =
        try {
            parseField(expr, range, allowQuestion)
            null
        } catch (e: Exception) {
            "Invalid $name field '$expr': ${e.message ?: "Invalid format"}"
        }

    private fun parseField(expr: String, range: IntRange, allowQuestion: Boolean): Field = when {
        expr == "*" -> Field.Every
        expr == "?" && allowQuestion -> Field.Every
        expr == "?" -> error("'?' is not allowed in this field")
        "/" in expr -> parseStep(expr, range)
        "," in expr -> parseList(expr, range, allowQuestion)
        "-" in expr -> parseRange(expr, range)
        else -> Field.Exact(singleValue(expr, range))
    }

    private fun parseStep(expr: String, range: IntRange): Field {
        val slash = expr.split("/")
        val base = slash[0]
        val step = slash.getOrNull(1)?.toIntOrNull() ?: error("Invalid step value")
        require(step > 0) { "Step value must be positive" }

        val baseRange = when {
            base == "*" -> range
            "-" in base -> {
                val r = parseRange(base, range) as Field.Range
                r.from..r.to
            }
            else -> {
                val start = base.toIntOrNull() ?: error("Invalid value: $base")
                require(start in range) { "Value $start is not within valid range $range" }
                start..range.last
            }
        }

        val valid = baseRange.filter { (it - baseRange.first) % step == 0 }
        require(valid.isNotEmpty()) { "Step value '$step' produces no valid values" }
        // A single value with a step (e.g. "59/2") that yields only its starting value is rejected,
        // matching the canonical CronField.
        require(!(base != "*" && "-" !in base && valid.size == 1)) {
            "Step value '$step' produces no valid values"
        }
        return Field.Step(step)
    }

    private fun parseList(expr: String, range: IntRange, allowQuestion: Boolean): Field {
        val values = sortedSetOf<Int>()
        expr.split(",").forEach { part ->
            when (val f = parseField(part.trim(), range, allowQuestion)) {
                is Field.Exact -> values.add(f.value)
                is Field.Range -> (f.from..f.to).forEach(values::add)
                is Field.Step -> range.filter { (it - range.first) % f.step == 0 }.forEach(values::add)
                Field.Every -> range.forEach(values::add)
                is Field.Listed -> values.addAll(f.values)
            }
        }
        require(values.isNotEmpty()) { "List expression produces no valid values" }
        return Field.Listed(values.toList())
    }

    private fun parseRange(expr: String, range: IntRange): Field {
        val parts = expr.split("-")
        require(parts.size == 2) { "Invalid range format: $expr" }
        val start = parts[0].toIntOrNull() ?: error("Invalid range start: ${parts[0]}")
        val end = parts[1].toIntOrNull() ?: error("Invalid range end: ${parts[1]}")
        require(start in range && end in range) { "Range $start-$end is not within valid range $range" }
        require(start <= end) { "Range start ($start) must be <= end ($end)" }
        return Field.Range(start, end)
    }

    private fun singleValue(expr: String, range: IntRange): Int {
        val value = expr.toIntOrNull() ?: error("Invalid value: $expr")
        require(value in range) { "Value $value is not within valid range $range" }
        return value
    }

    // --- describing -------------------------------------------------------------------------------
    //
    // The describer composes natural English the way cron is actually read aloud: a "specific time
    // of day" expression (all of second/minute/hour pinned) is phrased as "<subject> at <time>"
    // (e.g. "Every Monday at 9:00 AM"); anything that recurs within the day leads with the finest
    // repeating unit ("Every 15 minutes") and appends day/month qualifiers. Times use 12-hour AM/PM,
    // days of the month use ordinals, weekday/weekend runs are named, and ranges read as windows.

    private val weekdays = setOf(1, 2, 3, 4, 5)
    private val weekend = setOf(0, 6)

    private fun describeParsed(f: List<Field>): String {
        val sec = f[SECOND]; val min = f[MINUTE]; val hour = f[HOUR]
        val dom = f[DAY_OF_MONTH]; val mon = f[MONTH]; val dow = f[DAY_OF_WEEK]

        // Specific time of day: second, minute and hour are all a single value.
        if (sec is Field.Exact && min is Field.Exact && hour is Field.Exact) {
            val time = formatTime(hour.value, min.value, sec.value)
            val (subject, monthConsumed) = daySubject(dom, dow, mon)
            val month = if (monthConsumed) "" else monthClause(mon)?.let { ", $it" } ?: ""
            return "$subject at $time$month"
        }

        // Otherwise: a schedule that recurs within the day. Lead with the finest repeating unit.
        return recurringLead(sec, min, hour) + scopeSuffix(dom, mon, dow)
    }

    private fun recurringLead(sec: Field, min: Field, hour: Field): String = when {
        sec is Field.Step -> "Every ${units(sec.step, "second")}"
        sec is Field.Every -> "Every second"

        min is Field.Step -> "Every ${units(min.step, "minute")}${hourWindow(hour)}"
        min is Field.Every -> when (hour) {
            is Field.Every -> "Every minute"
            is Field.Step -> "Every minute, every ${units(hour.step, "hour")}"
            is Field.Range -> "Every minute, between ${clock(hour.from)} and ${clock(hour.to)}"
            is Field.Exact -> "Every minute, during the ${clock(hour.value)} hour"
            is Field.Listed -> "Every minute, during ${joinNatural(hour.values.map { clock(it) })}"
        }

        min is Field.Exact -> when (hour) {
            is Field.Every ->
                if (min.value == 0) "Every hour, on the hour"
                else "At ${count(min.value, "minute")} past every hour"
            is Field.Step -> "At minute ${min.value}, every ${units(hour.step, "hour")}"
            is Field.Range -> "At minute ${min.value}, between ${clock(hour.from)} and ${clock(hour.to)}"
            is Field.Listed -> "At minute ${min.value}, during ${joinNatural(hour.values.map { clock(it) })}"
            is Field.Exact -> "At ${formatTime(hour.value, min.value, 0)}" // sec was Range/Listed
        }

        min is Field.Range -> "Every minute from minute ${min.from} through ${min.to}${hourWindow(hour)}"
        else -> "At ${minuteList(min)} past the hour${hourWindow(hour)}" // min Listed
    }

    /** The hour qualifier for a sub-hour frequency, e.g. ", between 9:00 AM and 5:00 PM". */
    private fun hourWindow(hour: Field): String = when (hour) {
        is Field.Every -> ""
        is Field.Range -> ", between ${clock(hour.from)} and ${clock(hour.to)}"
        is Field.Step -> ", every ${units(hour.step, "hour")}"
        is Field.Exact -> ", during the ${clock(hour.value)} hour"
        is Field.Listed -> ", during ${joinNatural(hour.values.map { clock(it) })}"
    }

    /**
     * The subject of a "<subject> at <time>" phrasing, plus whether it already named the month
     * (e.g. "On January 1st"), so the caller doesn't repeat it.
     */
    private fun daySubject(dom: Field, dow: Field, mon: Field): Pair<String, Boolean> = when {
        dow !is Field.Every -> dowSubject(dow) to false
        dom is Field.Every -> "Every day" to false
        // A single day in a single month is a yearly date — read it as "On January 1st".
        mon is Field.Exact && dom is Field.Exact ->
            ("On ${monthNames[mon.value - 1]} ${ordinal(dom.value)}") to true
        dom is Field.Exact -> "On the ${ordinal(dom.value)} of every month" to false
        dom is Field.Range ->
            "On the ${ordinal(dom.from)} through ${ordinal(dom.to)} of every month" to false
        dom is Field.Listed ->
            "On the ${joinNatural(dom.values.map { ordinal(it) })} of every month" to false
        dom is Field.Step -> "On every ${ordinal(dom.step)} day of the month" to false
        else -> "Every day" to false
    }

    private fun dowSubject(dow: Field): String = when (dow) {
        is Field.Exact -> "Every ${dayNames[dow.value]}"
        is Field.Range -> when {
            (dow.from..dow.to).toSet() == weekdays -> "On weekdays"
            else -> "${dayNames[dow.from]} through ${dayNames[dow.to]}"
        }
        is Field.Listed -> when (dow.values.toSet()) {
            weekdays -> "On weekdays"
            weekend -> "On weekends"
            else -> "Every ${joinNatural(dow.values.map { dayNames[it] })}"
        }
        is Field.Step -> "Every ${ordinal(dow.step)} day of the week"
        Field.Every -> "Every day"
    }

    /** The trailing day/month qualifiers for a recurring phrasing, e.g. ", only on Monday, only in May". */
    private fun scopeSuffix(dom: Field, mon: Field, dow: Field): String = buildString {
        dowClause(dow)?.let { append(", ").append(it) }
        domClause(dom)?.let { append(", ").append(it) }
        monthClause(mon)?.let { append(", ").append(it) }
    }

    private fun dowClause(dow: Field): String? = when (dow) {
        is Field.Exact -> "only on ${dayNames[dow.value]}"
        is Field.Range -> when {
            (dow.from..dow.to).toSet() == weekdays -> "on weekdays"
            else -> "${dayNames[dow.from]} through ${dayNames[dow.to]}"
        }
        is Field.Listed -> when (dow.values.toSet()) {
            weekdays -> "on weekdays"
            weekend -> "on weekends"
            else -> "on ${joinNatural(dow.values.map { dayNames[it] })}"
        }
        is Field.Step -> "every ${ordinal(dow.step)} day of the week"
        Field.Every -> null
    }

    private fun domClause(dom: Field): String? = when (dom) {
        is Field.Exact -> "on the ${ordinal(dom.value)} of the month"
        is Field.Range -> "on the ${ordinal(dom.from)} through ${ordinal(dom.to)} of the month"
        is Field.Listed -> "on the ${joinNatural(dom.values.map { ordinal(it) })} of the month"
        is Field.Step -> "on every ${ordinal(dom.step)} day of the month"
        Field.Every -> null
    }

    private fun monthClause(mon: Field): String? = when (mon) {
        is Field.Exact -> "only in ${monthNames[mon.value - 1]}"
        is Field.Range -> "from ${monthNames[mon.from - 1]} through ${monthNames[mon.to - 1]}"
        is Field.Listed -> "only in ${joinNatural(mon.values.map { monthNames[it - 1] })}"
        is Field.Step -> "every ${ordinal(mon.step)} month"
        Field.Every -> null
    }

    private fun minuteList(min: Field): String = when (min) {
        is Field.Listed -> joinNatural(min.values.map { "minute $it" })
        is Field.Exact -> "minute ${min.value}"
        else -> "minute"
    }

    /** 12-hour clock with AM/PM, e.g. 14,5,0 -> "2:05 PM"; seconds shown only when non-zero. */
    private fun formatTime(hour: Int, minute: Int, second: Int): String {
        val period = if (hour < 12) "AM" else "PM"
        val twelve = ((hour + 11) % 12) + 1
        val secs = if (second != 0) ":%02d".format(second) else ""
        return "%d:%02d%s %s".format(twelve, minute, secs, period)
    }

    /** Just the hour on a 12-hour clock, e.g. 9 -> "9:00 AM". */
    private fun clock(hour: Int): String = formatTime(hour, 0, 0)

    /** "minute"/"15 minutes" — drops the count when it is 1 (for "Every minute"). */
    private fun units(step: Int, noun: String): String = if (step == 1) noun else "$step ${noun}s"

    /** "1 minute"/"5 minutes" — always keeps the count (for "5 minutes past the hour"). */
    private fun count(n: Int, noun: String): String = if (n == 1) "1 $noun" else "$n ${noun}s"

    private fun ordinal(n: Int): String {
        val suffix = if (n % 100 in 11..13) "th" else when (n % 10) {
            1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th"
        }
        return "$n$suffix"
    }

    private fun joinNatural(items: List<String>): String = when (items.size) {
        0 -> ""
        1 -> items[0]
        else -> items.dropLast(1).joinToString(", ") + " and " + items.last()
    }
}
