package com.budget.householdbook.ui.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budget.householdbook.ui.theme.IosElevatedModalDark
import com.budget.householdbook.ui.theme.IosSystemRedLight
import com.budget.householdbook.ui.theme.LocalIsDarkTheme

/**
 * [방안 B] Apple iOS 18 스타일 플로팅 캡슐 탭 바 (Floating Pill Dock Tab Bar)
 * - 바닥에 딱 붙은 직사각형 바 대신 좌우/하단에 여백을 둔 둥근 캡슐 독(Dock) 형태로 띄우고,
 * - 선택된 탭에는 부드러운 알약(Pill) 하이라이트 배경 + 채움(Filled) 아이콘을,
 * - 비선택 탭에는 외곽선(Outlined) 아이콘을 적용하여 iOS 감성과 한글 5탭 가독성을 동시에 확보합니다.
 */
@Composable
fun IosFloatingCapsuleTabBar(
    selectedDestination: TopLevelDestination,
    pendingCount: Int,
    onSelectDestination: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val dockContainerColor = if (isDark) {
        IosElevatedModalDark.copy(alpha = 0.85f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)
    }
    val dockBorderColor = if (isDark) {
        Color.White.copy(alpha = 0.16f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    }
    val inactiveTint = if (isDark) {
        Color(0xFF98989F)
    } else {
        Color(0xFF6C6C70)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = if (isDark) 12.dp else 10.dp,
                    shape = RoundedCornerShape(32.dp),
                    ambientColor = Color.Black.copy(alpha = 0.18f),
                    spotColor = Color.Black.copy(alpha = 0.18f)
                ),
            shape = RoundedCornerShape(32.dp),
            color = dockContainerColor,
            border = BorderStroke(0.5.dp, dockBorderColor),
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TopLevelDestination.entries.forEach { tab ->
                    val isSelected = selectedDestination == tab
                    val activeColor = MaterialTheme.colorScheme.primary
                    val contentColor = if (isSelected) activeColor else inactiveTint
                    val pillBg = if (isSelected) {
                        activeColor.copy(alpha = if (isDark) 0.20f else 0.12f)
                    } else {
                        Color.Transparent
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(pillBg)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onSelectDestination(tab) }
                            .padding(vertical = 7.dp, horizontal = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(contentAlignment = Alignment.TopEnd) {
                                Icon(
                                    imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.tabLabel,
                                    tint = contentColor,
                                    modifier = Modifier.size(22.dp)
                                )
                                if (tab == TopLevelDestination.QUEUE && pendingCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .offset(x = 8.dp, y = (-4).dp)
                                            .size(16.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(IosSystemRedLight),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (pendingCount > 99) "99" else pendingCount.toString(),
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            lineHeight = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false,
                                            style = androidx.compose.ui.text.TextStyle(
                                                platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                                                    includeFontPadding = false
                                                )
                                            )
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = tab.tabLabel,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = contentColor,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
