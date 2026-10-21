package com.budget.householdbook.core.datastore

import com.budget.householdbook.domain.model.MemberNames
import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.budget.householdbook.domain.model.FinancialInstitutions
import androidx.datastore.preferences.preferencesDataStore
import com.budget.householdbook.core.database.AppDatabase
import com.budget.householdbook.domain.model.MealPriceConfig
import com.budget.householdbook.domain.model.MealType
import com.budget.householdbook.domain.model.ThemeMode
import com.budget.householdbook.domain.model.UserTier
import com.budget.householdbook.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "household_user_prefs")

data class AppUserSettings(
    val currentSpaceId: String = AppDatabase.SPACE_ID_SHARED,
    val userTier: UserTier = UserTier.FREE,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** 장부에 표시할 내 이름 (구글 프로필 이름 또는 직접 바꾼 이름, 없으면 빈 값) */
    val userRealName: String = "",
    val isLoggedIn: Boolean = false,
    val userEmail: String = "",
    val userUid: String = "",
    val mealPriceConfig: MealPriceConfig = MealPriceConfig(),
    val isSeedCompleted: Boolean = false,
    /** 첫 실행 튜토리얼을 끝까지 봤거나 건너뛰었는지 */
    val hasSeenOnboarding: Boolean = false,
    val kbAutoInquiryEnabled: Boolean = false,
    val kbAutoInquiryIntervalHours: Int = DEFAULT_KB_AUTO_INQUIRY_HOURS,
    /** 알림·문자를 읽어올 금융기관 ID ([FinancialInstitutions]) */
    val enabledInstitutionIds: Set<String> = FinancialInstitutions.DEFAULT_ENABLED_IDS,
    /** 알림을 읽지 않을 앱 패키지 (문자는 [NotificationSources.SMS_SOURCE_ID]) */
    val excludedSourceIds: Set<String> = emptySet(),
    /** 지금까지 금융 알림을 보낸 출처: ID(패키지 또는 sms) → 앱 이름 */
    val seenSources: Map<String, String> = emptyMap(),
    /** 다른 앱에서 같은 결제 알림이 연달아 오면 하나만 남기기 */
    val autoFilterDuplicateAlerts: Boolean = true,
    /** 알림으로 들어온 내역의 기본 저장 공간 (null이면 마지막으로 보던 공간) */
    val defaultIngestSpaceId: String? = null
) {
    /** 다른 멤버에게 보이는 내 이름: 저장된 이름 → 이메일 앞부분 → '나' */
    val displayName: String
        get() = userRealName.ifBlank { userEmail.substringBefore("@").ifBlank { "나" } }
}

const val DEFAULT_KB_AUTO_INQUIRY_HOURS = 6

private const val SEEN_SEPARATOR = '\u001F'

class UserPreferencesRepository(private val context: Context) : SettingsRepository {

    private object Keys {
        val CURRENT_SPACE_ID = stringPreferencesKey("current_space_id")
        val USER_TIER = stringPreferencesKey("user_tier")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val USER_REAL_NAME = stringPreferencesKey("user_real_name")
        val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val USER_UID = stringPreferencesKey("user_uid")
        val BREAKFAST_PRICE = longPreferencesKey("meal_price_breakfast")
        val LUNCH_PRICE = longPreferencesKey("meal_price_lunch")
        val DINNER_PRICE = longPreferencesKey("meal_price_dinner")
        val SNACK_PRICE = longPreferencesKey("meal_price_snack")
        val IS_SEED_COMPLETED = booleanPreferencesKey("is_seed_completed")
        val HAS_SEEN_ONBOARDING = booleanPreferencesKey("has_seen_onboarding")
        val KB_AUTO_INQUIRY_ENABLED = booleanPreferencesKey("kb_auto_inquiry_enabled")
        val KB_AUTO_INQUIRY_HOURS = intPreferencesKey("kb_auto_inquiry_hours")
        val ENABLED_INSTITUTIONS = stringSetPreferencesKey("enabled_institution_ids")
        val EXCLUDED_SOURCES = stringSetPreferencesKey("excluded_notification_sources")
        val SEEN_SOURCES = stringSetPreferencesKey("seen_notification_sources")
        val AUTO_FILTER_DUPLICATES = booleanPreferencesKey("auto_filter_duplicate_alerts")
        val DEFAULT_INGEST_SPACE = stringPreferencesKey("default_ingest_space_id")
    }

