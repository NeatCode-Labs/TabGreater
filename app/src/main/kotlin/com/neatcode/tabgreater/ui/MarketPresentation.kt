package com.neatcode.tabgreater.ui

import com.neatcode.tabgreater.core.model.Market
import com.neatcode.tabgreater.core.model.InstrumentKind
import java.math.BigDecimal
import java.text.DateFormat
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/** Human-facing identity for search, charts and watchlist tiles. */
internal fun Market.displayLabel(): String = displayName?.takeIf(String::isNotBlank) ?: key.pair

/** Event identity for a watchlist card: outcome first, contract prompt next, settlement time last. */
internal fun Market.cardDisplayLabel(): String {
    val label = displayLabel().trim()
    if (instrumentKind != InstrumentKind.OUTCOME) return label

    val side = outcomeSide?.trim()?.takeIf(String::isNotEmpty)
    val prompt = side?.let { label.removeOutcomeSide(it) } ?: label
    val fields = description.eventFields()
    val isPriceContract = fields["class"].equals("priceBinary", ignoreCase = true) ||
        fields["class"].equals("priceBucket", ignoreCase = true)
    val sourceTime = fields.letOrNull("time")
    val parsedTime = sourceTime?.let(::parseEventTime)
    val relation = prompt.timeRelation(sourceTime, parsedTime)
    val isTouch = parsedTime != null && Regex("(?i)\\b(?:touches|touch|touched)\\b").containsMatchIn(prompt)
    val useSourceTime = parsedTime != null && (relation != null || prompt.containsEventTime(parsedTime) || isTouch)
    val sourceSegments = prompt.split(" · ").map(String::trim).filter(String::isNotEmpty)
    val embeddedDate = sourceSegments.lastOrNull(::isEventDateSegment)
    val protocolExpiry = fields.letOrNull("expiry")?.let(::parseEventTime)
    val eventEpoch = when {
        useSourceTime -> parsedTime
        isPriceContract -> protocolExpiry ?: expiresAt
        else -> null
    }
    val settlementDate = eventEpoch?.let(::formatEventDate)
    val replacementDate = settlementDate ?: embeddedDate
    val cleanedPrompt = if (useSourceTime && parsedTime != null) prompt.removeMachineTime(parsedTime) else prompt
    val segments = cleanedPrompt.split(" · ").map(String::trim).filter(String::isNotEmpty)
    val core = segments.filterNot { replacementDate != null && isEventDateSegment(it) }
        .filterNot { isPriceContract && replacementDate != null && isRedundantPeriod(it) }

    val contract = when {
        fields["class"].equals("priceBinary", ignoreCase = true) -> binaryContract(fields, core)
        fields["class"].equals("priceBucket", ignoreCase = true) -> bucketContract(fields, core)
        isTouch -> touchContract(fields, core)
        else -> null
    }
    val selectedParticipant = fields.letOrNull("participant")
        ?: core.lastOrNull()
    val matchup = confirmedMatchup(fields, selectedParticipant, core)
    val titleParts = when {
        contract != null -> listOf(contract)
        matchup != null -> listOf(matchup.selected)
        else -> genericEventTitle(core, selectedParticipant)
    }
    val headlineText = titleParts.firstOrNull()?.takeIf(String::isNotBlank) ?: key.pair
    val headline = side?.let { "$it · $headlineText" } ?: headlineText

    val dateDetail = when {
        isPriceContract && replacementDate != null -> "Ends $replacementDate"
        settlementDate == null -> embeddedDate
        relation != null -> "${relation.replaceFirstChar(Char::uppercase)} $settlementDate"
        else -> settlementDate
    }
    val matchupDetail = matchup?.let { "vs ${it.opponent}" }
    val genericDetail = titleParts.drop(1).filter(String::isNotBlank)
    val subtitle = listOfNotNull(matchupDetail, genericDetail.joinToString(" · ").takeIf(String::isNotBlank), dateDetail)
        .joinToString(" · ")
        .takeIf(String::isNotBlank)
    return listOfNotNull(headline, subtitle).joinToString("\n")
}

