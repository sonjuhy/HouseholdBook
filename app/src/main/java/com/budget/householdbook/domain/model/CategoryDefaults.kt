package com.budget.householdbook.domain.model

import com.budget.householdbook.data.local.entity.CategoryEntity

/**
 * 장부 공간을 바꿨을 때 카테고리가 이전 공간 것으로 남지 않도록 맞춰주는 규칙
 * - 원하는 카테고리가 해당 공간에 있으면 그대로 사용
 * - 없으면 거래 유형(수입/지출)에 맞는 그 공간의 첫 상위 카테고리 + 첫 세부 카테고리
 */
object CategoryDefaults {

    fun pick(
        spaceCategories: List<CategoryEntity>,
        type: TransactionType,
        preferredCategoryId: Long?,
        preferredSubCategoryId: Long?
    ): Pair<Long?, Long?> {
        val parents = spaceCategories.filter { it.parentId == null }
        val wantedType = if (type == TransactionType.INCOME) TransactionType.INCOME else TransactionType.EXPENSE

        val parent = parents.firstOrNull { it.id == preferredCategoryId }
            ?: parents.firstOrNull { it.transactionType == wantedType }
            ?: parents.firstOrNull()
            ?: return null to null

        val subs = spaceCategories.filter { it.parentId == parent.id }
        val sub = subs.firstOrNull { it.id == preferredSubCategoryId }
            ?: if (parent.id == preferredCategoryId && preferredSubCategoryId == null) null else subs.firstOrNull()
        return parent.id to sub?.id
    }
}

/**
 * 알림 출처(앱별 제외 기능)에서 문자 메시지를 하나의 출처로 다루기 위한 ID
 */
object NotificationSources {
    const val SMS_SOURCE_ID = "sms"
    const val SMS_SOURCE_LABEL = "문자 메시지(SMS)"
}
