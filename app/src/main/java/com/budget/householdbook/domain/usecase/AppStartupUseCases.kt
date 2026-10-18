package com.budget.householdbook.domain.usecase

import com.budget.householdbook.core.common.CurrencyAndDateFormatter
import com.budget.householdbook.core.database.AppDatabase
import com.budget.householdbook.data.local.entity.CategoryEntity
import com.budget.householdbook.data.local.entity.LedgerSpaceEntity
import com.budget.householdbook.data.local.entity.MealSavingLogEntity
import com.budget.householdbook.data.local.entity.MerchantCategoryRuleEntity
import com.budget.householdbook.data.local.entity.PendingTransactionEntity
import com.budget.householdbook.data.local.entity.TransactionEntity
import com.budget.householdbook.domain.model.IngestionSource
import com.budget.householdbook.domain.model.MealType
import com.budget.householdbook.domain.model.QueueKind
import com.budget.householdbook.domain.model.SpaceType
import com.budget.householdbook.domain.model.TransactionType
import com.budget.householdbook.domain.repository.AutomationRuleRepository
import com.budget.householdbook.domain.repository.CategoryRepository
import com.budget.householdbook.domain.repository.LedgerSpaceRepository
import com.budget.householdbook.domain.repository.MealRepository
import com.budget.householdbook.domain.repository.PendingQueueRepository
import com.budget.householdbook.domain.repository.SettingsRepository
import com.budget.householdbook.domain.repository.TransactionRepository
import com.budget.householdbook.domain.repository.TransactionRunner
import kotlinx.coroutines.flow.first
import java.util.UUID

/**
 * 앱 최초 실행 시 기본 공간(공통 생활비, 내 용돈), 상위/세부 카테고리,
 * 그리고 즉시 검수해볼 수 있는 샘플 거래·집밥 기록·대기 큐 항목을 시드합니다. (1회만 실행)
 */
