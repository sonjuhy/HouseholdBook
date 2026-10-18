package com.budget.householdbook.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
    val userRealName: String = "홍길동",
    val isLoggedIn: Boolean = false,
    val userEmail: String = "",
    val userUid: String = "",
    val mealPriceConfig: MealPriceConfig = MealPriceConfig(),
    val isSeedCompleted: Boolean = false,
    val kbAutoInquiryEnabled: Boolean = false,
    val kbAutoInquiryIntervalHours: Int = DEFAULT_KB_AUTO_INQUIRY_HOURS
)

const val DEFAULT_KB_AUTO_INQUIRY_HOURS = 6

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
        val KB_AUTO_INQUIRY_ENABLED = booleanPreferencesKey("kb_auto_inquiry_enabled")
        val KB_AUTO_INQUIRY_HOURS = intPreferencesKey("kb_auto_inquiry_hours")
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
            userRealName = prefs[Keys.USER_REAL_NAME] ?: "홍길동",
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
            kbAutoInquiryEnabled = prefs[Keys.KB_AUTO_INQUIRY_ENABLED] ?: false,
            kbAutoInquiryIntervalHours = prefs[Keys.KB_AUTO_INQUIRY_HOURS] ?: DEFAULT_KB_AUTO_INQUIRY_HOURS
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
        context.dataStore.edit { it[Keys.USER_REAL_NAME] = name.trim().ifBlank { "홍길동" } }
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

    override suspend fun markSeedCompleted() {
        context.dataStore.edit { it[Keys.IS_SEED_COMPLETED] = true }
    }
}