private fun String.removeOutcomeSide(side: String): String {
    val suffix = " · $side"
    val prefix = "$side · "
    return when {
        endsWith(suffix, ignoreCase = true) -> dropLast(suffix.length)
        startsWith(prefix, ignoreCase = true) -> drop(prefix.length)
        equals(side, ignoreCase = true) -> ""
        else -> this
    }.trim()
}

private fun Map<String, String>.letOrNull(key: String): String? = get(key.lowercase(Locale.ROOT))?.takeIf(String::isNotBlank)

private fun String?.eventFields(): Map<String, String> {
    if (isNullOrBlank()) return emptyMap()
    val field = Regex("(?:^|[|·])\\s*([A-Za-z][A-Za-z0-9_]*)\\s*:\\s*([^|·]+)")
    val fields = linkedMapOf<String, String>()
    field.findAll(this).forEach { match ->
        val key = match.groupValues[1].lowercase(Locale.ROOT)
        val value = match.groupValues[2].trim()
        if (value.isNotEmpty() && key !in fields) fields[key] = value
    }
    return fields
}

private fun binaryContract(fields: Map<String, String>, fallback: List<String>): String? {
    val underlying = fields.letOrNull("underlying") ?: fields.letOrNull("asset")
    val target = fields.letOrNull("targetprice") ?: fields.letOrNull("target")
    if (underlying != null && target != null) {
        val pair = if ('/' in underlying) underlying else "$underlying/USD"
        return "$pair ≥ ${formatThreshold(target)}"
    }
    return fallback.firstOrNull()?.let { normalizeLegacyBinary(it) }
}

private fun normalizeLegacyBinary(value: String): String {
    val binary = Regex("^([^\\s]+)\\s+above\\s+(\\$[0-9][0-9,]*(?:\\.[0-9]+)?)$", RegexOption.IGNORE_CASE)
        .matchEntire(value) ?: return value
    val pair = binary.groupValues[1].let { if ('/' in it) it else "$it/USD" }
    return "$pair ≥ ${binary.groupValues[2].removePrefix("\$")}"
}

private fun bucketContract(fields: Map<String, String>, fallback: List<String>): String? {
    val underlying = fields.letOrNull("underlying") ?: fields.letOrNull("asset")
    val thresholds = fields.letOrNull("pricethresholds")?.split(',')?.map(String::trim).orEmpty()
    val index = fields.letOrNull("index")?.toIntOrNull()
    if (underlying != null && thresholds.isNotEmpty() && index != null) {
        val pair = if ('/' in underlying) underlying else "$underlying/USD"
        return when (index) {
            0 -> "$pair below ${formatThreshold(thresholds.first())}"
            in 1 until thresholds.size -> "$pair from ${formatThreshold(thresholds[index - 1])} to below ${formatThreshold(thresholds[index])}"
            thresholds.size -> "$pair ≥ ${formatThreshold(thresholds.last())}"
            else -> "$pair other price bucket"
        }
    }
    return fallback.firstOrNull()?.let(::normalizeLegacyBucket)
}

private fun touchContract(fields: Map<String, String>, fallback: List<String>): String? {
    val prompt = fallback.firstOrNull() ?: return null
    val touch = Regex("\\b(touches|touch|touched)\\b", RegexOption.IGNORE_CASE)
        .find(prompt) ?: return null
    val underlying = fields.letOrNull("underlying") ?: fields.letOrNull("asset") ?: return prompt
    val target = fields.letOrNull("targetprice") ?: fields.letOrNull("target") ?: return prompt
    val pair = if ('/' in underlying) underlying else "$underlying/USD"
    return "$pair ${touch.value.lowercase(Locale.ROOT)} ${formatThreshold(target)}"
}

