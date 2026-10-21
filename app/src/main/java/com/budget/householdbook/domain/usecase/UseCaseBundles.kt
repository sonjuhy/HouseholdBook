package com.budget.householdbook.domain.usecase

/*
 * 화면(ViewModel)에서 사용하는 UseCase를 기능별로 묶은 번들. Koin이 각 UseCase를 주입해 생성합니다.
 */

/** 자동 수집 (예시 알림, [Beta] KB 빠른조회) */
data class IngestionUseCases(
    val ingestRawNotification: IngestRawNotificationUseCase,
    val runKbQuickInquiry: RunKbQuickInquiryUseCase,
    val saveKbCredentials: SaveKbCredentialsUseCase,
    val updateKbAutoInquiry: UpdateKbAutoInquiryUseCase
)

/** 등록 대기 큐 (승인 / 분할 / 취소 반영·연결 / 삭제 / 일괄 승인) */
data class PendingQueueUseCases(
    val approve: ApprovePendingItemUseCase,
    val approveSplit: ApproveSplitPendingItemUseCase,
    val applyMatchedCancel: ApplyMatchedCancelUseCase,
    val getManualCancelCandidates: GetManualCancelCandidatesUseCase,
    val linkCancelToTransaction: LinkCancelToTransactionUseCase,
    val dismiss: DismissPendingItemUseCase,
    val approveAll: ApproveAllPendingItemsUseCase,
    val updateClassification: UpdatePendingClassificationUseCase
)

/** 확정 거래 (직접 입력 / 취소 / 삭제) */
data class TransactionUseCases(
    val addManual: AddManualTransactionUseCase,
    val cancel: CancelTransactionUseCase,
    val restoreCancel: RestoreCanceledTransactionUseCase,
    val delete: DeleteTransactionUseCase
)

/** 카테고리 CRUD */
data class CategoryUseCases(
    val add: AddCategoryUseCase,
    val update: UpdateCategoryUseCase,
    val delete: DeleteCategoryUseCase
)

/** 집밥 절약 카운터 */
data class MealUseCases(
    val record: RecordHomeCookedMealUseCase,
    val delete: DeleteMealLogUseCase,
    val updatePriceConfig: UpdateMealPriceConfigUseCase
)

/** [Pro] 구글 계정·플랜·함께 쓰기 */
data class AccountUseCases(
    val signIn: SignInWithGoogleUseCase,
    val signOut: SignOutGoogleUseCase,
    val invitePartner: InvitePartnerUseCase,
    val updateSharedMembers: UpdateSharedMembersUseCase,
    val updateUserTier: UpdateUserTierUseCase,
    val loadMembers: LoadSharedMembersUseCase,
    val cancelInvitation: CancelInvitationUseCase,
    val removeMember: RemoveSharedMemberUseCase,
    val leaveSharedSpace: LeaveSharedSpaceUseCase
)

/** 공간 전환·예산·화면 스타일·무시 키워드 등 설정 */
data class SettingsUseCases(
    val selectSpace: SelectSpaceUseCase,
    val updateSpaceBudget: UpdateSpaceBudgetUseCase,
    val updateThemeMode: UpdateThemeModeUseCase,
    val deleteIgnoredKeyword: DeleteIgnoredKeywordUseCase,
    val insertSampleData: InsertSampleDataUseCase,
    val updateEnabledInstitutions: UpdateEnabledInstitutionsUseCase,
    val updateExcludedSources: UpdateExcludedSourcesUseCase,
    val updateAutoFilterDuplicates: UpdateAutoFilterDuplicatesUseCase,
    val updateDefaultIngestSpace: UpdateDefaultIngestSpaceUseCase,
    val updateOnboardingSeen: UpdateOnboardingSeenUseCase
)
