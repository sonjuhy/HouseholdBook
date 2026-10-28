package com.budget.householdbook.domain.usecase

import com.budget.householdbook.data.local.entity.CategoryEntity
import com.budget.householdbook.domain.model.TransactionType
import com.budget.householdbook.domain.repository.CategoryRepository

/** 분류 추가 결과 */
enum class AddCategoryResult { ADDED, BLANK_NAME, DUPLICATE_NAME, PARENT_NOT_FOUND }

/**
 * 상위 카테고리(parentId = null) 또는 세부 카테고리 추가
 * - 공용·개인 분류는 공간별로 따로: 같은 이름이 다른 공간에 있어도 되지만, 같은 공간·같은 대분류 안에서는 중복 불가
 * - 세부 분류는 그 공간에 있는 대분류 아래에만 (대분류의 수입/지출 유형을 따름)
 */
class AddCategoryUseCase(private val categoryRepository: CategoryRepository) {
    suspend operator fun invoke(
        spaceId: String,
        parentId: Long?,
        transactionType: TransactionType,
        name: String,
        emoji: String,
        colorHex: String
    ): AddCategoryResult {
        val trimmedName = name.trim()
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
                sortOrder = (spaceCats.filter { it.parentId == parentId }.maxOfOrNull { it.sortOrder } ?: 0) + 1
            )
        )
        return AddCategoryResult.ADDED
    }
}

/** 카테고리 이름/아이콘/색상/순서 수정 */
class UpdateCategoryUseCase(private val categoryRepository: CategoryRepository) {
    suspend operator fun invoke(category: CategoryEntity) {
        categoryRepository.updateCategory(category)
    }
}

/** 카테고리 삭제: 기존 거래 보존을 위해 상위 + 세부 카테고리를 소프트 삭제(isArchived) */
class DeleteCategoryUseCase(private val categoryRepository: CategoryRepository) {
    suspend operator fun invoke(categoryId: Long) {
        categoryRepository.archiveCategoryAndSubs(categoryId)
    }
}
