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

        fun parse(text: String?): List<Int> =
            text.orEmpty().split(",", " ", "→").mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..365 }
                .ifEmpty { DEFAULT_INTERVALS }
    }
}