private fun normalizeLegacyBucket(value: String): String {
    val bucket = Regex("^(.+?)\\s+price\\s+between\\s+(.+?)\\s+and\\s+(.+)$", RegexOption.IGNORE_CASE)
        .matchEntire(value) ?: return value
    return "${bucket.groupValues[1]} price from ${bucket.groupValues[2]} to below ${bucket.groupValues[3]}"
}

private fun formatThreshold(source: String): String {
    val raw = source.trim()
    val dollar = raw.startsWith('$')
    val number = raw.removePrefix("\$").replace(",", "")
    val plain = if (Regex("[-+]?\\d+(?:\\.\\d+)?").matches(number)) number
    else runCatching { BigDecimal(number).toPlainString() }.getOrNull()
        ?: return if (dollar) raw else "\$$raw"
    val negative = plain.startsWith('-')
    val unsigned = plain.removePrefix("-").removePrefix("+")
    val integer = unsigned.substringBefore('.')
    val fraction = unsigned.substringAfter('.', "")
    val grouped = integer.reversed().chunked(3).joinToString(",").reversed()
    val sign = if (negative) "-" else ""
    val normalized = sign + grouped + if (fraction.isEmpty()) "" else ".$fraction"
    return normalized
}

private data class ConfirmedMatchup(val selected: String, val opponent: String)

private fun confirmedMatchup(
    fields: Map<String, String>,
    selectedLabel: String?,
    prompt: List<String>,
): ConfirmedMatchup? {
    val selected = fields.letOrNull("participant") ?: selectedLabel ?: return null
    val first = fields.letOrNull("participanta")
    val second = fields.letOrNull("participantb")
    val pair = if (first != null && second != null) {
        first to second
    } else {
        prompt.asSequence().mapNotNull(::parseMatchup).firstOrNull() ?: return null
    }
    return when {
        sameParticipant(selected, pair.first) -> ConfirmedMatchup(selected, pair.second)
        sameParticipant(selected, pair.second) -> ConfirmedMatchup(selected, pair.first)
        else -> null
    }
}

private fun parseMatchup(value: String): Pair<String, String>? {
    val match = Regex("^(.+?)\\s+(?:v|vs\\.?|versus)\\s+(.+?)(?:\\s*[:—|].*)?$", RegexOption.IGNORE_CASE)
        .matchEntire(value.trim()) ?: return null
    val first = match.groupValues[1].substringAfterLast(':').trim()
    val second = match.groupValues[2].trim()
    return (first to second).takeIf { it.first.isNotBlank() && it.second.isNotBlank() }
}

private fun sameParticipant(first: String, second: String): Boolean {
    val left = normalizeParticipant(first)
    val right = normalizeParticipant(second)
    return left.isNotEmpty() && right.isNotEmpty() && left == right
}

