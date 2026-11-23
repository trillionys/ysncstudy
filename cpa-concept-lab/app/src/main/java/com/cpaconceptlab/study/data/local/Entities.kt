package com.cpaconceptlab.study.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * ── 콘텐츠 테이블 ──────────────────────────────────────────────
 * 콘텐츠는 assets 또는 가져온 팩에서 갱신된다. 개념·문항은 삭제하지 않고 isActive 로 비활성화한다.
 * 콘텐츠 테이블 사이의 외래키(문항-개념 연결)만 CASCADE 를 사용한다.
 *
 * ── 사용자 학습기록 테이블 ─────────────────────────────────────
 * 학습기록은 콘텐츠 테이블에 외래키를 걸지 않는다. 콘텐츠가 수정·재가져오기 되어도
 * 학습기록이 연쇄 삭제되지 않게 하기 위해서다. 연결은 안정적인 conceptId/questionId 로만 한다.
 */

@Entity(tableName = "subjects")
data class SubjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val sortOrder: Int,
    val isActive: Boolean,
)

@Entity(tableName = "chapters", indices = [Index("subjectId")])
data class ChapterEntity(
    @PrimaryKey val id: String,
    val code: String,
    val subjectId: String,
    val title: String,
    val englishTitle: String,
    val sortOrder: Int,
    /** 스키마 v3: 학습 파트 */
    @ColumnInfo(defaultValue = "") val partTitle: String = "",
)

@Entity(tableName = "sections", indices = [Index("chapterId")])
data class SectionEntity(
    @PrimaryKey val id: String,
    val chapterId: String,
    val title: String,
    val sortOrder: Int,
)

@Entity(tableName = "concepts", indices = [Index("chapterId"), Index("sectionId"), Index("subjectId")])
data class ConceptEntity(
    @PrimaryKey val id: String,
    val subjectId: String,
    val chapterId: String,
    val sectionId: String,
    val parentConceptId: String?,
    val koreanName: String,
    val englishName: String,
    val definition: String,
    val whyItMatters: String,
    val detailedExplanation: String,
    val comparison: String,
    val example: String,
    val formula: String?,
    val examTips: String,
    val commonMistakes: String,
    val relatedConceptIds: List<String>,
    val prerequisiteConceptIds: List<String>,
    val examReferenceNumbers: List<String>,
    val standardReference: String,
    val effectiveFrom: String?,
    val effectiveTo: String?,
    val isHistorical: Boolean,
    val requiresReview: Boolean,
    val revisionNote: String?,
    val originalExamYear: Int?,
    val reviewStatus: String,
    val isRequired: Boolean,
    val isActive: Boolean,
    val sortOrder: Int,
    val revision: Int,
    val contentVersion: Int,
    // 스키마 v3 추가 컬럼
    val recognitionRules: String? = null,
    val measurementRules: String? = null,
    val exceptions: String? = null,
    val journalEntries: String? = null,
    @ColumnInfo(defaultValue = "[]") val sourceProblemIds: List<String> = emptyList(),
    @ColumnInfo(defaultValue = "AUTHOR_REVIEWED") val verificationStatus: String = "AUTHOR_REVIEWED",
)

@Entity(tableName = "questions", indices = [Index("primaryConceptId"), Index("chapterId")])
data class QuestionEntity(
    @PrimaryKey val id: String,
    val primaryConceptId: String,
    val conceptIds: List<String>,
    val subjectId: String,
    val chapterId: String,
    val questionType: String,
    val questionText: String,
    val blankAnswer: String,
    val acceptedAnswers: List<String>,
    val unit: String?,
    val explanation: String,
    val confusion: String?,
    val calculation: String?,
    val difficulty: Int,
    val examReferenceNumbers: List<String>,
    val relatedConceptIds: List<String>,
    val standardReference: String,
    val effectiveFrom: String?,
    val effectiveTo: String?,
    val isHistorical: Boolean,
    val requiresReview: Boolean,
    val revisionNote: String?,
    val reviewStatus: String,
    val isActive: Boolean,
    val revision: Int,
    val sortOrder: Int,
    val contentVersion: Int,
    // 스키마 v3 추가 컬럼
    @ColumnInfo(defaultValue = "text") val inputType: String = "text",
    @ColumnInfo(defaultValue = "[]") val choices: List<String> = emptyList(),
    @ColumnInfo(defaultValue = "[]") val slotLabels: List<String> = emptyList(),
    @ColumnInfo(defaultValue = "[]") val slotAnswers: List<String> = emptyList(),
    val calcFormulaId: String? = null,
    /** 계산 입력값 JSON(이름 → 값) */
    val calcParams: String? = null,
    @ColumnInfo(defaultValue = "[]") val sourceProblemIds: List<String> = emptyList(),
)

