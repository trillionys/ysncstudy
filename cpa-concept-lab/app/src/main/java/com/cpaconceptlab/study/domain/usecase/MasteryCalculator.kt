package com.cpaconceptlab.study.domain.usecase

import com.cpaconceptlab.study.data.local.QuestionProgressEntity
import com.cpaconceptlab.study.domain.model.MasteryLevel
import com.cpaconceptlab.study.domain.model.QuizMode

/**
 * 개념 숙달 단계 계산.
 *
 * - 미학습: 문항 풀이와 개념 열람이 모두 없음
 * - 학습 시작: 개념을 열람했으나 문항 풀이 없음
 * - 기본문제 연습 중 / 숙달: 기본 모드에서 모든 연결 문항을 맞힌 적이 있고, 최근 답이 정답이며, 기본 정답률 ≥ 70% 이면 숙달
 * - 심화문제 연습 중 / 숙달: 심화 모드에 같은 기준 적용
 * - 복습 필요: 복습 예정일이 지났거나, 숙달 이후 최근 풀이가 오답인 문항이 있음
 * - 완전 숙달: 기본·심화 모두 숙달이고 심화 모드 모든 문항이 3단계(기본 간격상 7일) 이상, 복습 예정 문항 없음
 */
object MasteryCalculator {

    const val MASTERY_ACCURACY = 0.7
    const val FULL_MASTERY_STAGE = 3

    fun compute(
        questionIds: Collection<String>,
        progress: Map<Pair<String, String>, QuestionProgressEntity>,
        viewed: Boolean,
        now: Long,
    ): MasteryLevel {
        if (questionIds.isEmpty()) return if (viewed) MasteryLevel.STARTED else MasteryLevel.NOT_STARTED
        val basic = questionIds.mapNotNull { progress[it to QuizMode.BASIC.name] }
        val adv = questionIds.mapNotNull { progress[it to QuizMode.ADVANCED.name] }
        if (basic.isEmpty() && adv.isEmpty()) return if (viewed) MasteryLevel.STARTED else MasteryLevel.NOT_STARTED

        fun mastered(rows: List<QuestionProgressEntity>): Boolean {
            if (rows.size < questionIds.size) return false
            if (rows.any { it.correctAttempts == 0 || !it.lastCorrect }) return false
            val total = rows.sumOf { it.totalAttempts }
            val correct = rows.sumOf { it.correctAttempts }
            return total > 0 && correct.toDouble() / total >= MASTERY_ACCURACY
        }

        val basicMastered = mastered(basic)
        val advMastered = mastered(adv)
        val anyDue = (basic + adv).any { it.nextReviewAt <= now }
        val anyLastWrong = (basic + adv).any { !it.lastCorrect }

        if (advMastered && basicMastered && !anyDue && adv.all { it.stage >= FULL_MASTERY_STAGE }) {
            return MasteryLevel.FULL_MASTERY
        }
        if (anyDue) return MasteryLevel.REVIEW_NEEDED
        if ((basicMastered || adv.isNotEmpty()) && anyLastWrong) return MasteryLevel.REVIEW_NEEDED
        if (advMastered) return MasteryLevel.ADVANCED_MASTERED
        if (adv.isNotEmpty()) return MasteryLevel.ADVANCED_PRACTICING
        if (basicMastered) return MasteryLevel.BASIC_MASTERED
        return MasteryLevel.BASIC_PRACTICING
    }
}
