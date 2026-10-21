package com.budget.householdbook.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budget.householdbook.core.designsystem.IosFilledButton
import com.budget.householdbook.core.designsystem.IosTintedButton
import com.budget.householdbook.ui.theme.IncomeBlue
import com.budget.householdbook.ui.theme.IosSystemGray
import com.budget.householdbook.ui.theme.IosSystemGreenLight
import com.budget.householdbook.ui.theme.MealSaverAmber
import kotlinx.coroutines.launch

/** 튜토리얼 한 장 */
data class OnboardingPage(
    val emoji: String,
    val title: String,
    val body: String,
    val highlights: List<String>,
    val tint: Color
)

/** 처음 실행 튜토리얼 내용 (전문 용어 없이 쉬운 말로 — history 3단계 원칙) */
val OnboardingPages: List<OnboardingPage> = listOf(
    OnboardingPage(
        emoji = "👋",
        title = "환영해요!",
        body = "우리집 공통 생활비와 내 용돈을\n한 앱에서 따로따로 관리해요.",
        highlights = listOf("공용 · 개인 분리", "목표 예산", "자동 기록"),
        tint = IncomeBlue
    ),
    OnboardingPage(
        emoji = "🔔",
        title = "결제 알림을 알아서 모아요",
        body = "카드·은행 앱 알림과 문자를 읽어\n'대기내역'에 모아둬요.\n확인하고 '공용에 저장' 또는\n'개인에 저장'만 누르면 끝이에요.",
        highlights = listOf("같은 결제 알림은 하나만", "결제 취소 자동 연결", "내 계좌 이체는 합계에서 제외"),
        tint = IosSystemGreenLight
    ),
    OnboardingPage(
        emoji = "🎯",
        title = "이번 달 얼마 남았는지 한눈에",
        body = "공간마다 목표 금액과\n월급날 같은 시작일을 정하면\n남은 예산과 하루에 써도 되는 금액을\n계산해 줘요.",
        highlights = listOf("시작일 1~28일 · 말일", "예산 넘으면 빨간 경고"),
        tint = Color(0xFFFF3B30)
    ),
    OnboardingPage(
        emoji = "🍚",
        title = "집밥 해 먹은 날은 +1",
        body = "직접 해 먹은 끼니를 누르면\n아낀 외식비가 쌓여요.\n홈 화면 위젯으로 앱을 열지 않고도\n기록할 수 있어요.",
        highlights = listOf("끼니별 금액 설정", "홈 화면 위젯"),
        tint = MealSaverAmber
    ),
    OnboardingPage(
        emoji = "👥",
        title = "가족·짝꿍과 함께 쓰기",
        body = "구글 계정으로 로그인하고 초대하면\n공통 생활비를 실시간으로 함께 봐요.\n내 용돈은 나만 볼 수 있어요.",
        highlights = listOf("인터넷 없어도 기록", "멤버 초대·관리"),
        tint = IncomeBlue
    )
)

/**
 * 처음 실행 튜토리얼
 * - 좌우로 넘기거나 '다음'으로 이동, 언제든 '건너뛰기'
 * - 마지막 장: '예시 데이터로 둘러보기' 또는 '시작하기'
 * - 설정 > 앱 둘러보기 > '앱 사용법 다시 보기'로 다시 볼 수 있음
 */
@Composable
fun OnboardingScreen(
    onFinish: (withSampleData: Boolean) -> Unit,
    pages: List<OnboardingPage> = OnboardingPages
) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == pages.lastIndex

    // 뒤로 가기: 이전 장으로 (첫 장에서는 기본 동작)
    BackHandler(enabled = pagerState.currentPage > 0) {
        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        // 상단: 건너뛰기 (마지막 장에서는 숨김)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            if (!isLastPage) {
                TextButton(onClick = { onFinish(false) }) {
                    Text("건너뛰기", color = IosSystemGray, fontSize = 15.sp, maxLines = 1, softWrap = false)
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { index ->
            OnboardingPageContent(pages[index])
        }

        PageIndicator(
            pageCount = pages.size,
            currentPage = pagerState.currentPage,
            activeColor = pages[pagerState.currentPage].tint,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 20.dp)
        )

        if (isLastPage) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                IosFilledButton(
                    text = "시작하기",
                    onClick = { onFinish(false) },
                    modifier = Modifier.fillMaxWidth()
                )
                IosTintedButton(
                    text = "예시 데이터로 둘러보기",
                    onClick = { onFinish(true) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            IosFilledButton(
                text = "다음",
                onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(128.dp)
                .clip(CircleShape)
                .background(page.tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = page.emoji, fontSize = 60.sp)
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = page.title,
            // 화면이 좁아 줄이 넘어가도 단어 중간이 끊기지 않도록 (Type.kt의 한국어 구절 단위 줄바꿈)
            style = MaterialTheme.typography.bodyLarge,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = page.body,
            style = MaterialTheme.typography.bodyLarge,
            fontSize = 15.sp,
            lineHeight = 23.sp,
            textAlign = TextAlign.Center,
            color = IosSystemGray
        )
        Spacer(modifier = Modifier.height(22.dp))
        // 핵심 기능 요약 (한 줄에 다 안 들어가면 다음 줄로)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            page.highlights.forEach { label ->
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = page.tint,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(page.tint.copy(alpha = 0.12f))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun PageIndicator(
    pageCount: Int,
    currentPage: Int,
    activeColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.semantics { contentDescription = "${pageCount}장 중 ${currentPage + 1}번째" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val selected = index == currentPage
            val width by animateDpAsState(if (selected) 22.dp else 7.dp, label = "dotWidth")
            val color by animateColorAsState(
                if (selected) activeColor else IosSystemGray.copy(alpha = 0.35f),
                label = "dotColor"
            )
            Box(
                modifier = Modifier
                    .height(7.dp)
                    .width(width)
                    .clip(RoundedCornerShape(50))
                    .background(color)
            )
        }
    }
}
