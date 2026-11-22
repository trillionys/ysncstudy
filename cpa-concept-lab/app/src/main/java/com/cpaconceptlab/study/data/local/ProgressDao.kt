package com.cpaconceptlab.study.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressDao {
    // ── 문항 풀이 기록 ──
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAttempt(item: QuestionAttemptEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAttempts(items: List<QuestionAttemptEntity>): List<Long>

    @Query("SELECT * FROM question_attempts ORDER BY attemptedAt")
    suspend fun allAttempts(): List<QuestionAttemptEntity>

    @Query("SELECT * FROM question_attempts WHERE questionId = :questionId ORDER BY attemptedAt DESC")
    suspend fun attemptsFor(questionId: String): List<QuestionAttemptEntity>

    @Query("SELECT COUNT(*) FROM question_attempts")
    suspend fun attemptCount(): Int

    // ── 문항 진도 ──
    @Upsert suspend fun upsertQuestionProgress(item: QuestionProgressEntity)
    @Upsert suspend fun upsertQuestionProgress(items: List<QuestionProgressEntity>)

    @Query("SELECT * FROM question_progress WHERE questionId = :questionId AND mode = :mode")
    suspend fun questionProgress(questionId: String, mode: String): QuestionProgressEntity?

    @Query("SELECT * FROM question_progress")
    suspend fun allQuestionProgress(): List<QuestionProgressEntity>

    @Query("SELECT * FROM question_progress")
    fun questionProgressFlow(): Flow<List<QuestionProgressEntity>>

    // ── 개념 진도 ──
    @Upsert suspend fun upsertConceptProgress(item: ConceptProgressEntity)
    @Upsert suspend fun upsertConceptProgress(items: List<ConceptProgressEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertConceptProgressIfAbsent(items: List<ConceptProgressEntity>): List<Long>

    @Query("SELECT * FROM concept_progress WHERE conceptId = :conceptId")
    suspend fun conceptProgress(conceptId: String): ConceptProgressEntity?

    @Query("SELECT * FROM concept_progress")
    suspend fun allConceptProgress(): List<ConceptProgressEntity>

    @Query("SELECT * FROM concept_progress")
    fun conceptProgressFlow(): Flow<List<ConceptProgressEntity>>

    // ── 오답노트 ──
    @Upsert suspend fun upsertWrongNote(item: WrongNoteEntity)
    @Upsert suspend fun upsertWrongNotes(items: List<WrongNoteEntity>)

    @Query("SELECT * FROM wrong_notes WHERE questionId = :questionId AND mode = :mode")
    suspend fun wrongNote(questionId: String, mode: String): WrongNoteEntity?

    @Query("SELECT * FROM wrong_notes")
    suspend fun allWrongNotes(): List<WrongNoteEntity>

    @Query("SELECT * FROM wrong_notes ORDER BY lastWrongAt DESC")
    fun wrongNotesFlow(): Flow<List<WrongNoteEntity>>

    // ── 학습일 ──
    @Upsert suspend fun upsertStudyDay(item: StudyDayEntity)
    @Upsert suspend fun upsertStudyDays(items: List<StudyDayEntity>)

    @Query("SELECT * FROM study_days WHERE epochDay = :day")
    suspend fun studyDay(day: Long): StudyDayEntity?

    @Query("SELECT * FROM study_days ORDER BY epochDay")
    suspend fun allStudyDays(): List<StudyDayEntity>

    @Query("SELECT * FROM study_days ORDER BY epochDay")
    fun studyDaysFlow(): Flow<List<StudyDayEntity>>

    // ── 복원(대체) 시에만 사용: 사용자 학습기록 전체 삭제. 콘텐츠 테이블은 건드리지 않는다. ──
    @Query("DELETE FROM question_attempts") suspend fun clearAttempts()
    @Query("DELETE FROM question_progress") suspend fun clearQuestionProgress()
    @Query("DELETE FROM concept_progress") suspend fun clearConceptProgress()
    @Query("DELETE FROM wrong_notes") suspend fun clearWrongNotes()
    @Query("DELETE FROM study_days") suspend fun clearStudyDays()
}