class SeedInitialDataUseCase(
    private val transactionRunner: TransactionRunner,
    private val settingsRepository: SettingsRepository,
    private val spaceRepository: LedgerSpaceRepository,
    private val categoryRepository: CategoryRepository,
    private val ruleRepository: AutomationRuleRepository,
    private val transactionRepository: TransactionRepository,
    private val mealRepository: MealRepository,
    private val pendingQueueRepository: PendingQueueRepository
) {
    suspend operator fun invoke() {
        val settings = settingsRepository.userSettingsFlow.first()
        if (settings.isSeedCompleted) return

        transactionRunner.runInTransaction {
            val sharedId = AppDatabase.SPACE_ID_SHARED
            val personalId = AppDatabase.SPACE_ID_PERSONAL

            spaceRepository.insertSpacesIfEmpty(
                listOf(
                    LedgerSpaceEntity(
                        id = sharedId,
                        name = "우리집 공통 생활비",
                        type = SpaceType.SHARED,
                        billingStartDay = 1,
                        targetBudgetAmount = 1_500_000L,
                        memberNamesCsv = "나, 짝꿍",
                        inviteCode = null,
                        isRemoteShared = false
                    ),
                    LedgerSpaceEntity(
                        id = personalId,
                        name = "내 개인 용돈",
                        type = SpaceType.PERSONAL,
                        billingStartDay = 25,
                        targetBudgetAmount = 500_000L,
                        memberNamesCsv = "나",
                        inviteCode = null,
                        isRemoteShared = false
                    )
                )
            )

            // 공통 생활비 상위 & 세부 카테고리 시드
            val foodParentId = categoryRepository.insertCategory(
                CategoryEntity(spaceId = sharedId, name = "식비", emoji = "🍔", colorHex = "#EF4444", sortOrder = 1)
            )
            val grocSubId = categoryRepository.insertCategory(
                CategoryEntity(spaceId = sharedId, parentId = foodParentId, name = "식재료/장보기", emoji = "🥬", colorHex = "#EF4444", sortOrder = 1)
            )
            val delivSubId = categoryRepository.insertCategory(
                CategoryEntity(spaceId = sharedId, parentId = foodParentId, name = "배달/외식", emoji = "🛵", colorHex = "#EF4444", sortOrder = 2)
            )
            categoryRepository.insertCategory(
                CategoryEntity(spaceId = sharedId, parentId = foodParentId, name = "카페/베이커리", emoji = "☕", colorHex = "#EF4444", sortOrder = 3)
            )

            val livingParentId = categoryRepository.insertCategory(
                CategoryEntity(spaceId = sharedId, name = "주거/생활", emoji = "🏠", colorHex = "#2563EB", sortOrder = 2)
            )
            val lifeSubId = categoryRepository.insertCategory(
                CategoryEntity(spaceId = sharedId, parentId = livingParentId, name = "생필품/마트", emoji = "🧻", colorHex = "#2563EB", sortOrder = 1)
            )
            categoryRepository.insertCategory(
                CategoryEntity(spaceId = sharedId, parentId = livingParentId, name = "관리비/공과금", emoji = "💡", colorHex = "#2563EB", sortOrder = 2)
            )

            val sharedIncomeParentId = categoryRepository.insertCategory(
                CategoryEntity(
                    spaceId = sharedId,
                    transactionType = TransactionType.INCOME,
                    name = "생활비 입금",
                    emoji = "💰",
                    colorHex = "#3B82F6",
                    sortOrder = 10
                )
            )
            categoryRepository.insertCategory(
                CategoryEntity(
                    spaceId = sharedId,
                    parentId = sharedIncomeParentId,
                    transactionType = TransactionType.INCOME,
                    name = "정기 생활비 각출",
                    emoji = "🏦",
                    colorHex = "#3B82F6",
                    sortOrder = 1
                )
            )

            // 내 개인 용돈 상위 & 세부 카테고리 시드
            val allowanceFoodId = categoryRepository.insertCategory(
                CategoryEntity(spaceId = personalId, name = "식음료/간식", emoji = "☕", colorHex = "#059669", sortOrder = 1)
            )
            val coffeeSubId = categoryRepository.insertCategory(
                CategoryEntity(spaceId = personalId, parentId = allowanceFoodId, name = "커피/디저트", emoji = "🧋", colorHex = "#059669", sortOrder = 1)
            )
            categoryRepository.insertCategory(
                CategoryEntity(spaceId = personalId, parentId = allowanceFoodId, name = "점심/회식", emoji = "🍱", colorHex = "#059669", sortOrder = 2)
            )

            val hobbyParentId = categoryRepository.insertCategory(
                CategoryEntity(spaceId = personalId, name = "취미/쇼핑", emoji = "🎮", colorHex = "#8B5CF6", sortOrder = 2)
            )
            val shoppingSubId = categoryRepository.insertCategory(
                CategoryEntity(spaceId = personalId, parentId = hobbyParentId, name = "의류/패션", emoji = "👕", colorHex = "#8B5CF6", sortOrder = 1)
            )
            categoryRepository.insertCategory(
                CategoryEntity(spaceId = personalId, parentId = hobbyParentId, name = "문화/구독", emoji = "🎬", colorHex = "#8B5CF6", sortOrder = 2)
            )

            val personalIncomeId = categoryRepository.insertCategory(
                CategoryEntity(
                    spaceId = personalId,
                    transactionType = TransactionType.INCOME,
                    name = "급여/용돈수입",
                    emoji = "💵",
                    colorHex = "#10B981",
                    sortOrder = 10
                )
            )

            // 기본 가맹점 자동 분류 규칙 시드
            ruleRepository.upsertMerchantRule(
                MerchantCategoryRuleEntity(
                    merchantKeyword = "우아한형제들",
                    targetSpaceId = sharedId,
                    categoryId = foodParentId,
                    subCategoryId = delivSubId
                )
            )
            ruleRepository.upsertMerchantRule(
                MerchantCategoryRuleEntity(
                    merchantKeyword = "스타벅스",
                    targetSpaceId = personalId,
                    categoryId = allowanceFoodId,
                    subCategoryId = coffeeSubId
                )
            )

            val now = System.currentTimeMillis()

            // 초기 확정 거래 내역 시드 (예산 프로그레스 및 결제 취소 매칭 체험용)
            val coupangTxId = transactionRepository.insertTransaction(
                TransactionEntity(
                    remoteId = UUID.randomUUID().toString(),
                    spaceId = sharedId,
                    type = TransactionType.EXPENSE,
                    amount = 18_900L,
                    categoryId = livingParentId,
                    subCategoryId = lifeSubId,
                    merchantOrTitle = "쿠팡(로켓배송)",
                    memo = "주방세제 및 키친타올",
                    authorName = "나",
                    transactionDate = now - 86_400_000L
                )
            )
            transactionRepository.insertTransaction(
                TransactionEntity(
                    remoteId = UUID.randomUUID().toString(),
                    spaceId = sharedId,
                    type = TransactionType.EXPENSE,
                    amount = 145_000L,
                    categoryId = foodParentId,
                    subCategoryId = grocSubId,
                    merchantOrTitle = "이마트 트레이더스",
                    memo = "주말 장보기",
                    authorName = "짝꿍",
                    transactionDate = now - 172_800_000L
                )
            )
            transactionRepository.insertTransaction(
                TransactionEntity(
                    remoteId = UUID.randomUUID().toString(),
                    spaceId = personalId,
                    type = TransactionType.EXPENSE,
                    amount = 64_000L,
                    categoryId = hobbyParentId,
                    subCategoryId = shoppingSubId,
                    merchantOrTitle = "무신사 스토어",
                    memo = "가을 셔츠 구매",
                    authorName = "나",
                    transactionDate = now - 120_000_000L
                )
            )
            transactionRepository.insertTransaction(
                TransactionEntity(
                    remoteId = UUID.randomUUID().toString(),
                    spaceId = personalId,
                    type = TransactionType.INCOME,
                    amount = 500_000L,
                    categoryId = personalIncomeId,
                    subCategoryId = null,
                    merchantOrTitle = "10월 개인 용돈",
                    memo = "정기 용돈",
                    authorName = "나",
                    transactionDate = now - 200_000_000L
                )
            )

            // 초기 집밥 절약 카운트 시드 (2건)
            mealRepository.insertMealLog(
                MealSavingLogEntity(
                    spaceId = sharedId,
                    dateIso = CurrencyAndDateFormatter.formatDateIso(now - 86_400_000L),
                    mealType = MealType.DINNER,
                    count = 1,
                    unitAmount = 15_000L,
                    savedAmount = 15_000L,
                    memo = "김치찌개 & 계란말이 직접 요리",
                    occurredAt = now - 86_400_000L
                )
            )
            mealRepository.insertMealLog(
                MealSavingLogEntity(
                    spaceId = sharedId,
                    dateIso = CurrencyAndDateFormatter.formatDateIso(now),
                    mealType = MealType.LUNCH,
                    count = 1,
                    unitAmount = 10_000L,
                    savedAmount = 10_000L,
                    memo = "점심 도시락",
                    occurredAt = now - 14_400_000L
                )
            )

            // 초기 등록 대기 큐(PendingQueue) 시드 3건:
            // 1) 일반 배달 지출 (수정/저장 또는 공통+개인 분할 등록 테스트용)
            pendingQueueRepository.insertIgnoreDuplicate(
                PendingTransactionEntity(
                    sourceType = IngestionSource.APP_NOTIFICATION,
                    sourceAppName = "KB Pay(국민카드)",
                    rawText = "[KB Pay] 52,000원 일시불 승인 이마트에브리데이 (공통 장보기 + 개인 간식 분할 가능)",
                    parsedAmount = 52_000L,
                    parsedType = TransactionType.EXPENSE,
                    parsedMerchant = "이마트에브리데이",
                    occurredAt = now - 3_600_000L,
                    queueKind = QueueKind.NORMAL,
                    suggestedSpaceId = sharedId,
                    suggestedCategoryId = foodParentId,
                    suggestedSubCategoryId = grocSubId,
                    dedupHash = "seed_pending_emart_52000"
                )
            )
            // 2) 기존 결제 취소 자동 매칭 건 (어제 쿠팡 18,900원 결제와 매칭됨)
            pendingQueueRepository.insertIgnoreDuplicate(
                PendingTransactionEntity(
                    sourceType = IngestionSource.SMS,
                    sourceAppName = "문자(SMS)",
                    rawText = "[Web발신] KB국민카드 18,900원 승인취소 쿠팡(로켓배송)",
                    parsedAmount = 18_900L,
                    parsedType = TransactionType.EXPENSE,
                    parsedMerchant = "쿠팡(로켓배송)",
                    occurredAt = now - 1_800_000L,
                    queueKind = QueueKind.CANCEL_MATCHED,
                    matchedTransactionId = coupangTxId,
                    suggestedSpaceId = sharedId,
                    suggestedCategoryId = livingParentId,
                    suggestedSubCategoryId = lifeSubId,
                    dedupHash = "seed_pending_cancel_coupang_18900"
                )
            )
            // 3) 내 계좌 간 이체 / 카드대금 자동 분류 건
            pendingQueueRepository.insertIgnoreDuplicate(
                PendingTransactionEntity(
                    sourceType = IngestionSource.APP_NOTIFICATION,
                    sourceAppName = "토스(Toss)",
                    rawText = "[토스] 100,000원 출금 내 계좌(KB국민 홍길동)로 이체",
                    parsedAmount = 100_000L,
                    parsedType = TransactionType.TRANSFER,
                    parsedMerchant = "내 계좌(KB국민 홍길동) 이체",
                    occurredAt = now - 600_000L,
                    queueKind = QueueKind.TRANSFER_PAIR,
                    suggestedSpaceId = sharedId,
                    suggestedCategoryId = foodParentId,
                    suggestedSubCategoryId = null,
                    dedupHash = "seed_pending_transfer_100000"
                )
            )
        }

        settingsRepository.markSeedCompleted()
    }
}

/**
 * 앱 시작 시 1회: 초기 데이터 시드 -> 백그라운드 작업 예약 -> 함께 쓰기 실시간 연결
 */
class InitializeAppUseCase(
    private val seedInitialData: SeedInitialDataUseCase,
    private val ensureBackgroundWork: EnsureBackgroundWorkUseCase,
    private val syncOnAppStart: SyncOnAppStartUseCase
) {
    suspend operator fun invoke() {
        seedInitialData()
        ensureBackgroundWork()
        syncOnAppStart()
    }
}
