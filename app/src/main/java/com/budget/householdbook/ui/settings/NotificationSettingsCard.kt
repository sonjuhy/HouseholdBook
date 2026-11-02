package com.budget.householdbook.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.LocalActivity
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.budget.householdbook.core.designsystem.IosHairlineDivider
import com.budget.householdbook.core.designsystem.IosInsetGroupedCard
import com.budget.householdbook.core.designsystem.IosSectionHeader
import com.budget.householdbook.core.designsystem.IosTintedButton
import com.budget.householdbook.core.notification.AndroidAppNotifier
import com.budget.householdbook.ui.theme.IosSystemGray

/** Android 13+에서 아직 알림 권한을 받지 못했는지 */
fun needsNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

/** 휴대폰의 배터리 최적화 대상이라 백그라운드에서 앱이 멈출 수 있는지 */
fun isBatteryRestricted(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == false

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

/** 이 앱의 Android 앱 정보 화면 (배터리 항목에서 제한 없음으로 바꾸기) */
fun openAppInfoSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/**
 * '알림 켜기' 동작
 * - 앱을 열 때 이미 한 번 물어봤으므로, Android가 권한 창을 다시 띄워 주는 경우에만 권한 요청
 * - 두 번 거절했거나 Android 설정에서 꺼 둔 경우 창이 뜨지 않으므로 Android 알림 설정으로
 */
@Composable
fun rememberEnableNotifications(): () -> Unit {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    return {
        val canAskAgain = needsNotificationPermission(context) && activity != null &&
            ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
        if (canAskAgain) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openAppNotificationSettings(context)
        }
    }
}

/**
 * 설정 > 알림: 새 결제 · 예산 80%/100% · 자동 기록 멈춤 알림
 * - 꺼져 있으면 '알림 켜기', 켜져 있으면 '종류별 설정' (Android 알림 설정에서 새 결제 / 예산 / 자동 기록 상태를 따로 끄기)
 * - 일부 종류를 꺼 뒀거나 배터리 최적화 대상이면 함께 알려줌
 */
@Composable
fun NotificationSettingsCard() {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(AndroidAppNotifier.isAllowed(context)) }
    var disabledKinds by remember { mutableStateOf(AndroidAppNotifier.disabledKinds(context)) }
    var batteryRestricted by remember { mutableStateOf(isBatteryRestricted(context)) }
    val enableNotifications = rememberEnableNotifications()
    // 권한 창이나 Android 설정에서 바꾸고 돌아온 경우 반영
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        allowed = AndroidAppNotifier.isAllowed(context)
        disabledKinds = AndroidAppNotifier.disabledKinds(context)
        batteryRestricted = isBatteryRestricted(context)
    }

    NotificationSettingsContent(
        allowed = allowed,
        disabledKinds = disabledKinds,
        batteryRestricted = batteryRestricted,
        onEnableNotifications = enableNotifications,
        onOpenKinds = { openAppNotificationSettings(context) },
        onOpenBattery = { openAppInfoSettings(context) }
    )
}

@Composable
fun NotificationSettingsContent(
    allowed: Boolean,
    disabledKinds: List<String>,
    batteryRestricted: Boolean,
    onEnableNotifications: () -> Unit,
    onOpenKinds: () -> Unit,
    onOpenBattery: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        IosSectionHeader(title = "알림")
        IosInsetGroupedCard(contentPadding = PaddingValues(16.dp)) {
            NotificationStatusRow(
                title = when {
                    !allowed -> "알림 꺼짐"
                    disabledKinds.isNotEmpty() -> "일부 알림 꺼짐"
                    else -> "알림 켜짐"
                },
                description = when {
                    !allowed -> "켜면 결제가 들어올 때 알림에서 바로 공용·개인에 저장할 수 있어요."
                    disabledKinds.isNotEmpty() -> "${disabledKinds.joinToString("·")} 알림이 꺼져 있어요. 종류별 설정에서 켤 수 있어요."
                    else -> "새 결제가 들어오면 알림에서 바로 저장하고, 예산을 80%·100% 쓰거나 자동 기록이 멈추면 알려드려요."
                }
            ) {
                if (allowed) {
                    IosTintedButton(text = "종류별 설정", onClick = onOpenKinds)
                } else {
                    IosTintedButton(
                        text = "알림 켜기",
                        icon = Icons.Default.NotificationsActive,
                        onClick = onEnableNotifications
                    )
                }
            }

            if (batteryRestricted) {
                Spacer(modifier = Modifier.height(12.dp))
                IosHairlineDivider()
                Spacer(modifier = Modifier.height(10.dp))
                NotificationStatusRow(
                    title = "배터리 사용 제한",
                    description = "휴대폰이 앱을 재우면 결제 기록과 알림이 늦거나 멈출 수 있어요. 앱 정보의 배터리에서 제한 없음으로 바꿔 주세요."
                ) {
                    IosTintedButton(text = "배터리 설정", onClick = onOpenBattery)
                }
            }
        }
    }
}

@Composable
private fun NotificationStatusRow(
    title: String,
    description: String,
    action: @Composable () -> Unit
) {
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
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = IosSystemGray
            )
        }
        action()
    }
}
