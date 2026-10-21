package com.budget.householdbook.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.sp

/**
 * 한국어 어절·문맥(Phrase) 단위 줄바꿈 설정
 * - 글자 중간에서 뚝 끊기지 않고, 한국어 띄어쓰기/어절 및 문맥 단위로 자연스럽게 다음 줄로 넘어가도록 설정합니다.
 * - Strategy.HighQuality / Balanced 조합으로 줄 길이 균형까지 맞춥니다.
 */
val KoreanPhraseLineBreak = LineBreak.Paragraph.copy(
    strategy = LineBreak.Strategy.HighQuality,
    strictness = LineBreak.Strictness.Normal,
    wordBreak = LineBreak.WordBreak.Phrase
)

val KoreanHeadingLineBreak = LineBreak.Heading.copy(
    strategy = LineBreak.Strategy.Balanced,
    strictness = LineBreak.Strictness.Normal,
    wordBreak = LineBreak.WordBreak.Phrase
)

/**
 * 앱의 글은 모두 한국어이므로 글자 언어를 한국어로 고정
 * - Android의 어절(Phrase) 단위 줄바꿈은 글자 언어가 한국어·일본어일 때만 동작하므로,
 *   휴대폰 언어가 영어 등이어도 단어 중간에서 끊기지 않도록 함
 */
private val KoreanLocale = LocaleList("ko-KR")

private val BaseKoreanTextStyle = TextStyle(
    fontFamily = FontFamily.Default,
    lineBreak = KoreanPhraseLineBreak,
    localeList = KoreanLocale
)

private val BaseKoreanHeadingStyle = TextStyle(
    fontFamily = FontFamily.Default,
    lineBreak = KoreanHeadingLineBreak,
    localeList = KoreanLocale
)

private val DefaultTypography = Typography()

val Typography = Typography(
    displayLarge = DefaultTypography.displayLarge.merge(BaseKoreanHeadingStyle),
    displayMedium = DefaultTypography.displayMedium.merge(BaseKoreanHeadingStyle),
    displaySmall = DefaultTypography.displaySmall.merge(BaseKoreanHeadingStyle),
    headlineLarge = DefaultTypography.headlineLarge.merge(BaseKoreanHeadingStyle),
    headlineMedium = DefaultTypography.headlineMedium.merge(BaseKoreanHeadingStyle),
    headlineSmall = DefaultTypography.headlineSmall.merge(BaseKoreanHeadingStyle),
    titleLarge = DefaultTypography.titleLarge.merge(BaseKoreanHeadingStyle),
    titleMedium = DefaultTypography.titleMedium.merge(BaseKoreanHeadingStyle),
    titleSmall = DefaultTypography.titleSmall.merge(BaseKoreanHeadingStyle),
    bodyLarge = DefaultTypography.bodyLarge.merge(BaseKoreanTextStyle).copy(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 23.sp,
        letterSpacing = (-0.2).sp
    ),
    bodyMedium = DefaultTypography.bodyMedium.merge(BaseKoreanTextStyle).copy(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.15).sp
    ),
    bodySmall = DefaultTypography.bodySmall.merge(BaseKoreanTextStyle).copy(
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = (-0.1).sp
    ),
    labelLarge = DefaultTypography.labelLarge.merge(BaseKoreanTextStyle),
    labelMedium = DefaultTypography.labelMedium.merge(BaseKoreanTextStyle),
    labelSmall = DefaultTypography.labelSmall.merge(BaseKoreanTextStyle)
)