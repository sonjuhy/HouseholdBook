package com.budget.householdbook.domain.usecase

import com.budget.householdbook.core.database.AppDatabase
import com.budget.householdbook.data.local.entity.CategoryEntity
import com.budget.householdbook.data.local.entity.LedgerSpaceEntity
import com.budget.householdbook.data.local.entity.TransactionEntity
import com.budget.householdbook.domain.model.SpaceType
import com.budget.householdbook.domain.model.SyncStatus
import com.budget.householdbook.domain.model.TransactionType
import com.budget.householdbook.domain.repository.ExportFileWriter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ExportTransactionsCsvUseCaseTest {

    private val zone = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 11, 22)
    private val shared = AppDatabase.SPACE_ID_SHARED
    private val personal = AppDatabase.SPACE_ID_PERSONAL

    private val spaces = FakeLedgerSpaceRepository(
        listOf(
            LedgerSpaceEntity(id = shared, name = "공통 생활비", type = SpaceType.SHARED, billingStartDay = 1),
            // 개인 용돈은 25일 기준: 11.22의 이번 주기는 10.25 ~ 11.24
            LedgerSpaceEntity(id = personal, name = "내 용돈", type = SpaceType.PERSONAL, billingStartDay = 25)
        )
    )
    private val categories = FakeCategoryRepository(
        listOf(
            CategoryEntity(id = 1, spaceId = shared, name = "식비"),
            CategoryEntity(id = 2, spaceId = shared, parentId = 1, name = "배달/외식"),
            CategoryEntity(id = 3, spaceId = shared, name = "예전 분류", isArchived = true),
            CategoryEntity(id = 5, spaceId = shared, transactionType = TransactionType.INCOME, name = "생활비 입금"),
            CategoryEntity(id = 10, spaceId = personal, name = "취미")
        )
    )

    private class FakeWriter : ExportFileWriter {
        val written = mutableMapOf<String, String>()
        override suspend fun writeText(uri: String, text: String) {
            written[uri] = text
        }
    }

    private val writer = FakeWriter()

    private fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0) =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    private fun tx(
        id: Long,
        date: Long,
        amount: Long = 10_000,
        categoryId: Long = 1,
        subCategoryId: Long? = null,
        spaceId: String = shared,
        type: TransactionType = TransactionType.EXPENSE,
        title: String = "가게$id",
        memo: String = "",
        canceled: Boolean = false
    ) = TransactionEntity(
        id = id, remoteId = "r$id", spaceId = spaceId, type = type, amount = amount, categoryId = categoryId,
        subCategoryId = subCategoryId, merchantOrTitle = title, memo = memo, isCanceled = canceled, transactionDate = date
    )

    private fun useCase(transactions: List<TransactionEntity>) =
        ExportTransactionsCsvUseCase(spaces, FakeTransactionRepository(transactions), categories, writer)

    private fun lines(csv: String) = csv.removePrefix("﻿").split("\r\n").filter { it.isNotEmpty() }

    @Test
    fun `고른 주기 범위의 내역만 오래된 순서로 저장하고 분류와 유형 이름을 적는다`() = runTest {
        val export = useCase(
            listOf(
                tx(1, at(2026, 8, 31, 23, 59)), // 범위 밖 (9월 전)
                tx(2, at(2026, 11, 3), amount = 25_000, subCategoryId = 2, title = "배민", memo = "야식"),
                tx(3, at(2026, 9, 1, 0, 0), amount = 3_000_000, categoryId = 5, type = TransactionType.INCOME, title = "월급"),
                tx(4, at(2026, 10, 10), categoryId = 3, canceled = true),
                tx(5, at(2026, 12, 1)), // 범위 밖 (12월)
                tx(6, at(2026, 10, 5)).copy(syncStatus = SyncStatus.PENDING_DELETE)
            )
        )

        val result = export("content://a", listOf(shared), fromMonthOffset = -2, toMonthOffset = 0, today = today, zoneId = zone)

        assertEquals(3, result.rowCount)
        val csv = writer.written.getValue("content://a")
        assertTrue("엑셀용 BOM", csv.startsWith("﻿"))
        assertEquals(
            listOf(
                "날짜,시간,장부,유형,대분류,세부분류,내용,금액,메모,작성자,취소",
                "2026-09-01,00:00,공통 생활비,수입,생활비 입금,,월급,3000000,,나,",
                "2026-10-10,12:00,공통 생활비,지출,예전 분류,,가게4,10000,,나,취소",
                "2026-11-03,12:00,공통 생활비,지출,식비,배달/외식,배민,25000,야식,나,"
            ),
            lines(csv)
        )
    }

    @Test
    fun `전체 장부는 장부마다 자기 월 기준일로 기간을 나눈다`() = runTest {
        val export = useCase(
            listOf(
                tx(1, at(2026, 11, 1)),
                tx(2, at(2026, 11, 24), spaceId = personal, categoryId = 10, title = "보드게임"), // 25일 기준 이번 주기
                tx(3, at(2026, 11, 25), spaceId = personal, categoryId = 10), // 25일 기준 다음 주기
                tx(4, at(2026, 10, 31)), // 1일 기준 지난 주기
                tx(5, at(2026, 10, 25), spaceId = personal, categoryId = 10, title = "용돈 첫날") // 25일 기준 이번 주기 첫날
            )
        )

        val result = export("content://b", listOf(shared, personal), fromMonthOffset = 0, toMonthOffset = 0, today = today, zoneId = zone)

        assertEquals(3, result.rowCount)
        val body = lines(writer.written.getValue("content://b")).drop(1)
        assertEquals(listOf("용돈 첫날", "가게1", "보드게임"), body.map { it.split(",")[6] })
        assertEquals(listOf("내 용돈", "공통 생활비", "내 용돈"), body.map { it.split(",")[2] })
    }

    @Test
    fun `시작과 끝을 거꾸로 넘겨도 같은 범위로 저장하고, 내역이 없으면 제목 줄만 저장한다`() = runTest {
        val export = useCase(listOf(tx(1, at(2026, 10, 15))))

        assertEquals(1, export("content://c", listOf(shared), fromMonthOffset = 0, toMonthOffset = -1, today = today, zoneId = zone).rowCount)
        assertEquals(0, export("content://d", listOf(shared), fromMonthOffset = -6, toMonthOffset = -4, today = today, zoneId = zone).rowCount)
        assertEquals(1, lines(writer.written.getValue("content://d")).size)
    }

    @Test
    fun `쉼표 따옴표 줄바꿈은 따옴표로 감싸고 수식처럼 보이는 글자는 막는다`() {
        assertEquals("\"스타벅스, 강남점\"", TransactionCsv.escape("스타벅스, 강남점"))
        assertEquals("\"그는 \"\"안녕\"\"\"", TransactionCsv.escape("그는 \"안녕\""))
        assertEquals("\"첫 줄\n둘째 줄\"", TransactionCsv.escape("첫 줄\n둘째 줄"))
        assertEquals("'=HYPERLINK(1)", TransactionCsv.escape("=HYPERLINK(1)"))
        assertEquals("'+82", TransactionCsv.escape("+82"))
        assertEquals("'@멘션", TransactionCsv.escape("@멘션"))
        assertEquals("\"'-1,2\"", TransactionCsv.escape("-1,2"))
        assertEquals("25000", TransactionCsv.escape("25000"))
        assertEquals("", TransactionCsv.escape(""))
    }

    @Test
    fun `파일 이름은 장부와 시작 끝 달로 만들고 파일에 못 쓰는 글자는 뺀다`() {
        assertEquals("가계부_공통 생활비_2026-09_2026-11.csv", TransactionCsv.fileName("공통 생활비", "2026-09", "2026-11"))
        assertEquals("가계부_전체_2026-11.csv", TransactionCsv.fileName("전체", "2026-11", "2026-11"))
        assertEquals("가계부_우리집생활비_2026-11.csv", TransactionCsv.fileName("우리집/생활비?", "2026-11", "2026-11"))
        assertEquals("가계부_2026-11.csv", TransactionCsv.fileName(" ", "2026-11", "2026-11"))
        // 25일 기준: 11.22는 10.25에 시작한 주기
        assertEquals("2026-10", TransactionCsv.monthOf(billingStartDay = 25, monthOffset = 0, today = today))
        assertEquals("2026-11", TransactionCsv.monthOf(billingStartDay = 1, monthOffset = 0, today = today))
    }
}
