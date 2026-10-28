package com.budget.householdbook.ui

import com.budget.householdbook.data.local.entity.LedgerSpaceEntity
import com.budget.householdbook.domain.model.SpaceType

/** 공간을 짧게: 공용 / 개인 (카드·버튼 안에 들어가도록) */
fun LedgerSpaceEntity.shortLabel(): String =
    if (type == SpaceType.SHARED) "공용" else "개인"
