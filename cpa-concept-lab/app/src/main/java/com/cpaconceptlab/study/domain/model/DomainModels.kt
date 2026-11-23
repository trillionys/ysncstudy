package com.cpaconceptlab.study.domain.model

import java.time.LocalDate

enum class QuizMode(val label: String, val description: String) {
    BASIC("기본", "초성 힌트 제공"),
    ADVANCED("심화", "힌트 없음");

    companion object {
        fun of(name: String?): QuizMode = entries.firstOrNull { it.name == name } ?: BASIC
    }
}

/** 출제 범위 */
enum class ScopeType(val label: String) {
    ALL("전체 범위"),
    PART("학습 파트"),
    CHAPTER("대단원"),
    SECTION("중단원"),
    CONCEPT("특정 개념"),
    EXAM("기출 출제범위"),
    UNSTUDIED("미학습 개념"),
    WRONG("오답 개념"),
    WEAK("취약 개념"),
    RECENT("최근 추가 개념"),
    DUE("복습 예정"),
    MOCK("랜덤 모의테스트"),
    QUESTION("선택 문항 다시 풀기");

    companion object {
        fun of(name: String?): ScopeType = entries.firstOrNull { it.name == name } ?: ALL
    }
}

/** 문항 형식 필터 */
enum class QuestionKind(val label: String) {
    ALL("모든 유형"),
    BLANK("빈칸만"),
    CALC("계산만"),
    OPTIONS("선택·매칭·분류");

    companion object {
        fun of(name: String?): QuestionKind = entries.firstOrNull { it.name == name } ?: ALL
    }
}

/**
 * 출제 범위 + 필터(난이도 범위, 문항 형식).
 * 인코딩: "TYPE[:id][~min-max~KIND]" — 필터가 기본값이면 기존(v1·v2) 형식과 같다.
 */
data class QuizScope(
    val type: ScopeType,
    val id: String? = null,
    val minLevel: Int = 1,
    val maxLevel: Int = 5,
    val kind: QuestionKind = QuestionKind.ALL,
) {
    val hasFilter: Boolean get() = minLevel != 1 || maxLevel != 5 || kind != QuestionKind.ALL

    fun encode(): String {
        val base = if (id == null) type.name else "${type.name}:$id"
        return if (hasFilter) "$base~$minLevel-$maxLevel~${kind.name}" else base
    }

    fun levelLabel(): String = if (minLevel == 1 && maxLevel == 5) "전체 난이도" else if (minLevel == maxLevel) "Level $minLevel" else "Level $minLevel~$maxLevel"

    companion object {
        fun decode(s: String?): QuizScope {
            if (s.isNullOrBlank()) return QuizScope(ScopeType.ALL)
            val segs = s.split("~")
            val parts = segs[0].split(":", limit = 2)
            val type = ScopeType.of(parts[0])
            val id = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
            val lv = segs.getOrNull(1)?.split("-")?.mapNotNull { it.toIntOrNull() }
            val min = lv?.getOrNull(0)?.coerceIn(1, 5) ?: 1
            val max = lv?.getOrNull(1)?.coerceIn(min, 5) ?: 5
            return QuizScope(type, id, min, max, QuestionKind.of(segs.getOrNull(2)))
        }
    }
}

/** 개념 숙달 단계 */
enum class MasteryLevel(val label: String, val score: Double) {
    NOT_STARTED("미학습", 0.0),
    STARTED("학습 시작", 0.1),
    BASIC_PRACTICING("기본문제 연습 중", 0.3),
    BASIC_MASTERED("기본문제 숙달", 0.5),
    ADVANCED_PRACTICING("심화문제 연습 중", 0.65),
    ADVANCED_MASTERED("심화문제 숙달", 0.85),
    REVIEW_NEEDED("복습 필요", 0.4),
    FULL_MASTERY("완전 숙달", 1.0);

    companion object {
        fun of(name: String?): MasteryLevel = entries.firstOrNull { it.name == name } ?: NOT_STARTED
    }
}

/** 단원 상태 분류(통계용) */
enum class ChapterStatus(val label: String) { NOT_STARTED("미학습"), LEARNING("학습 중"), REVIEW("복습 필요"), MASTERED("숙달") }

/** 기준 적용 상태 표시 */
enum class StandardStatus(val label: String) {
    CURRENT("현재 기준"),
    UPCOMING("시행 예정 기준"),
    HISTORICAL("과거 기준"),
}

object StandardStatusResolver {
    /**
     * effectiveFrom/effectiveTo/isHistorical 로 현재 기준·과거 기준·시행 예정 기준을 판정한다.
     * '개정사항 확인 필요' 표시는 requiresReview 플래그로 별도 표시한다.
     */
    fun resolve(effectiveFrom: String?, effectiveTo: String?, isHistorical: Boolean, today: LocalDate): StandardStatus {
        if (isHistorical) return StandardStatus.HISTORICAL
        val to = effectiveTo?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (to != null && today.isAfter(to)) return StandardStatus.HISTORICAL
        val from = effectiveFrom?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (from != null && today.isBefore(from)) return StandardStatus.UPCOMING
        return StandardStatus.CURRENT
    }
}
