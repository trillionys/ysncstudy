package com.cpaconceptlab.study.domain.usecase

import com.cpaconceptlab.study.data.local.QuestionProgressEntity
import java.util.concurrent.TimeUnit

/**
 * 간격 반복 복습 스케줄러.
 *
 * 기본 간격: 1일 → 3일 → 7일 → 14일 → 30일 (설정에서 변경 가능)
 *  - 정답: 단계 +1, 다음 간격 적용
 *  - 정답이지만 응답 시간이 매우 김(느린 회상): 단계를 올리지 않고 현재 단계 간격 유지
 *  - 오답: 단계 0 으로 초기화, 첫 간격(기본 1일) 후 복습
 *  - 누적 정답률이 낮은 문항(< 60%, 3회 이상 풀이)은 간격을 30% 단축
 */
class ReviewScheduler(
    intervalsDays: List<Int> = DEFAULT_INTERVALS,
    private val slowResponseMs: Long = 60_000,
) {
    val intervals: List<Int> = intervalsDays.filter { it > 0 }.ifEmpty { DEFAULT_INTERVALS }

    data class Outcome(val stage: Int, val nextReviewAt: Long, val mastery: Double)

    fun schedule(previous: QuestionProgressEntity?, isCorrect: Boolean, responseMs: Long, now: Long): Outcome {
        val prevStage = previous?.stage ?: 0
        val total = (previous?.totalAttempts ?: 0) + 1
        val correct = (previous?.correctAttempts ?: 0) + if (isCorrect) 1 else 0
        val accuracy = correct.toDouble() / total

        val stage = when {
            !isCorrect -> 0
            responseMs > slowResponseMs && prevStage > 0 -> prevStage
            else -> (prevStage + 1).coerceAtMost(intervals.size)
        }
        val intervalDays = when {
            !isCorrect -> intervals.first().toDouble()
            else -> intervals[(stage - 1).coerceIn(0, intervals.lastIndex)].toDouble()
        }
        val adjusted = if (isCorrect && total >= 3 && accuracy < 0.6) intervalDays * 0.7 else intervalDays
        val next = now + (adjusted * TimeUnit.DAYS.toMillis(1)).toLong()
        val mastery = (0.6 * accuracy + 0.4 * (stage.toDouble() / intervals.size)).coerceIn(0.0, 1.0)
        return Outcome(stage, next, mastery)
    }

    companion object {
        val DEFAULT_INTERVALS = listOf(1, 3, 7, 14, 30)

        const val MAX_INTERVAL_DAYS = 3650
        const val MAX_STEPS = 12

        fun parse(text: String?): List<Int> =
            text.orEmpty().split(",", " ", "→").mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..MAX_INTERVAL_DAYS }
                .ifEmpty { DEFAULT_INTERVALS }

        /** 직접 설정 검증: 저장하지 않을 값이면 이유를 돌려준다 */
        fun validate(steps: List<Int?>): String? = when {
            steps.isEmpty() -> "복습 단계를 1개 이상 두세요."
            steps.size > MAX_STEPS -> "복습 단계는 최대 ${MAX_STEPS}개입니다."
            steps.any { it == null } -> "모든 단계에 일(day) 수를 숫자로 입력하세요."
            steps.any { it!! < 1 } -> "복습 간격은 1일 이상이어야 합니다."
            steps.any { it!! > MAX_INTERVAL_DAYS } -> "복습 간격은 ${MAX_INTERVAL_DAYS}일 이하로 입력하세요."
            steps.zipWithNext().any { (a, b) -> b!! <= a!! } -> "각 단계는 이전 단계보다 길어야 합니다. 간격이 점점 늘어나야 오래 기억하는 데 도움이 됩니다."
            else -> null
        }

        /** 미리보기: 오늘 새 개념을 학습하고 매번 정답을 맞힌다고 가정한 누적 복습일(오늘 기준 며칠 뒤) */
        fun previewOffsets(intervals: List<Int>): List<Int> = intervals.runningReduce { acc, d -> acc + d }
    }
}
