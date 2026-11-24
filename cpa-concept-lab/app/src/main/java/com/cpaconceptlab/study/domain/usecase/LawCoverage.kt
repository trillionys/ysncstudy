package com.cpaconceptlab.study.domain.usecase

import com.cpaconceptlab.study.data.local.LawArticleEntity

/** 조문 커버리지 집계(법률·편 단위). 빌드 검증 리포트와 같은 기준을 앱에서도 다시 계산한다. */
object LawCoverage {
    data class Row(
        val group: String,
        val total: Int,
        val live: Int,
        val completed: Int,
        val integrated: Int,
        val writing: Int,
        val review: Int,
        val deleted: Int,
        val outOfScope: Int,
        /** 검토 필요 조문 중 개념·문항이 연결된 것 */
        val reviewLinked: Int = 0,
    ) {
        /** 개념·문항이 연결된 현행 조문 수 */
        val linked: Int get() = completed + integrated + reviewLinked
        val rate: Double get() = if (live == 0) 0.0 else linked.toDouble() / live
    }

    val GROUPS = listOf(
        Triple("상법총칙", "상법", "제1편"), Triple("상행위", "상법", "제2편"), Triple("회사법", "상법", "제3편"),
        Triple("공인회계사법", "공인회계사법", null), Triple("외부감사법", "외부감사법", null),
    )

    /** 세법개론(TX): 법률별 그룹. 지방세법은 취득세·등록면허세·재산세 장과 총칙만 시험범위(나머지는 OUT_OF_SCOPE) */
    val TAX_GROUPS = listOf(
        Triple("국세기본법", "국세기본법", null), Triple("법인세법", "법인세법", null), Triple("소득세법", "소득세법", null),
        Triple("부가가치세법", "부가가치세법", null), Triple("상속세 및 증여세법", "상증법", null),
        Triple("지방세법(취득·등록면허·재산세)", "지방세법", null), Triple("조세특례제한법", "조특법", null),
    )

    fun groupsOf(subjectId: String) = if (subjectId == "TX") TAX_GROUPS else GROUPS

    fun rows(articles: List<LawArticleEntity>, subjectId: String = "CPA-LAW"): List<Row> = groupsOf(subjectId).map { (name, law, part) ->
        val a = articles.filter { it.lawShortName == law && (part == null || it.part.startsWith(part)) }
        Row(
            group = name, total = a.size, live = a.count { !it.deleted },
            completed = a.count { it.status == "COMPLETED" }, integrated = a.count { it.status == "INTEGRATED" },
            writing = a.count { it.status == "WRITING" }, review = a.count { it.status == "REVIEW_NEEDED" },
            deleted = a.count { it.status == "DELETED" }, outOfScope = a.count { it.status == "OUT_OF_SCOPE" },
            reviewLinked = a.count { it.status == "REVIEW_NEEDED" && (it.conceptIds.isNotEmpty() || it.questionCount > 0) },
        )
    }
}
