package com.budget.householdbook.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.budget.householdbook.domain.model.BudgetAlertLevel
import com.budget.householdbook.domain.model.BudgetAlertRecord
import com.budget.householdbook.domain.model.CollectionAlertState
import com.budget.householdbook.domain.repository.AlertStateRepository
import kotlinx.coroutines.flow.first

private val Context.alertStateStore by preferencesDataStore(name = "household_alert_state")

/**
 * 알림을 언제 보냈는지 기록 (사용자 설정과 섞이지 않도록 별도 파일)
 * - 예산: 공간별 "주기 시작일|단계"
 * - 자동 수집: 알림 읽기 권한 사용 이력, 은행 자동 불러오기 연속 실패 횟수
 */
class AlertStateDataStore(private val context: Context) : AlertStateRepository {

    private object Keys {
        fun budget(spaceId: String) = stringPreferencesKey("budget_alert_$spaceId")
        val ACCESS_EVER_ENABLED = booleanPreferencesKey("notification_access_ever_enabled")
        val ACCESS_ALERT_SENT = booleanPreferencesKey("notification_access_alert_sent")
        val BANK_FAILURES = intPreferencesKey("bank_inquiry_failures")
        val BANK_ALERT_SENT = booleanPreferencesKey("bank_inquiry_alert_sent")
    }

    override suspend fun getLastBudgetAlert(spaceId: String): BudgetAlertRecord? {
        val raw = context.alertStateStore.data.first()[Keys.budget(spaceId)] ?: return null
        val (period, levelName) = raw.split('|').takeIf { it.size == 2 } ?: return null
        val level = runCatching { BudgetAlertLevel.valueOf(levelName) }.getOrNull() ?: return null
        return BudgetAlertRecord(period, level)
    }

    override suspend fun setLastBudgetAlert(spaceId: String, record: BudgetAlertRecord) {
        context.alertStateStore.edit { it[Keys.budget(spaceId)] = "${record.periodStart}|${record.level.name}" }
    }

    override suspend fun getCollectionState(): CollectionAlertState {
        val prefs = context.alertStateStore.data.first()
        return CollectionAlertState(
            notificationAccessEverEnabled = prefs[Keys.ACCESS_EVER_ENABLED] ?: false,
            notificationAccessAlertSent = prefs[Keys.ACCESS_ALERT_SENT] ?: false,
            bankInquiryFailures = prefs[Keys.BANK_FAILURES] ?: 0,
            bankInquiryAlertSent = prefs[Keys.BANK_ALERT_SENT] ?: false
        )
    }

    override suspend fun setCollectionState(state: CollectionAlertState) {
        context.alertStateStore.edit {
            it[Keys.ACCESS_EVER_ENABLED] = state.notificationAccessEverEnabled
            it[Keys.ACCESS_ALERT_SENT] = state.notificationAccessAlertSent
            it[Keys.BANK_FAILURES] = state.bankInquiryFailures
            it[Keys.BANK_ALERT_SENT] = state.bankInquiryAlertSent
        }
    }
}
