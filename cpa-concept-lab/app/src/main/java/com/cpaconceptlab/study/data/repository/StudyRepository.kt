package com.cpaconceptlab.study.data.repository

import androidx.room.withTransaction
import com.cpaconceptlab.study.data.local.AppDatabase
import com.cpaconceptlab.study.data.local.ConceptProgressEntity
import com.cpaconceptlab.study.data.local.QuestionAttemptEntity
import com.cpaconceptlab.study.data.local.QuestionEntity
import com.cpaconceptlab.study.data.local.QuestionProgressEntity
import com.cpaconceptlab.study.data.local.StudyDayEntity
import com.cpaconceptlab.study.data.local.WrongNoteEntity
import com.cpaconceptlab.study.domain.model.MasteryLevel
import com.cpaconceptlab.study.domain.model.QuizMode
import com.cpaconceptlab.study.domain.usecase.MasteryCalculator
import com.cpaconceptlab.study.domain.usecase.ReviewScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.ZoneId

/** 사용자 학습기록 저장소. 모든 기록 변경은 트랜잭션으로 수행된다. */
class StudyRepository(private val db: AppDatabase, private val zone: ZoneId = ZoneId.systemDefault()) {
    private val dao = db.progressDao()

    val userSnapshot: Flow<UserSnapshot> = combine(
        dao.questionProgressFlow(), dao.conceptProgressFlow(), dao.wrongNotesFlow(), dao.studyDaysFlow(),
    ) { qp, cp, wn, sd -> UserSnapshot(qp, cp, wn, sd) }

    suspend fun currentSnapshot(): UserSnapshot =
        UserSnapshot(dao.allQuestionProgress(), dao.allConceptProgress(), dao.allWrongNotes(), dao.allStudyDays())

    data class AnswerRecord(val progress: QuestionProgressEntity, val wrongNote: WrongNoteEntity?)

    /**
     * 답안 제출 결과 저장:
     * 풀이 기록 추가 → 문항 진도(모드별)·복습 일정 갱신 → 오답노트 갱신(이력 보존) → 개념 진도 재계산 → 학습일 누적
     */
    suspend fun recordAnswer(
        question: QuestionEntity,
        mode: QuizMode,
        userAnswer: String,
        isCorrect: Boolean,
        responseMs: Long,
        now: Long,
        scheduler: ReviewScheduler,
        conceptQuestionIds: Map<String, List<String>>,
    ): AnswerRecord = db.withTransaction {
        // 같은 밀리초에 중복 제출되는 경우를 피하기 위해 고유 시각 보정
        var at = now
        while (dao.insertAttempt(QuestionAttemptEntity(0, question.id, mode.name, userAnswer.take(200), isCorrect, at, responseMs)) == -1L) at++

        val prev = dao.questionProgress(question.id, mode.name)
        val out = scheduler.schedule(prev, isCorrect, responseMs, at)
        val progress = QuestionProgressEntity(
            questionId = question.id,
            mode = mode.name,
            firstAnsweredAt = prev?.firstAnsweredAt ?: at,
            totalAttempts = (prev?.totalAttempts ?: 0) + 1,
            correctAttempts = (prev?.correctAttempts ?: 0) + if (isCorrect) 1 else 0,
            wrongAttempts = (prev?.wrongAttempts ?: 0) + if (isCorrect) 0 else 1,
            streak = if (isCorrect) (prev?.streak ?: 0) + 1 else 0,
            lastAnsweredAt = at,
            lastCorrect = isCorrect,
            totalResponseMs = (prev?.totalResponseMs ?: 0) + responseMs,
            stage = out.stage,
            nextReviewAt = out.nextReviewAt,
            mastery = out.mastery,
        )
        dao.upsertQuestionProgress(progress)

        // 오답노트: 다시 맞혀도 이력(wrongCount, first/lastWrongAt)은 삭제하지 않고 해결 여부만 바꾼다.
        val oldNote = dao.wrongNote(question.id, mode.name)
        val note = when {
            !isCorrect -> WrongNoteEntity(
                questionId = question.id, mode = mode.name, lastUserAnswer = userAnswer.take(200),
                wrongCount = (oldNote?.wrongCount ?: 0) + 1, firstWrongAt = oldNote?.firstWrongAt ?: at,
                lastWrongAt = at, isResolved = false, resolvedAt = null,
            )
            oldNote != null && !oldNote.isResolved -> oldNote.copy(isResolved = true, resolvedAt = at)
            else -> oldNote
        }
        if (note != null && note != oldNote) dao.upsertWrongNote(note)

        // 개념 진도(문항이 연결된 모든 개념)
        val allProgress = dao.allQuestionProgress().associateBy { it.questionId to it.mode }
        for (cid in question.conceptIds.distinct()) {
            val cp = dao.conceptProgress(cid) ?: emptyConceptProgress(cid)
            val qids = conceptQuestionIds[cid].orEmpty()
            val level = MasteryCalculator.compute(qids, allProgress, cp.viewCount > 0, at)
            val nextReview = qids.flatMap { id -> QuizMode.entries.mapNotNull { allProgress[id to it.name]?.nextReviewAt } }.minOrNull()
            val basic = mode == QuizMode.BASIC
            dao.upsertConceptProgress(
                cp.copy(
                    firstStudiedAt = cp.firstStudiedAt ?: at,
                    lastStudiedAt = at,
                    basicAttemptCount = cp.basicAttemptCount + if (basic) 1 else 0,
                    basicCorrectCount = cp.basicCorrectCount + if (basic && isCorrect) 1 else 0,
                    advancedAttemptCount = cp.advancedAttemptCount + if (!basic) 1 else 0,
                    advancedCorrectCount = cp.advancedCorrectCount + if (!basic && isCorrect) 1 else 0,
                    masteryLevel = level.name,
                    nextReviewAt = nextReview,
                ),
            )
        }

        // 학습일
        val day = Instant.ofEpochMilli(at).atZone(zone).toLocalDate().toEpochDay()
        val sd = dao.studyDay(day)
        dao.upsertStudyDay(
            StudyDayEntity(
                epochDay = day,
                solvedCount = (sd?.solvedCount ?: 0) + 1,
                correctCount = (sd?.correctCount ?: 0) + if (isCorrect) 1 else 0,
                studyMs = (sd?.studyMs ?: 0) + responseMs.coerceAtMost(10 * 60_000),
            ),
        )
        AnswerRecord(progress, note)
    }

