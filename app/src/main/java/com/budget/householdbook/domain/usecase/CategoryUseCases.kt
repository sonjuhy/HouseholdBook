package com.budget.householdbook.domain.usecase

import com.budget.householdbook.core.database.AppDatabase
import com.budget.householdbook.data.local.entity.CategoryEntity
import com.budget.householdbook.domain.model.MAX_CATEGORY_NAME_LENGTH
import com.budget.householdbook.domain.model.SyncStatus
import com.budget.householdbook.domain.model.TransactionType
import com.budget.householdbook.domain.repository.CategoryRepository

/** 분류 추가 결과 */
enum class AddCategoryResult { ADDED, BLANK_NAME, DUPLICATE_NAME, PARENT_NOT_FOUND }

/** 공용 분류를 바꾸면 함께 쓰는 사람에게 올릴 표시 (개인 분류는 휴대폰에만) */
private fun syncStatusFor(spaceId: String): SyncStatus =
    if (spaceId == AppDatabase.SPACE_ID_SHARED) SyncStatus.PENDING_UPLOAD else SyncStatus.SYNCED

/**
 * 상위 카테고리(parentId = null) 또는 세부 카테고리 추가
 * - 공용·개인 분류는 공간별로 따로: 같은 이름이 다른 공간에 있어도 되지만, 같은 공간·같은 대분류 안에서는 중복 불가
 * - 세부 분류는 그 공간에 있는 대분류 아래에만 (대분류의 수입/지출 유형을 따름)
 * - 공용 분류는 함께 쓰는 사람에게도 반영
 */
class AddCategoryUseCase(
    private val categoryRepository: CategoryRepository,
    private val syncSharedChanges: SyncSharedChangesUseCase
) {
    suspend operator fun invoke(
        spaceId: String,
        parentId: Long?,
        transactionType: TransactionType,
        name: String,
        emoji: String,
        colorHex: String,
        nowMillis: Long = System.currentTimeMillis()
    ): AddCategoryResult {
        val trimmedName = name.trim().take(MAX_CATEGORY_NAME_LENGTH)
        if (trimmedName.isBlank()) return AddCategoryResult.BLANK_NAME

        val spaceCats = categoryRepository.getCategoriesBySpaceOnce(spaceId)
        val parent = parentId?.let { id -> spaceCats.firstOrNull { it.id == id && it.parentId == null } }
        if (parentId != null && parent == null) return AddCategoryResult.PARENT_NOT_FOUND
        if (spaceCats.any { it.parentId == parentId && it.name.equals(trimmedName, ignoreCase = true) }) {
            return AddCategoryResult.DUPLICATE_NAME
        }

        categoryRepository.insertCategory(
            CategoryEntity(
                spaceId = spaceId,
                parentId = parentId,
                transactionType = parent?.transactionType ?: transactionType,
                name = trimmedName,
                emoji = emoji.trim().ifBlank { "🏷️" },
                colorHex = parent?.colorHex ?: colorHex,
                sortOrder = (spaceCats.filter { it.parentId == parentId }.maxOfOrNull { it.sortOrder } ?: 0) + 1,
                syncStatus = syncStatusFor(spaceId),
                updatedAt = nowMillis
            )
        )
        if (spaceId == AppDatabase.SPACE_ID_SHARED) syncSharedChanges()
        return AddCategoryResult.ADDED
    }
}

/** 카테고리 이름/아이콘/색상/순서 수정 */
class UpdateCategoryUseCase(
    private val categoryRepository: CategoryRepository,
    private val syncSharedChanges: SyncSharedChangesUseCase
) {
    suspend operator fun invoke(category: CategoryEntity, nowMillis: Long = System.currentTimeMillis()) {
        categoryRepository.updateCategory(
            category.copy(
                name = category.name.trim().take(MAX_CATEGORY_NAME_LENGTH),
                syncStatus = syncStatusFor(category.spaceId),
                updatedAt = nowMillis
            )
        )
        if (category.spaceId == AppDatabase.SPACE_ID_SHARED) syncSharedChanges()
    }
}

/** 카테고리 삭제: 기존 거래 보존을 위해 상위 + 세부 카테고리를 소프트 삭제(isArchived) */
class DeleteCategoryUseCase(
    private val categoryRepository: CategoryRepository,
    private val syncSharedChanges: SyncSharedChangesUseCase
) {
    suspend operator fun invoke(categoryId: Long, nowMillis: Long = System.currentTimeMillis()) {
        val category = categoryRepository.getById(categoryId) ?: return
        categoryRepository.archiveCategoryAndSubs(categoryId, nowMillis, syncStatusFor(category.spaceId))
        if (category.spaceId == AppDatabase.SPACE_ID_SHARED) syncSharedChanges()
    }
}
