package com.budget.householdbook.di

import androidx.room.Room
import com.budget.householdbook.core.database.AppDatabase
import com.budget.householdbook.core.database.Migrations
import com.budget.householdbook.core.datastore.UserPreferencesRepository
import com.budget.householdbook.core.security.SecureCredentialManager
import com.budget.householdbook.data.ingestion.kbquick.KbQuickInquiryRepositoryImpl
import com.budget.householdbook.data.ingestion.kbquick.KbQuickInquiryScraper
import com.budget.householdbook.data.ingestion.kbquick.QuickInquiryProvider
import com.budget.householdbook.data.repository.RoomAutomationRuleRepository
import com.budget.householdbook.data.repository.RoomCategoryRepository
import com.budget.householdbook.data.repository.RoomLedgerSpaceRepository
import com.budget.householdbook.data.repository.RoomMealRepository
import com.budget.householdbook.data.repository.RoomPendingQueueRepository
import com.budget.householdbook.data.repository.RoomTransactionRepository
import com.budget.householdbook.data.repository.RoomTransactionRunner
import com.budget.householdbook.data.sync.FirebaseSharedSyncRepository
import com.budget.householdbook.data.sync.RemoteSyncManager
import com.budget.householdbook.data.work.BackgroundWorkScheduler
import com.budget.householdbook.domain.repository.AutomationRuleRepository
import com.budget.householdbook.domain.repository.BackgroundTaskScheduler
import com.budget.householdbook.domain.repository.CategoryRepository
import com.budget.householdbook.domain.repository.KbQuickInquiryRepository
import com.budget.householdbook.domain.repository.LedgerSpaceRepository
import com.budget.householdbook.domain.repository.MealRepository
import com.budget.householdbook.domain.repository.MealWidgetRefresher
import com.budget.householdbook.domain.repository.PendingQueueRepository
import com.budget.householdbook.domain.repository.SettingsRepository
import com.budget.householdbook.domain.repository.SharedSyncRepository
import com.budget.householdbook.domain.repository.LocalDataResetter
import com.budget.householdbook.data.local.AndroidLocalDataResetter
import com.budget.householdbook.domain.repository.TransactionRepository
import com.budget.householdbook.domain.repository.TransactionRunner
import com.budget.householdbook.domain.usecase.AccountUseCases
import com.budget.householdbook.domain.usecase.AddCategoryUseCase
import com.budget.householdbook.domain.usecase.AddManualTransactionUseCase
import com.budget.householdbook.domain.usecase.ApplyMatchedCancelUseCase
import com.budget.householdbook.domain.usecase.ApproveAllPendingItemsUseCase
import com.budget.householdbook.domain.usecase.ApprovePendingItemUseCase
import com.budget.householdbook.domain.usecase.ApproveSplitPendingItemUseCase
import com.budget.householdbook.domain.usecase.CalculateBudgetSummaryUseCase
import com.budget.householdbook.domain.usecase.CancelTransactionUseCase
import com.budget.householdbook.domain.usecase.RestoreCanceledTransactionUseCase
import com.budget.householdbook.domain.usecase.CategoryUseCases
import com.budget.householdbook.domain.usecase.SharedCategorySync
import com.budget.householdbook.domain.usecase.DeleteCategoryUseCase
import com.budget.householdbook.domain.usecase.DeleteIgnoredKeywordUseCase
import com.budget.householdbook.domain.usecase.UpdateEnabledInstitutionsUseCase
import com.budget.householdbook.domain.usecase.DeleteMealLogUseCase
import com.budget.householdbook.domain.usecase.DeleteTransactionUseCase
import com.budget.householdbook.domain.usecase.UpdateTransactionUseCase
import com.budget.householdbook.domain.usecase.DismissPendingItemUseCase
import com.budget.householdbook.domain.usecase.UpdateAutoFilterDuplicatesUseCase
import com.budget.householdbook.domain.usecase.UpdateDefaultIngestSpaceUseCase
import com.budget.householdbook.domain.usecase.UpdateOnboardingSeenUseCase
import com.budget.householdbook.domain.usecase.UpdateExcludedSourcesUseCase
import com.budget.householdbook.domain.usecase.UpdatePendingClassificationUseCase
import com.budget.householdbook.domain.usecase.EnqueueParsedDraftUseCase
import com.budget.householdbook.domain.usecase.EnsureBackgroundWorkUseCase
import com.budget.householdbook.domain.usecase.GetManualCancelCandidatesUseCase
import com.budget.householdbook.domain.usecase.GetMealWidgetSummaryUseCase
import com.budget.householdbook.domain.usecase.IngestRawNotificationUseCase
import com.budget.householdbook.domain.usecase.IngestionUseCases
import com.budget.householdbook.domain.usecase.InitializeAppUseCase
import com.budget.householdbook.domain.usecase.InvitePartnerUseCase
import com.budget.householdbook.domain.usecase.CancelInvitationUseCase
import com.budget.householdbook.domain.usecase.LeaveSharedSpaceUseCase
import com.budget.householdbook.domain.usecase.TransferOwnershipUseCase
import com.budget.householdbook.domain.usecase.DeleteAccountUseCase
import com.budget.householdbook.domain.usecase.AcceptPendingInvitationUseCase
import com.budget.householdbook.domain.usecase.QuickSavePendingUseCase
import com.budget.householdbook.domain.usecase.CheckBudgetAlertUseCase
import com.budget.householdbook.domain.usecase.CheckCollectionStatusUseCase
import com.budget.householdbook.domain.usecase.RecordBankInquiryResultUseCase
import com.budget.householdbook.domain.repository.AppNotifier
import com.budget.householdbook.domain.repository.AlertStateRepository
import com.budget.householdbook.core.datastore.AlertStateDataStore
import com.budget.householdbook.core.notification.AndroidAppNotifier
import com.budget.householdbook.core.notification.BudgetAlertMonitor
import com.budget.householdbook.core.notification.PendingAlertCleaner
import com.budget.householdbook.domain.usecase.UpdateDisplayNameUseCase
import com.budget.householdbook.domain.usecase.LoadSharedMembersUseCase
import com.budget.householdbook.domain.usecase.RemoveSharedMemberUseCase
import com.budget.householdbook.domain.usecase.IsKbAutoInquiryReadyUseCase
import com.budget.householdbook.domain.usecase.LinkCancelToTransactionUseCase
import com.budget.householdbook.domain.usecase.MealUseCases
import com.budget.householdbook.domain.usecase.ObserveHouseholdOverviewUseCase
import com.budget.householdbook.domain.usecase.PendingQueueUseCases
import com.budget.householdbook.domain.usecase.RecordHomeCookedMealUseCase
import com.budget.householdbook.domain.usecase.RecordCustomMealSavingUseCase
import com.budget.householdbook.domain.usecase.RunKbQuickInquiryUseCase
import com.budget.householdbook.domain.usecase.SaveKbCredentialsUseCase
import com.budget.householdbook.domain.usecase.SeedInitialDataUseCase
import com.budget.householdbook.domain.usecase.InsertSampleDataUseCase
import com.budget.householdbook.domain.usecase.SelectSpaceUseCase
import com.budget.householdbook.domain.usecase.SettingsUseCases
import com.budget.householdbook.domain.usecase.SignInWithGoogleUseCase
import com.budget.householdbook.domain.usecase.SignOutGoogleUseCase
import com.budget.householdbook.domain.usecase.SyncOnAppStartUseCase
import com.budget.householdbook.domain.usecase.SyncPendingSharedChangesUseCase
import com.budget.householdbook.domain.usecase.SyncSharedChangesUseCase
import com.budget.householdbook.domain.usecase.TransactionUseCases
import com.budget.householdbook.domain.usecase.UpdateCategoryUseCase
import com.budget.householdbook.domain.usecase.UpdateKbAutoInquiryUseCase
import com.budget.householdbook.domain.usecase.UpdateMealPriceConfigUseCase
import com.budget.householdbook.domain.usecase.UpdateSharedMembersUseCase
import com.budget.householdbook.domain.usecase.UpdateSpaceBudgetUseCase
import com.budget.householdbook.domain.usecase.UpdateThemeModeUseCase
import com.budget.householdbook.domain.usecase.UpdateUserTierUseCase
import com.budget.householdbook.ui.MainViewModel
import com.budget.householdbook.ui.widget.AndroidMealWidgetRefresher
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

