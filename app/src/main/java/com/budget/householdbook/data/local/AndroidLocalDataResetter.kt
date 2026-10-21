package com.budget.householdbook.data.local

import android.app.ActivityManager
import android.content.Context
import com.budget.householdbook.domain.repository.LocalDataResetter

/**
 * 회원 탈퇴 때 '이 휴대폰의 기록도 지우기'를 고른 경우
 * - 장부 DB, 설정, 암호화해 둔 빠른조회 계좌 정보, 위젯 상태까지 모두 지우고 앱이 종료됨
 * - 다시 열면 처음 설치한 상태 (튜토리얼부터)
 */
class AndroidLocalDataResetter(private val context: Context) : LocalDataResetter {
    override fun wipeAllLocalData() {
        context.getSystemService(ActivityManager::class.java).clearApplicationUserData()
    }
}
