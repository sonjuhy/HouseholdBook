package com.budget.householdbook.domain.usecase

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

/** 자동 건너뛰기(무시) 키워드 삭제 */
class DeleteIgnoredKeywordUseCase(private val ruleRepository: AutomationRuleRepository) {
    suspend operator fun invoke(id: Long) {
        ruleRepository.deleteIgnoredKeyword(id)
    }
}