    override val userSettingsFlow: Flow<AppUserSettings> = context.dataStore.data.map { prefs ->
        val tierName = prefs[Keys.USER_TIER] ?: UserTier.FREE.name
        val tier = runCatching { UserTier.valueOf(tierName) }.getOrDefault(UserTier.FREE)
        val themeModeName = prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        val themeMode = runCatching { ThemeMode.valueOf(themeModeName) }.getOrDefault(ThemeMode.SYSTEM)
        AppUserSettings(
            currentSpaceId = prefs[Keys.CURRENT_SPACE_ID] ?: AppDatabase.SPACE_ID_SHARED,
            userTier = tier,
            themeMode = themeMode,
            userRealName = prefs[Keys.USER_REAL_NAME]?.takeUnless { it == MemberNames.LEGACY_PLACEHOLDER_NAME }.orEmpty(),
            isLoggedIn = prefs[Keys.IS_LOGGED_IN] ?: false,
            userEmail = prefs[Keys.USER_EMAIL] ?: "",
            userUid = prefs[Keys.USER_UID] ?: "",
            mealPriceConfig = MealPriceConfig(
                breakfastPrice = prefs[Keys.BREAKFAST_PRICE] ?: MealType.BREAKFAST.defaultUnitPrice,
                lunchPrice = prefs[Keys.LUNCH_PRICE] ?: MealType.LUNCH.defaultUnitPrice,
                dinnerPrice = prefs[Keys.DINNER_PRICE] ?: MealType.DINNER.defaultUnitPrice,
                snackPrice = prefs[Keys.SNACK_PRICE] ?: MealType.SNACK.defaultUnitPrice
            ),
            isSeedCompleted = prefs[Keys.IS_SEED_COMPLETED] ?: false,
            hasSeenOnboarding = prefs[Keys.HAS_SEEN_ONBOARDING] ?: false,
            kbAutoInquiryEnabled = prefs[Keys.KB_AUTO_INQUIRY_ENABLED] ?: false,
            kbAutoInquiryIntervalHours = prefs[Keys.KB_AUTO_INQUIRY_HOURS] ?: DEFAULT_KB_AUTO_INQUIRY_HOURS,
            enabledInstitutionIds = prefs[Keys.ENABLED_INSTITUTIONS] ?: FinancialInstitutions.DEFAULT_ENABLED_IDS,
            excludedSourceIds = prefs[Keys.EXCLUDED_SOURCES] ?: emptySet(),
            seenSources = (prefs[Keys.SEEN_SOURCES] ?: emptySet()).mapNotNull { decodeSeenSource(it) }.toMap(),
            autoFilterDuplicateAlerts = prefs[Keys.AUTO_FILTER_DUPLICATES] ?: true,
            defaultIngestSpaceId = prefs[Keys.DEFAULT_INGEST_SPACE]?.takeIf { it.isNotBlank() }
        )
    }

    override suspend fun setCurrentSpaceId(spaceId: String) {
        context.dataStore.edit { it[Keys.CURRENT_SPACE_ID] = spaceId }
    }

    override suspend fun setUserTier(tier: UserTier) {
        context.dataStore.edit { it[Keys.USER_TIER] = tier.name }
    }

    override suspend fun setThemeMode(themeMode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = themeMode.name }
    }

    override suspend fun setUserRealName(name: String) {
        context.dataStore.edit { it[Keys.USER_REAL_NAME] = name.trim() }
    }

    override suspend fun saveGoogleLoginProfile(
        uid: String,
        email: String,
        displayName: String,
        resolvedTier: UserTier
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.IS_LOGGED_IN] = true
            prefs[Keys.USER_UID] = uid
            prefs[Keys.USER_EMAIL] = email.trim().lowercase()
            prefs[Keys.USER_REAL_NAME] = displayName.trim().ifBlank { email.substringBefore("@") }
            prefs[Keys.USER_TIER] = resolvedTier.name
        }
    }

    override suspend fun clearGoogleLogin() {
        context.dataStore.edit { prefs ->
            prefs[Keys.IS_LOGGED_IN] = false
            prefs[Keys.USER_UID] = ""
            prefs[Keys.USER_EMAIL] = ""
            prefs[Keys.USER_TIER] = UserTier.FREE.name
        }
    }

    override suspend fun updateMealPriceConfig(config: MealPriceConfig) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BREAKFAST_PRICE] = config.breakfastPrice.coerceAtLeast(500L)
            prefs[Keys.LUNCH_PRICE] = config.lunchPrice.coerceAtLeast(500L)
            prefs[Keys.DINNER_PRICE] = config.dinnerPrice.coerceAtLeast(500L)
            prefs[Keys.SNACK_PRICE] = config.snackPrice.coerceAtLeast(500L)
        }
    }

    override suspend fun setKbAutoInquiry(enabled: Boolean, intervalHours: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.KB_AUTO_INQUIRY_ENABLED] = enabled
            prefs[Keys.KB_AUTO_INQUIRY_HOURS] = intervalHours
        }
    }

    override suspend fun setExcludedSources(ids: Set<String>) {
        context.dataStore.edit { it[Keys.EXCLUDED_SOURCES] = ids }
    }

    override suspend fun recordSeenSource(sourceId: String, label: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.SEEN_SOURCES] ?: emptySet()
            val entry = encodeSeenSource(sourceId, label)
            if (entry in current) return@edit
            // 같은 출처의 예전 이름은 지우고 최신 앱 이름으로 저장
            prefs[Keys.SEEN_SOURCES] = current.filterNot { decodeSeenSource(it)?.first == sourceId }.toSet() + entry
        }
    }

    override suspend fun setAutoFilterDuplicateAlerts(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_FILTER_DUPLICATES] = enabled }
    }

    override suspend fun setDefaultIngestSpaceId(spaceId: String?) {
        context.dataStore.edit { it[Keys.DEFAULT_INGEST_SPACE] = spaceId.orEmpty() }
    }

    private fun encodeSeenSource(sourceId: String, label: String) = "$sourceId$SEEN_SEPARATOR$label"

    private fun decodeSeenSource(raw: String): Pair<String, String>? {
        val index = raw.indexOf(SEEN_SEPARATOR)
        if (index <= 0) return null
        return raw.substring(0, index) to raw.substring(index + 1)
    }

    override suspend fun setEnabledInstitutions(ids: Set<String>) {
        context.dataStore.edit { it[Keys.ENABLED_INSTITUTIONS] = ids }
    }

    override suspend fun setOnboardingSeen(seen: Boolean) {
        context.dataStore.edit { it[Keys.HAS_SEEN_ONBOARDING] = seen }
    }

    override suspend fun markSeedCompleted() {
        context.dataStore.edit { it[Keys.IS_SEED_COMPLETED] = true }
    }
}