private fun normalizeParticipant(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")
        .lowercase(Locale.ROOT)

private fun String.timeRelation(sourceTime: String?, parsedTime: Long?): String? {
    if (sourceTime.isNullOrBlank() || parsedTime == null) return null
    return RELATIVE_EVENT_TIME_PATTERN.findAll(this)
        .firstOrNull { parseEventTime(it.groupValues[2]) == parsedTime }
        ?.groupValues?.get(1)
        ?.lowercase(Locale.ROOT)
}

private fun String.containsEventTime(epochMs: Long): Boolean =
    EVENT_TIME_TOKEN.findAll(this).any { parseEventTime(it.value) == epochMs }

private fun genericEventTitle(core: List<String>, participant: String?): List<String> {
    if (core.isEmpty()) return emptyList()
    val selected = participant?.trim()?.takeIf(String::isNotEmpty)
    if (selected == null) return listOf(core.joinToString(" · "))
    val context = core.filterNot { it.equals(selected, ignoreCase = true) }
    return if (context.isEmpty()) listOf(selected) else listOf(selected, context.joinToString(" · "))
}

private fun String.removeMachineTime(parsedTime: Long): String {
    return split(" · ").joinToString(" · ") { segment ->
        EVENT_TIME_TOKEN.findAll(segment).toList().asReversed().fold(segment) { text, match ->
            if (parseEventTime(match.value) != parsedTime) {
                text
            } else {
                val relative = Regex("(?i)\\b(by|at|on|before|after|until|through)\\s*$")
                    .find(text.substring(0, match.range.first))
                val start = relative?.range?.first ?: match.range.first
                (text.substring(0, start) + text.substring(match.range.last + 1))
                    .trim().trimEnd(',', ';', ':', '—', '-')
            }
        }
    }.trim(' ', '·')
}

private fun parseEventTime(value: String): Long? {
    val raw = value.trim()
    raw.toLongOrNull()?.let { numeric ->
        return if (numeric in 1 until 100_000_000_000L) numeric * 1_000L else numeric
    }
    return runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(raw, COMPACT_EVENT_TIME).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(raw, COMPACT_EVENT_TIME_WITH_SECONDS).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(raw, READABLE_EVENT_TIME).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(raw, READABLE_EVENT_TIME_WITH_SECONDS).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
}

private fun isEventDateSegment(value: String): Boolean = EVENT_DATE_PATTERN.containsMatchIn(value)

private fun isRedundantPeriod(value: String): Boolean =
    REDUNDANT_PERIOD_PATTERN.matches(value.trim())

private fun formatEventDate(epochMs: Long): String {
    val instant = Instant.ofEpochMilli(epochMs)
    val format = if (instant.atZone(ZoneOffset.UTC).second == 0) EVENT_DATE_FORMAT else EVENT_DATE_FORMAT_WITH_SECONDS
    return format.format(instant)
}

private val COMPACT_EVENT_TIME = DateTimeFormatter.ofPattern("uuuuMMdd-HHmm", Locale.ROOT)
private val COMPACT_EVENT_TIME_WITH_SECONDS = DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss", Locale.ROOT)
private val READABLE_EVENT_TIME = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm 'UTC'", Locale.US)
private val READABLE_EVENT_TIME_WITH_SECONDS = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss 'UTC'", Locale.US)
private const val EVENT_TIME_TOKEN_PATTERN = "(?:\\d{8}-\\d{4}(?:\\d{2})?|\\d{10,13}|\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(?::\\d{2})?Z?|(?:Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\\s+\\d{1,2},?\\s+\\d{4}\\s+\\d{1,2}:\\d{2}(?::\\d{2})?\\s*(?:UTC|GMT))"
private val EVENT_TIME_TOKEN = Regex(EVENT_TIME_TOKEN_PATTERN, RegexOption.IGNORE_CASE)
private val RELATIVE_EVENT_TIME_PATTERN = Regex("(?i)\\b(by|at|on|before|after|until|through)\\s+($EVENT_TIME_TOKEN_PATTERN)")

private val EVENT_DATE_PATTERN = Regex(
    "(?i)^\\s*(?:Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\\s+\\d{1,2},?\\s+\\d{4}\\s+\\d{1,2}:\\d{2}(?::\\d{2})?\\s*(?:UTC|GMT)?\\s*$",
)
private val REDUNDANT_PERIOD_PATTERN = Regex("(?i)\\d+\\s*[dhw]\\s+period")
private val EVENT_DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy HH:mm 'UTC'", Locale.US).withZone(ZoneOffset.UTC)
private val EVENT_DATE_FORMAT_WITH_SECONDS: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss 'UTC'", Locale.US).withZone(ZoneOffset.UTC)

/** Contract kind, quote unit and event deadline, when the source provides them. */
internal fun Market.contextLabel(): String = buildList {
    add(instrumentKind.label)
    outcomeSide?.takeIf(String::isNotBlank)?.let(::add)
    venue?.takeIf(String::isNotBlank)?.let(::add)
    unit?.takeIf(String::isNotBlank)?.let(::add)
    expiresAt?.let { expiry ->
        add("Deadline ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(expiry))}")
    }
    if (!active) add("Inactive")
}.joinToString(" · ")
