package com.budget.householdbook.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.budget.householdbook.core.designsystem.IosInsetGroupedCard
import com.budget.householdbook.core.designsystem.IosSectionHeader
import com.budget.householdbook.core.designsystem.IosTintedButton
import com.budget.householdbook.ui.theme.IosSystemGray

/** 앱 알림을 보낼 수 있는 상태인지 (Android 13+ 알림 권한 + 앱 알림 켜짐) */
fun areAppNotificationsAllowed(context: Context): Boolean {
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

/** 이 앱의 Android 알림 설정 화면 (종류별로 끄고 켜기) */
fun openAppNotificationSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/**
 * 설정 > 알림: 새 결제 · 예산 80%/100% · 자동 기록 멈춤 알림
 * - 꺼져 있으면 '알림 켜기' (Android 13+는 권한 요청, 한 번 거절했으면 Android 알림 설정으로)
 * - 켜져 있으면 '종류별 설정' (Android 알림 설정에서 새 결제 / 예산 / 자동 기록 상태를 따로 끄기)
 */
@Composable
fun NotificationSettingsCard() {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(areAppNotificationsAllowed(context)) }
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = areAppNotificationsAllowed(context)
    }
    // Android 설정에서 바꾸고 돌아온 경우 반영
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { allowed = areAppNotificationsAllowed(context) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        IosSectionHeader(title = "알림")
        IosInsetGroupedCard(contentPadding = PaddingValues(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 10.dp)
                ) {
                    Text(
                        text = if (allowed) "알림 켜짐" else "알림 꺼짐",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (allowed) {
                            "새 결제가 들어오면 알림에서 바로 저장하고, 예산을 80%·100% 쓰거나 자동 기록이 멈추면 알려드려요."
                        } else {
                            "켜면 결제가 들어올 때 알림에서 바로 공용·개인에 저장할 수 있어요."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = IosSystemGray
                    )
                }
                if (allowed) {
                    IosTintedButton(text = "종류별 설정", onClick = { openAppNotificationSettings(context) })
                } else {
                    IosTintedButton(
                        text = "알림 켜기",
                        icon = Icons.Default.NotificationsActive,
                        onClick = {
                            val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                                PackageManager.PERMISSION_GRANTED
                            if (needsPermission && !askedOnce) {
                                askedOnce = true
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                // 이미 거절했거나 Android 설정에서 꺼 둔 경우
                                openAppNotificationSettings(context)
                            }
                        }
                    )
                }
            }
        }
    }
}
