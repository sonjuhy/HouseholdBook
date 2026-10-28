package com.budget.householdbook.domain.model

import com.budget.householdbook.data.local.entity.CategoryEntity
import java.util.UUID

/**
 * 분류의 원격 ID: 휴대폰마다 다른 로컬 id 대신, 함께 쓰는 사람끼리 같은 분류를 가리킴
 * - 기본 분류(처음 설치할 때 만든 분류)와 업데이트 전부터 있던 분류는 장부·대분류·이름으로 정해지는 같은 값
 *   → 두 사람 휴대폰의 '식비'가 따로 생기지 않고 하나로 합쳐짐
 * - 새로 추가하는 분류는 무작위 값 (이름을 바꾼 뒤 같은 이름을 다시 만들어도 겹치지 않도록)
 */
object CategoryRemoteIds {
    fun newId(): String = "cat-" + UUID.randomUUID()

    fun named(spaceId: String, parentName: String?, name: String): String =
        "cat-" + UUID.nameUUIDFromBytes("$spaceId|${parentName.orEmpty()}|$name".toByteArray(Charsets.UTF_8))
}

/** 분류 이름 최대 길이 (firestore.rules의 공용 분류 이름 검사와 같게 유지) */
const val MAX_CATEGORY_NAME_LENGTH = 50

/** 공유 장부(Firestore)에 올리는 공용 분류 한 건 */
data class RemoteCategory(
    val remoteId: String,
    val parentRemoteId: String?,
    val transactionType: TransactionType,
    val name: String,
    val emoji: String,
    val colorHex: String,
    val sortOrder: Int,
    val isArchived: Boolean,
    val updatedAt: Long
)

fun CategoryEntity.toRemote(parentRemoteId: String?) = RemoteCategory(
    remoteId = remoteId,
    parentRemoteId = parentRemoteId,
    transactionType = transactionType,
    name = name.take(MAX_CATEGORY_NAME_LENGTH),
    emoji = emoji,
    colorHex = colorHex,
    sortOrder = sortOrder,
    isArchived = isArchived,
    updatedAt = updatedAt
)
