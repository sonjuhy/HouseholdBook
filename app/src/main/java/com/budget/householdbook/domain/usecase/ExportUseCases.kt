package com.budget.householdbook.domain.usecase

import com.budget.householdbook.core.common.BillingCycleCalculator
import com.budget.householdbook.data.local.entity.CategoryEntity
import com.budget.householdbook.data.local.entity.LedgerSpaceEntity
import com.budget.householdbook.data.local.entity.TransactionEntity
import com.budget.householdbook.domain.repository.CategoryRepository
import com.budget.householdbook.domain.repository.ExportFileWriter
import com.budget.householdbook.domain.repository.LedgerSpaceRepository
import com.budget.householdbook.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 장부 내역 CSV 내보내기
 * - 기간은 정산 주기(월 기준일) 한 달 단위: [fromMonthOffset] ~ [toMonthOffset] (0 = 이번 주기, -1 = 지난 주기)
 * - 장부마다 자기 월 기준일로 기간을 계산
 * - 엑셀에서 한글이 깨지지 않도록 UTF-8 BOM을 붙이고, 줄바꿈은 CRLF
 */
object TransactionCsv {

    val HEADER = listOf("날짜", "시간", "장부", "유형", "대분류", "세부분류", "내용", "금액", "메모", "작성자", "취소")

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.KOREA)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.KOREA)
    private val monthFormatter = DateTimeFormatter.ofPattern("yyyy-MM", Locale.KOREA)

    /** 정산 주기 offset의 시작 달 (파일 이름·화면 표기용) */
    fun monthOf(billingStartDay: Int, monthOffset: Int, today: LocalDate = LocalDate.now()): String =
        BillingCycleCalculator.calculatePeriod(baseDate = today, billingStartDay = billingStartDay, monthOffset = monthOffset)
            .startDate.format(monthFormatter)

    /** 예: 가계부_공통 생활비_2026-09_2026-11.csv, 한 달이면 가계부_공통 생활비_2026-11.csv */
    fun fileName(spaceLabel: String, fromMonth: String, toMonth: String): String {
        val safeLabel = spaceLabel.replace(Regex("[\\\\/:*?\"<>|]"), "").trim()
        val range = if (fromMonth == toMonth) fromMonth else "${fromMonth}_$toMonth"
        return if (safeLabel.isEmpty()) "가계부_$range.csv" else "가계부_${safeLabel}_$range.csv"
    }

    fun build(
        rows: List<TransactionEntity>,
        spacesById: Map<String, LedgerSpaceEntity>,
        categoriesById: Map<Long, CategoryEntity>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String = buildString {
        append('﻿')
        appendRow(HEADER)
        rows.forEach { tx ->
            val time = Instant.ofEpochMilli(tx.transactionDate).atZone(zoneId)
            appendRow(
                listOf(
                    time.format(dateFormatter),
                    time.format(timeFormatter),
                    spacesById[tx.spaceId]?.name ?: tx.spaceId,
                    tx.type.displayName,
                    categoriesById[tx.categoryId]?.name.orEmpty(),
                    tx.subCategoryId?.let { categoriesById[it]?.name }.orEmpty(),
                    tx.merchantOrTitle,
                    tx.amount.toString(),
                    tx.memo,
                    tx.authorName,
                    if (tx.isCanceled) "취소" else ""
                )
            )
        }
    }

    private fun StringBuilder.appendRow(cells: List<String>) {
        cells.joinTo(this, separator = ",") { escape(it) }
        append("\r\n")
    }

    /**
     * 쉼표·따옴표·줄바꿈이 있으면 따옴표로 감싸고,
     * 엑셀이 수식으로 실행하지 않도록 = + - @ 로 시작하는 글자는 앞에 ' 를 붙임 (금액 숫자는 그대로)
     */
    internal fun escape(raw: String): String {
        val isNumber = raw.isNotEmpty() && raw.all { it.isDigit() }
        val value = if (!isNumber && raw.isNotEmpty() && raw[0] in FORMULA_PREFIXES) "'$raw" else raw
        return if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }

    private val FORMULA_PREFIXES = setOf('=', '+', '-', '@', '\t', '\r')
}

/** 내보내기 결과 */
data class CsvExportResult(val rowCount: Int)

/**
 * 고른 장부·기간의 거래 내역을 CSV로 만들어 사용자가 고른 파일([uri])에 저장
 * - 휴대폰에서 지우는 중인 내역(PENDING_DELETE)은 빼고, 취소된 내역은 `취소` 열에 표시해 함께 넣음
 * - 숨긴 분류도 예전 거래의 분류 이름으로 표시
 */
class ExportTransactionsCsvUseCase(
    private val spaceRepository: LedgerSpaceRepository,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val fileWriter: ExportFileWriter
) {
    suspend operator fun invoke(
        uri: String,
        spaceIds: List<String>,
        fromMonthOffset: Int,
        toMonthOffset: Int,
        today: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): CsvExportResult {
        require(spaceIds.isNotEmpty()) { "내보낼 장부를 골라주세요." }
        val from = minOf(fromMonthOffset, toMonthOffset)
        val to = maxOf(fromMonthOffset, toMonthOffset)

        val spaces = spaceIds.distinct().mapNotNull { spaceRepository.getSpaceById(it) }
        val rows = spaces.flatMap { space ->
            val start = BillingCycleCalculator.calculatePeriod(today, space.billingStartDay, from, zoneId).startMillis
            val end = BillingCycleCalculator.calculatePeriod(today, space.billingStartDay, to, zoneId).endExclusiveMillis
            transactionRepository.observeTransactionsInPeriod(space.id, start, end).first()
        }.sortedWith(compareBy<TransactionEntity> { it.transactionDate }.thenBy { it.id })

        val categories = spaces.flatMap { categoryRepository.getAllBySpaceIncludingArchived(it.id) }
        val csv = TransactionCsv.build(
            rows = rows,
            spacesById = spaces.associateBy { it.id },
            categoriesById = categories.associateBy { it.id },
            zoneId = zoneId
        )
        fileWriter.writeText(uri, csv)
        return CsvExportResult(rowCount = rows.size)
    }
}
