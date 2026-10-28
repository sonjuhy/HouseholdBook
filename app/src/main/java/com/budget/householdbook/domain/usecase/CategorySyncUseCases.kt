package com.budget.householdbook.domain.usecase

import com.budget.householdbook.core.database.AppDatabase
import com.budget.householdbook.data.local.entity.CategoryEntity
import com.budget.householdbook.domain.model.RemoteCategory
import com.budget.householdbook.domain.model.SyncStatus
import com.budget.householdbook.domain.model.toRemote
import com.budget.householdbook.domain.repository.CategoryRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 공용 분류 동기화 (Firestore 통신은 RemoteSyncManager, 합치는 규칙은 여기)
 * - 휴대폰마다 다른 로컬 id 대신 원격 ID(remoteId)로 같은 분류를 찾음
 * - 같은 분류를 양쪽에서 바꿨으면 updatedAt이 더 최근인 쪽을 남김
 * - 지운 분류는 숨김(isArchived)으로 올려 기존 거래는 계속 그 분류로 보임
 * - 개인 분류는 절대 올리지 않음
 */
class SharedCategorySync(private val categoryRepository: CategoryRepository) {

    private val sharedId = AppDatabase.SPACE_ID_SHARED

    // 실시간 수신과 동기화가 동시에 같은 분류를 넣지 않도록
    private val applyLock = Mutex()

    /** 아직 올리지 않은 공용 분류 변경이 있는지 */
    suspend fun hasPendingUploads(): Boolean = categoryRepository.countPendingUploads(sharedId) > 0

    /**
     * 공유 장부의 분류를 내 휴대폰에 반영
     * @param remote 받은 분류 (공유 장부 전체 목록이면 [isFullSnapshot] = true)
     * @param isFullSnapshot 전체 목록일 때만, 내 휴대폰에서 따로 만든 같은 이름 분류를 공유 장부 분류와 하나로 합침
     * @return 새로 생기거나 바뀐 분류 수
     */
    suspend fun applyRemote(remote: List<RemoteCategory>, isFullSnapshot: Boolean = false): Int = applyLock.withLock {
        val remoteIds = remote.mapTo(mutableSetOf()) { it.remoteId }
        var changed = 0
        // 대분류를 먼저 넣어야 세부 분류가 부모를 찾음
        for (item in remote.sortedBy { it.parentRemoteId != null }) {
            val parentId = item.parentRemoteId?.let { categoryRepository.getByRemoteId(it)?.id }
            if (item.parentRemoteId != null && parentId == null) continue

            val local = categoryRepository.getByRemoteId(item.remoteId)
                ?: if (isFullSnapshot) findSameNameNotOnRemote(item, parentId, remoteIds) else null

            when {
                local == null -> {
                    categoryRepository.insertCategory(item.toEntity(id = 0L, parentId = parentId))
                    changed++
                }
                local.remoteId != item.remoteId -> {
                    // 두 휴대폰에서 따로 만든 같은 이름 분류: 로컬 id(거래가 가리키는 값)는 두고 원격 ID만 맞춤
                    val newer = if (local.updatedAt > item.updatedAt) local.copy(remoteId = item.remoteId) else item.toEntity(local.id, parentId)
                    categoryRepository.updateCategory(
                        newer.copy(syncStatus = if (local.updatedAt > item.updatedAt) SyncStatus.PENDING_UPLOAD else SyncStatus.SYNCED)
                    )
                    changed++
                }
                item.updatedAt > local.updatedAt -> {
                    categoryRepository.updateCategory(item.toEntity(local.id, parentId))
                    changed++
                }
            }
        }
        changed
    }

    /**
     * 공유 장부에 올릴 공용 분류: 공유 장부에 없거나 내 쪽이 더 최근에 바뀐 것 (숨긴 분류 포함)
     * @param remoteUpdatedAt 공유 장부에 이미 있는 분류의 원격 ID → updatedAt
     */
    suspend fun toUpload(remoteUpdatedAt: Map<String, Long>): List<Pair<Long, RemoteCategory>> {
        val all = categoryRepository.getAllBySpaceIncludingArchived(sharedId)
        val remoteIdById = all.associate { it.id to it.remoteId }
        return all
            .filter { cat -> remoteUpdatedAt[cat.remoteId]?.let { cat.updatedAt > it } ?: true }
            .map { cat -> cat.id to cat.toRemote(parentRemoteId = cat.parentId?.let { remoteIdById[it] }) }
    }

    /**
     * 올린 분류와, 공유 장부 쪽이 같거나 더 최근이라 올릴 필요가 없던 분류를 '동기화됨'으로
     * (그래야 다음 동기화 때 분류 목록을 다시 읽지 않음)
     */
    suspend fun markUploaded(uploadedIds: List<Long>, remoteUpdatedAt: Map<String, Long>) {
        val settled = categoryRepository.getAllBySpaceIncludingArchived(sharedId)
            .filter { it.syncStatus == SyncStatus.PENDING_UPLOAD }
            .filter { cat -> remoteUpdatedAt[cat.remoteId]?.let { it >= cat.updatedAt } ?: false }
            .map { it.id }
        val ids = (uploadedIds + settled).distinct()
        if (ids.isNotEmpty()) categoryRepository.markSynced(ids)
    }

    /** 거래를 올릴 때: 로컬 분류 id → 원격 ID */
    suspend fun remoteIdOf(localId: Long?): String? = localId?.let { categoryRepository.getById(it)?.remoteId }

    /** 거래를 받을 때: 원격 ID → 내 휴대폰의 분류 id (아직 없으면 null) */
    suspend fun localIdOf(remoteId: String?): Long? = remoteId?.let { categoryRepository.getByRemoteId(it)?.id }

    private suspend fun findSameNameNotOnRemote(item: RemoteCategory, parentId: Long?, remoteIds: Set<String>): CategoryEntity? =
        categoryRepository.getAllBySpaceIncludingArchived(sharedId).firstOrNull {
            !it.isArchived && it.parentId == parentId && it.remoteId !in remoteIds &&
                it.name.equals(item.name, ignoreCase = true)
        }

    private fun RemoteCategory.toEntity(id: Long, parentId: Long?) = CategoryEntity(
        id = id,
        spaceId = sharedId,
        parentId = parentId,
        transactionType = transactionType,
        name = name,
        emoji = emoji,
        colorHex = colorHex,
        sortOrder = sortOrder,
        isArchived = isArchived,
        syncStatus = SyncStatus.SYNCED,
        remoteId = remoteId,
        updatedAt = updatedAt
    )
}