/** Room DB 및 DAO */
val databaseModule = module {
    single {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java,
            "household_book_v001.db"
        )
            // 스키마가 바뀌면 Migrations에 단계별 마이그레이션을 추가 (사용자 데이터를 지우는 방식은 쓰지 않음)
            .addMigrations(*Migrations.ALL)
            .build()
    }

    single { get<AppDatabase>().ledgerSpaceDao() }
    single { get<AppDatabase>().categoryDao() }
    single { get<AppDatabase>().pendingTransactionDao() }
    single { get<AppDatabase>().transactionDao() }
    single { get<AppDatabase>().mealSavingDao() }
    single { get<AppDatabase>().ruleDao() }
}

/** 데이터 소스(DataStore, Keystore, Firebase, WorkManager, 스크래퍼) + 도메인 저장소 인터페이스 구현체 */
val repositoryModule = module {
    single { UserPreferencesRepository(androidContext()) } bind SettingsRepository::class
    single { SecureCredentialManager(androidContext()) }
    single { KbQuickInquiryScraper() }
    single { BackgroundWorkScheduler(androidContext()) } bind BackgroundTaskScheduler::class
    single { RemoteSyncManager(androidContext(), get(), get(), get(), get()) }
    factoryOf(::SharedCategorySync)

    singleOf(::RoomTransactionRunner) { bind<TransactionRunner>() }
    singleOf(::RoomLedgerSpaceRepository) { bind<LedgerSpaceRepository>() }
    singleOf(::RoomCategoryRepository) { bind<CategoryRepository>() }
    singleOf(::RoomPendingQueueRepository) { bind<PendingQueueRepository>() }
    singleOf(::RoomTransactionRepository) { bind<TransactionRepository>() }
    singleOf(::RoomMealRepository) { bind<MealRepository>() }
    singleOf(::RoomAutomationRuleRepository) { bind<AutomationRuleRepository>() }
    singleOf(::FirebaseSharedSyncRepository) { bind<SharedSyncRepository>() }
    single<LocalDataResetter> { AndroidLocalDataResetter(androidContext()) }
    // 은행별 빠른조회 연동 목록 (은행을 추가하면 여기에 등록)
    single<KbQuickInquiryRepository> {
        KbQuickInquiryRepositoryImpl(
            credentialManager = get(),
            providers = listOf<QuickInquiryProvider>(get<KbQuickInquiryScraper>())
        )
    }
    single<MealWidgetRefresher> { AndroidMealWidgetRefresher(androidContext()) }
    // 휴대폰 알림 (새 결제 · 예산 · 자동 기록 상태)
    single { AndroidAppNotifier(androidContext()) } bind AppNotifier::class
    single<AlertStateRepository> { AlertStateDataStore(androidContext()) }
    single { BudgetAlertMonitor(get(), get(), get()) }
    single { PendingAlertCleaner(get(), get()) }
}

