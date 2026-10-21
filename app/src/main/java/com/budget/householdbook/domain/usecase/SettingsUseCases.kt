package com.budget.householdbook.domain.usecase

import com.budget.householdbook.domain.model.FinancialInstitutions
import com.budget.householdbook.domain.model.ThemeMode
import com.budget.householdbook.domain.repository.AutomationRuleRepository
import com.budget.householdbook.domain.repository.MealWidgetRefresher
import com.budget.householdbook.domain.repository.SettingsRepository

/** 장부 공간 전환 (위젯도 새 공간 기준으로 갱신) */
class SelectSpaceUseCase(
    private val settingsRepository: SettingsRepository,
    private val widgetRefresher: MealWidgetRefresher
) {
    suspend operator fun invoke(spaceId: String) {
        settingsRepository.setCurrentSpaceId(spaceId)
        widgetRefresher.refresh()
    }
}

/** 화면 스타일(시스템/밝게/어둡게) 변경 */
class UpdateThemeModeUseCase(private val settingsRepository: SettingsRepository) {
    suspend operator fun invoke(themeMode: ThemeMode) {
        settingsRepository.setThemeMode(themeMode)
    }
}

/** 알림·문자를 읽어올 금융기관 선택 저장 (목록에 없는 ID는 버림) */
class UpdateEnabledInstitutionsUseCase(private val settingsRepository: SettingsRepository) {
    suspend operator fun invoke(ids: Set<String>) {
        settingsRepository.setEnabledInstitutions(ids.intersect(FinancialInstitutions.DEFAULT_ENABLED_IDS))
    }
}

/** 자동 건너뛰기(무시) 키워드 삭제 */
class DeleteIgnoredKeywordUseCase(private val ruleRepository: AutomationRuleRepository) {
    suspend operator fun invoke(id: Long) {
        ruleRepository.deleteIgnoredKeyword(id)
    }
}

/** 앱별 알림 제외 목록 저장 */
class UpdateExcludedSourcesUseCase(private val settingsRepository: SettingsRepository) {
    suspend operator fun invoke(ids: Set<String>) = settingsRepository.setExcludedSources(ids)
}

/** 다른 앱에서 온 같은 결제 알림 자동 거르기 켜기/끄기 */
class UpdateAutoFilterDuplicatesUseCase(private val settingsRepository: SettingsRepository) {
    suspend operator fun invoke(enabled: Boolean) = settingsRepository.setAutoFilterDuplicateAlerts(enabled)
}

/** 알림으로 들어온 내역의 기본 저장 공간 (null = 마지막으로 보던 공간) */
class UpdateDefaultIngestSpaceUseCase(private val settingsRepository: SettingsRepository) {
    suspend operator fun invoke(spaceId: String?) = settingsRepository.setDefaultIngestSpaceId(spaceId)
}