@Entity(
    tableName = "question_concepts",
    primaryKeys = ["questionId", "conceptId"],
    indices = [Index("conceptId")],
    foreignKeys = [
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["id"],
            childColumns = ["questionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class QuestionConceptCrossRef(
    val questionId: String,
    val conceptId: String,
)

@Entity(tableName = "exam_scopes")
data class ExamScopeEntity(
    @PrimaryKey val number: String,
    val subjectId: String,
    val title: String,
    val conceptIds: List<String>,
)

/** 스키마 v3: 원본 문제 출처(NEW-INV-001 등). 원본 이미지를 판독하지 못한 항목은 검수 상태로 구분한다. */
@Entity(tableName = "problem_sources")
data class ProblemSourceEntity(
    @PrimaryKey val sourceId: String,
    val sourceFile: String?,
    val sourcePage: String?,
    val originalNumber: String?,
    val topic: String,
    val conceptIds: List<String>,
    val formula: String?,
    val solutionSteps: String?,
    val keyTrap: String?,
    val linkedQuestionIds: List<String>,
    val readingConfidence: String,
    val verificationStatus: String,
    val reviewRequired: Boolean,
    val note: String?,
)

/** 스키마 v3: 지정 학습범위 체크리스트 */
@Entity(tableName = "scope_items")
data class ScopeItemEntity(
    @PrimaryKey val id: String,
    val area: String,
    val item: String,
    val conceptId: String?,
    val covered: Boolean,
    val sortOrder: Int,
)

/** 콘텐츠 가져오기 이력. 번들 콘텐츠 버전 비교와 가져오기 결과 표시에 사용한다. */
@Entity(tableName = "content_versions")
data class ContentVersionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val version: Int,
    val source: String,
    val packId: String,
    val importedAt: Long,
    val conceptCount: Int,
    val questionCount: Int,
    val insertedConcepts: Int,
    val updatedConcepts: Int,
    val insertedQuestions: Int,
    val updatedQuestions: Int,
)

/** 사용자가 직접 가져온 콘텐츠 팩 원본. 전체 백업에 포함되어 재설치 후에도 복원된다. */
@Entity(tableName = "imported_packs")
data class ImportedPackEntity(
    @PrimaryKey val packKey: String,
    val fileName: String,
    val importedAt: Long,
    val json: String,
)

/* ───────────── 사용자 학습기록 ───────────── */

@Entity(
    tableName = "question_attempts",
    indices = [
        Index("questionId"),
        Index("attemptedAt"),
        Index(value = ["questionId", "mode", "attemptedAt"], unique = true),
    ],
)
data class QuestionAttemptEntity(
    @PrimaryKey(autoGenerate = true) val attemptId: Long = 0,
    val questionId: String,
    val mode: String,
    val userAnswer: String,
    val isCorrect: Boolean,
    val attemptedAt: Long,
    val responseTimeMs: Long,
)

@Entity(tableName = "question_progress", primaryKeys = ["questionId", "mode"])
data class QuestionProgressEntity(
    val questionId: String,
    val mode: String,
    val firstAnsweredAt: Long,
    val totalAttempts: Int,
    val correctAttempts: Int,
    val wrongAttempts: Int,
    val streak: Int,
    val lastAnsweredAt: Long,
    val lastCorrect: Boolean,
    val totalResponseMs: Long,
    /** 간격 반복 단계(0 = 처음/오답 직후). */
    val stage: Int,
    val nextReviewAt: Long,
    /** 0.0~1.0 숙달도 */
    val mastery: Double,
)

@Entity(tableName = "concept_progress")
data class ConceptProgressEntity(
    @PrimaryKey val conceptId: String,
    val firstStudiedAt: Long?,
    val lastStudiedAt: Long?,
    val viewCount: Int,
    val basicAttemptCount: Int,
    val basicCorrectCount: Int,
    val advancedAttemptCount: Int,
    val advancedCorrectCount: Int,
    val masteryLevel: String,
    val nextReviewAt: Long?,
    val isBookmarked: Boolean,
    /** 스키마 v2 에서 추가: 개념별 개인 메모 */
    @ColumnInfo(defaultValue = "") val memo: String = "",
)

/** 오답노트. 현재 해결 여부(isResolved)와 과거 오답 이력(wrongCount, first/lastWrongAt)을 분리해 관리한다. */
@Entity(tableName = "wrong_notes", primaryKeys = ["questionId", "mode"])
data class WrongNoteEntity(
    val questionId: String,
    val mode: String,
    val lastUserAnswer: String,
    val wrongCount: Int,
    val firstWrongAt: Long,
    val lastWrongAt: Long,
    val isResolved: Boolean,
    val resolvedAt: Long?,
)

@Entity(tableName = "study_days")
data class StudyDayEntity(
    /** LocalDate.toEpochDay() */
    @PrimaryKey val epochDay: Long,
    val solvedCount: Int,
    val correctCount: Int,
    val studyMs: Long,
)