/** 도메인 UseCase (상태 없음 → factory) 및 화면별 번들 */
val useCaseModule = module {
    // 앱 시작 · 동기화
    factoryOf(::SeedInitialDataUseCase)
    factoryOf(::InsertSampleDataUseCase)
    factoryOf(::EnsureBackgroundWorkUseCase)
    factoryOf(::SyncOnAppStartUseCase)
    factoryOf(::InitializeAppUseCase)
    factoryOf(::SyncSharedChangesUseCase)
    factoryOf(::SyncPendingSharedChangesUseCase)

    // 자동 수집
    factoryOf(::EnqueueParsedDraftUseCase)
    factoryOf(::IngestRawNotificationUseCase)
    factoryOf(::RunKbQuickInquiryUseCase)
    factoryOf(::SaveKbCredentialsUseCase)
    factoryOf(::UpdateKbAutoInquiryUseCase)
    factoryOf(::IsKbAutoInquiryReadyUseCase)

    // 대기 큐
    factoryOf(::ApprovePendingItemUseCase)
    factoryOf(::ApproveSplitPendingItemUseCase)
    factoryOf(::ApplyMatchedCancelUseCase)
    factoryOf(::GetManualCancelCandidatesUseCase)
    factoryOf(::LinkCancelToTransactionUseCase)
    factoryOf(::DismissPendingItemUseCase)
    factoryOf(::UpdatePendingClassificationUseCase)
    factoryOf(::ApproveAllPendingItemsUseCase)

    // 거래 · 카테고리 · 예산
    factoryOf(::AddManualTransactionUseCase)
    factoryOf(::CancelTransactionUseCase)
    factoryOf(::RestoreCanceledTransactionUseCase)
    factoryOf(::DeleteTransactionUseCase)
    factoryOf(::UpdateTransactionUseCase)
    factoryOf(::AddCategoryUseCase)
    factoryOf(::UpdateCategoryUseCase)
    factoryOf(::DeleteCategoryUseCase)
    factoryOf(::UpdateSpaceBudgetUseCase)
    factoryOf(::CalculateBudgetSummaryUseCase)
    factoryOf(::ObserveHouseholdOverviewUseCase)

    // 집밥
    factoryOf(::RecordHomeCookedMealUseCase)
    factoryOf(::RecordCustomMealSavingUseCase)
    factoryOf(::DeleteMealLogUseCase)
    factoryOf(::UpdateMealPriceConfigUseCase)
    factoryOf(::GetMealWidgetSummaryUseCase)

    // 계정 · 설정
    factoryOf(::UpdateSharedMembersUseCase)
    factoryOf(::UpdateUserTierUseCase)
    factoryOf(::SignInWithGoogleUseCase)
    factoryOf(::AcceptPendingInvitationUseCase)
    factoryOf(::InvitePartnerUseCase)
    factoryOf(::LoadSharedMembersUseCase)
    factoryOf(::CancelInvitationUseCase)
    factoryOf(::RemoveSharedMemberUseCase)
    factoryOf(::LeaveSharedSpaceUseCase)
    factoryOf(::TransferOwnershipUseCase)
    factoryOf(::DeleteAccountUseCase)
    // 알림
    factoryOf(::QuickSavePendingUseCase)
    factoryOf(::CheckBudgetAlertUseCase)
    factoryOf(::CheckCollectionStatusUseCase)
    factoryOf(::RecordBankInquiryResultUseCase)
    factoryOf(::UpdateDisplayNameUseCase)
    factoryOf(::SignOutGoogleUseCase)
    factoryOf(::SelectSpaceUseCase)
    factoryOf(::UpdateThemeModeUseCase)
    factoryOf(::DeleteIgnoredKeywordUseCase)
    factoryOf(::UpdateEnabledInstitutionsUseCase)
    factoryOf(::UpdateExcludedSourcesUseCase)
    factoryOf(::UpdateAutoFilterDuplicatesUseCase)
    factoryOf(::UpdateDefaultIngestSpaceUseCase)
    factoryOf(::UpdateOnboardingSeenUseCase)

    // ViewModel용 번들
    factoryOf(::IngestionUseCases)
    factoryOf(::PendingQueueUseCases)
    factoryOf(::TransactionUseCases)
    factoryOf(::CategoryUseCases)
    factoryOf(::MealUseCases)
    factoryOf(::AccountUseCases)
    factoryOf(::SettingsUseCases)
}

val viewModelModule = module {
    viewModelOf(::MainViewModel)
}

/** [com.budget.householdbook.HouseholdBookApp]에서 로드하는 전체 모듈 */
val appModules = listOf(databaseModule, repositoryModule, useCaseModule, viewModelModule)
