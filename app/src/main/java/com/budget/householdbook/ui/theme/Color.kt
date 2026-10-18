package com.budget.householdbook.ui.theme

import androidx.compose.ui.graphics.Color

// Apple Human Interface Guidelines (iOS System Colors)
val IosSystemBlueLight = Color(0xFF007AFF)       // 공통 생활비 iOS System Blue
val IosSystemBlueDark = Color(0xFF0A84FF)
val IosSystemBlueTint = Color(0xFFE5F1FF)

val IosSystemGreenLight = Color(0xFF34C759)      // 개인 용돈 & 수입 iOS System Green
val IosSystemGreenDark = Color(0xFF30D158)
val IosSystemGreenTint = Color(0xFFE8F9EE)

val IosSystemRedLight = Color(0xFFFF3B30)        // 지출 & 삭제(Destructive) iOS System Red
val IosSystemRedDark = Color(0xFFFF453A)

val IosSystemOrangeLight = Color(0xFFFF9500)     // 집밥 절약 iOS System Orange
val IosSystemOrangeDark = Color(0xFFFF9F0A)
val IosSystemOrangeTint = Color(0xFFFFF4E5)

val IosSystemGray = Color(0xFF8E8E93)            // iOS Secondary Label / Inactive Tab
val IosSystemGray2 = Color(0xFFAEAEB2)
val IosSystemGray4 = Color(0xFFD1D1D6)
val IosSystemGray5Light = Color(0xFFE5E5EA)      // Segmented Control Track & Pill Background
val IosSystemGray5Dark = Color(0xFF2C2C2E)
val IosSystemGray6Light = Color(0xFFF2F2F7)      // iOS systemGroupedBackground (Light)
val IosSystemGray6Dark = Color(0xFF000000)       // iOS systemGroupedBackground (Dark OLED)

val IosCardSurfaceLight = Color(0xFFFFFFFF)      // iOS secondarySystemGroupedBackground (Light)
val IosCardSurfaceDark = Color(0xFF1C1C1E)       // iOS secondarySystemGroupedBackground (Dark)
val IosSegmentedThumbDark = Color(0xFF3A3A3C)    // iOS Dark Mode Segmented Control Active Thumb
val IosElevatedModalDark = Color(0xFF242426)     // iOS Dark Mode Elevated Sheet / Dialog Surface

val IosSeparatorLight = Color(0xFFC6C6C8)        // 0.5dp Hairline Separator (Light)
val IosSeparatorDark = Color(0xFF38383A)         // 0.5dp Hairline Separator (Dark)

// 기존 화면 호환 별칭
val ExpenseCoral = IosSystemRedLight
val IncomeBlue = IosSystemBlueLight
val TransferSlate = IosSystemGray
val MealSaverAmber = IosSystemOrangeLight