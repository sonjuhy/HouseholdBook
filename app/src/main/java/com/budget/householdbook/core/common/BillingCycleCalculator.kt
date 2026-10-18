package com.budget.householdbook.core.common

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * 정산 기준일(billingStartDay) 기반의 한 달 주기 범위 정보
 */
data class BillingPeriod(
    val startDate: LocalDate,
    val endDateInclusive: LocalDate,
    val startMillis: Long,
    val endExclusiveMillis: Long,
    val totalDays: Int,
    val remainingDays: Int,
    val periodLabel: String
)

/**
 * 사용자 지정 월 기준일(기본 매월 1일 ~ 말일, 또는 2~28일/말일 선택)을 기준으로
 * 동적 기간 범위(startMillis ~ endExclusiveMillis)를 계산하는 유틸리티
 */
object BillingCycleCalculator {

    /** 매월 말일을 정산 시작일로 쓰는 특수 값 */
    const val LAST_DAY_OF_MONTH = 99

    /** 저장 가능한 기준일로 보정 (1..28 또는 말일 99) */
    fun sanitizeStartDay(day: Int): Int =
        if (day == LAST_DAY_OF_MONTH) LAST_DAY_OF_MONTH else day.coerceIn(1, 28)

    /** 화면 표기용: 25 -> "25일", 99 -> "말일" */
    fun startDayLabel(day: Int): String =
        if (day == LAST_DAY_OF_MONTH) "말일" else "${day.coerceIn(1, 28)}일"

    private val shortFormatter = DateTimeFormatter.ofPattern("M.d", Locale.KOREA)
    private val monthTitleFormatter = DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREA)

    /**
     * @param baseDate 기준 날짜 (오늘 날짜 기준)
     * @param billingStartDay 월 정산 시작일 (1..28 또는 말일 [LAST_DAY_OF_MONTH], 기본값 1)
     * @param monthOffset 0이면 현재 주기, -1이면 이전 주기, +1이면 다음 주기
     */
    fun calculatePeriod(
        baseDate: LocalDate = LocalDate.now(),
        billingStartDay: Int = 1,
        monthOffset: Int = 0,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): BillingPeriod {
        val safeStartDay = sanitizeStartDay(billingStartDay)

        val currentCycleStart: LocalDate = if (safeStartDay == LAST_DAY_OF_MONTH) {
            // 말일 기준: 오늘이 말일이면 오늘부터, 아니면 지난달 말일부터 시작
            val thisMonthEnd = YearMonth.from(baseDate).atEndOfMonth()
            if (baseDate == thisMonthEnd) thisMonthEnd else YearMonth.from(baseDate).minusMonths(1).atEndOfMonth()
        } else if (safeStartDay == 1) {
            baseDate.withDayOfMonth(1)
        } else {
            if (baseDate.dayOfMonth >= safeStartDay) {
                baseDate.withDayOfMonth(safeStartDay)
            } else {
                baseDate.minusMonths(1).withDayOfMonth(safeStartDay)
            }
        }

        // 말일 기준은 달마다 일수가 달라 plusMonths 대신 매달의 말일로 다시 계산
        val targetStart = if (safeStartDay == LAST_DAY_OF_MONTH) {
            YearMonth.from(currentCycleStart).plusMonths(monthOffset.toLong()).atEndOfMonth()
        } else {
            currentCycleStart.plusMonths(monthOffset.toLong())
        }
        val targetEndExclusive = if (safeStartDay == LAST_DAY_OF_MONTH) {
            YearMonth.from(targetStart).plusMonths(1).atEndOfMonth()
        } else if (safeStartDay == 1) {
            YearMonth.from(targetStart).plusMonths(1).atDay(1)
        } else {
            targetStart.plusMonths(1)
        }
        val targetEndInclusive = targetEndExclusive.minusDays(1)

        val startMillis = targetStart.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endExclusiveMillis = targetEndExclusive.atStartOfDay(zoneId).toInstant().toEpochMilli()

        val totalDays = ChronoUnit.DAYS.between(targetStart, targetEndExclusive).toInt().coerceAtLeast(1)
        val remainingDays = when {
            baseDate.isBefore(targetStart) -> totalDays
            baseDate.isAfter(targetEndInclusive) -> 0
            else -> (ChronoUnit.DAYS.between(baseDate, targetEndExclusive).toInt()).coerceAtLeast(1)
        }

        val label = "${targetStart.format( monthTitleFormatter )} (${targetStart.format(shortFormatter)} ~ ${targetEndInclusive.format(shortFormatter)})"

        return BillingPeriod(
            startDate = targetStart,
            endDateInclusive = targetEndInclusive,
            startMillis = startMillis,
            endExclusiveMillis = endExclusiveMillis,
            totalDays = totalDays,
            remainingDays = remainingDays,
            periodLabel = label
        )
    }
}

/**
 * 한국 원화(KRW) 및 일시 포맷터
 */
object CurrencyAndDateFormatter {
    private val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일 HH:mm", Locale.KOREA)
    private val dateOnlyFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.KOREA)

    fun formatWon(amount: Long): String = "${numberFormat.format(amount)}원"

    fun formatSignedWon(amount: Long, isIncome: Boolean): String {
        val prefix = if (isIncome) "+" else "-"
        return "$prefix${numberFormat.format(kotlin.math.abs(amount))}원"
    }

    fun formatDateTime(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val zdt = Instant.ofEpochMilli(epochMillis).atZone(zoneId)
        return zdt.format(dateTimeFormatter)
    }

    fun formatDateIso(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val zdt = Instant.ofEpochMilli(epochMillis).atZone(zoneId)
        return zdt.format(dateOnlyFormatter)
    }
}
