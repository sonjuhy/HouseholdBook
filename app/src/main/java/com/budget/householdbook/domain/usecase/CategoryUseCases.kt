package com.budget.householdbook.domain.usecase

import com.budget.householdbook.data.local.entity.CategoryEntity
import com.budget.householdbook.domain.model.TransactionType
import com.budget.householdbook.domain.repository.CategoryRepository

/**
 * 상위 카테고리(parentId = null) 또는 세부 카테고리 추가
 */
class AddCategoryUseCase(private val categoryRepository: CategoryRepository) {
    suspend operator fun invoke(
        spaceId: String,
        parentId: Long?,
        transactionType: TransactionType,
        name: String,
        emoji: String,
        colorHex: String
    ) {
        categoryRepository.insertCategory(
            CategoryEntity(
                spaceId = spaceId,
                parentId = parentId,
                transactionType = transactionType,
                name = name.trim(),
                emoji = emoji.trim().ifBlank { "🏷️" },
                colorHex = colorHex
            )
        )
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