    /** 개념 학습 화면 열람 기록(미학습 → 학습 시작) */
    suspend fun markConceptViewed(conceptId: String, now: Long) = db.withTransaction {
        val cp = dao.conceptProgress(conceptId) ?: emptyConceptProgress(conceptId)
        val level = MasteryLevel.of(cp.masteryLevel)
        dao.upsertConceptProgress(
            cp.copy(
                viewCount = cp.viewCount + 1,
                firstStudiedAt = cp.firstStudiedAt ?: now,
                lastStudiedAt = now,
                masteryLevel = if (level == MasteryLevel.NOT_STARTED) MasteryLevel.STARTED.name else cp.masteryLevel,
            ),
        )
    }

    suspend fun toggleBookmark(conceptId: String) = db.withTransaction {
        val cp = dao.conceptProgress(conceptId) ?: emptyConceptProgress(conceptId)
        dao.upsertConceptProgress(cp.copy(isBookmarked = !cp.isBookmarked))
    }

    suspend fun saveMemo(conceptId: String, memo: String) = db.withTransaction {
        val cp = dao.conceptProgress(conceptId) ?: emptyConceptProgress(conceptId)
        dao.upsertConceptProgress(cp.copy(memo = memo.take(2000)))
    }

    suspend fun attemptsFor(questionId: String) = dao.attemptsFor(questionId)

    /** 최근 N분 이내에 푼 문항(반복 출제 억제용) */
    suspend fun recentlyAnswered(withinMs: Long, now: Long): Set<String> =
        dao.allQuestionProgress().filter { now - it.lastAnsweredAt < withinMs }.map { it.questionId }.toSet()

    companion object {
        fun emptyConceptProgress(conceptId: String) = ConceptProgressEntity(
            conceptId = conceptId, firstStudiedAt = null, lastStudiedAt = null, viewCount = 0,
            basicAttemptCount = 0, basicCorrectCount = 0, advancedAttemptCount = 0, advancedCorrectCount = 0,
            masteryLevel = MasteryLevel.NOT_STARTED.name, nextReviewAt = null, isBookmarked = false,
        )
    }
}
