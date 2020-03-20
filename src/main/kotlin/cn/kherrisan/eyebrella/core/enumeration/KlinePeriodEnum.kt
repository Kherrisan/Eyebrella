package cn.kherrisan.eyebrella.core.enumeration

import java.time.Duration
import java.time.Period

typealias CalendarScale = Int

const val SECOND = 13
const val MINUTE = 12
const val HOUR = 11
const val DAY = 6

enum class KlinePeriodEnum(val period: Period = Period.ZERO, val duration: Duration = Duration.ZERO) {
    _1MIN(duration = Duration.ofMinutes(1)),
    _3MIN(duration = Duration.ofMinutes(3)),
    _5MIN(duration = Duration.ofMinutes(5)),
    _15MIN(duration = Duration.ofMinutes(15)),
    _30MIN(duration = Duration.ofMinutes(30)),
    _60MIN(duration = Duration.ofMinutes(60)),
    _2HOUR(duration = Duration.ofHours(2)),
    _3HOUR(duration = Duration.ofHours(3)),
    _4HOUR(duration = Duration.ofHours(4)),
    _6HOUR(duration = Duration.ofHours(6)),
    _8HOUR(duration = Duration.ofHours(8)),
    _12HOUR(duration = Duration.ofHours(12)),
    _1DAY(duration = Duration.ofDays(1)),
    _3DAY(duration = Duration.ofDays(3)),
    _1WEEK(Period.ofDays(7)),
    _2WEEK(Period.ofDays(14)),
    _1MON(Period.ofMonths(1)),
    _1YEAR(Period.ofYears(1));

    fun toSeconds(): Long {
        return duration.toSeconds() + period.days * 24 * 3600
    }
}