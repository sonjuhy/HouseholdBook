package com.budget.householdbook.domain.model

/**
 * 장부 공간의 멤버 이름 목록(`memberNamesCsv`)을 화면에 보여줄 때 쓰는 순수 로직
 * - 예전 버전은 이름 대신 예시 이름('홍길동')이나 '(혼자 쓰기)' 꼬리표를 저장해 두었으므로 걷어내고 보여줌
 */
object MemberNames {

    /** 예전 버전이 이름이 없을 때 넣어 두던 예시 이름 (저장돼 있으면 이름이 없는 것으로 봄) */
    const val LEGACY_PLACEHOLDER_NAME = "홍길동"

    private const val LEGACY_SOLO_SUFFIX = "(혼자 쓰기)"

    /** 실제 이름만 남긴 목록. 예시 이름은 [myName]으로 바꿈 */
    fun clean(csv: String, myName: String): List<String> =
        csv.split(",")
            .map { it.trim().removeSuffix(LEGACY_SOLO_SUFFIX).trim() }
            .map { if (it == LEGACY_PLACEHOLDER_NAME) myName else it }
            .filter { it.isNotBlank() }
            .distinct()

    /** 홈·설정에 보여줄 한 줄 요약: 공유 전이면 '나만 사용 중', 공유 중이면 '엄마, 아빠' */
    fun summary(csv: String, isShared: Boolean, myName: String): String =
        if (!isShared) "나만 사용 중" else clean(csv, myName).joinToString(", ").ifBlank { myName }
}
