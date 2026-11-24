package com.cpaconceptlab.study.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 버전이 있는 학습 콘텐츠의 JSON 구조.
 *
 * 앱에 포함된 콘텐츠(assets/content)와 사용자가 가져오는 확장 콘텐츠 팩이 같은 구조를 사용한다.
 * 모든 목록은 선택 항목이므로, 하나의 팩에 개념만 또는 문항만 담을 수도 있다.
 */
@Serializable
data class ContentManifest(
    val formatVersion: Int,
    val contentVersion: Int,
    val contentId: String = "bundled",
    val generatedAt: String? = null,
    val files: List<String>,
    val conceptCount: Int = 0,
    val questionCount: Int = 0,
    val requiredConceptIds: List<String> = emptyList(),
)

@Serializable
data class ContentPack(
    val formatVersion: Int = 1,
    val contentVersion: Int,
    val packId: String = "pack",
    val generatedAt: String? = null,
    val subjects: List<SubjectDto> = emptyList(),
    val chapters: List<ChapterDto> = emptyList(),
    val sections: List<SectionDto> = emptyList(),
    val examScopes: List<ExamScopeDto> = emptyList(),
    val concepts: List<ConceptDto> = emptyList(),
    val questions: List<QuestionDto> = emptyList(),
    /** v3: 원본 문제 출처(교재 문항 번호 → 개념·문항 연결, 판독 신뢰도·검수 상태) */
    val problemSources: List<ProblemSourceDto> = emptyList(),
    /** v3: 지정 학습범위 체크리스트(세부항목 → 연결 개념) */
    val scopeItems: List<ScopeItemDto> = emptyList(),
    /** v4(기업법): 조문 원문·커버리지 상태 */
    val lawArticles: List<LawArticleDto> = emptyList(),
    /** v4: 숫자·기간·정족수 규칙 DB */
    val numericRules: List<NumericRuleDto> = emptyList(),
    /** v4: 판례 법리 노트(사건번호를 확인하지 못한 경우 비움) */
    val caseNotes: List<CaseNoteDto> = emptyList(),
    /** v4: 시험 출제 영역과 공고 비중 */
    val examAreas: List<ExamAreaDto> = emptyList(),
    /** v4: 법령 버전(공포일·시행일·출처) */
    val lawVersions: List<LawVersionDto> = emptyList(),
    /** v5: 과목별 공식 출제범위 마스터 목차 */
    val curriculumItems: List<CurriculumItemDto> = emptyList(),
)

/** 조문 1개. status: COMPLETED·INTEGRATED·WRITING·OUT_OF_SCOPE·DELETED·NOT_IN_FORCE·REVIEW_NEEDED */
@Serializable
data class LawArticleDto(
    val id: String,
    val lawName: String,
    val lawShortName: String,
    val articleId: String,
    val articleNo: Int,
    val branch: Int = 0,
    val title: String = "",
    val text: String = "",
    val part: String = "",
    val chapter: String = "",
    val deleted: Boolean = false,
    val inScope: Boolean = true,
    val status: String,
    val statusLabel: String = "",
    val note: String? = null,
    val examArea: String? = null,
    val conceptIds: List<String> = emptyList(),
    val questionCount: Int = 0,
    val lawVersion: String = "",
    val effectiveDate: String? = null,
    val sourceUrl: String? = null,
    val verifiedAt: String? = null,
)

@Serializable
data class NumericRuleDto(
    val ruleId: String,
    val lawName: String,
    val article: String,
    val ruleName: String,
    val subject: String = "",
    val condition: String = "",
    val value: String,
    val unit: String = "",
    val exception: String? = null,
    val effectiveDate: String? = null,
    val sourceUrl: String? = null,
    val conceptId: String? = null,
    val questionId: String? = null,
)

@Serializable
data class CaseNoteDto(
    val id: String,
    val conceptId: String,
    val topic: String,
    val holding: String,
    val caseNumber: String? = null,
    val decisionDate: String? = null,
    val court: String = "",
    val articles: String = "",
    val verificationStatus: String = "DOCTRINE_ONLY_NOT_VERIFIED",
)

@Serializable
data class ExamAreaDto(
    val id: String,
    val name: String,
    val minPercent: Int,
    val maxPercent: Int,
    val mockQuestions: Int,
    val questionCount: Int = 0,
    val subjectId: String,
)

@Serializable
data class LawVersionDto(
    val lawName: String,
    val lawShortName: String,
    val lawVersion: String,
    val promulgationDate: String? = null,
    val effectiveDate: String? = null,
    val sourceUrl: String? = null,
    val verifiedAt: String? = null,
    val examYear: Int? = null,
)

@Serializable
data class ProblemSourceDto(
    val sourceId: String,
    val sourceFile: String? = null,
    val sourcePage: String? = null,
    val originalNumber: String? = null,
    val topic: String = "",
    val conceptIds: List<String> = emptyList(),
    val formula: String? = null,
    val solutionSteps: String? = null,
    val keyTrap: String? = null,
    val linkedQuestionIds: List<String> = emptyList(),
    val readingConfidence: String = "NONE",
    val verificationStatus: String = "SOURCE_NOT_PROVIDED",
    val reviewRequired: Boolean = true,
    val note: String? = null,
)

@Serializable
data class ScopeItemDto(
    val id: String,
    val area: String,
    val item: String,
    val conceptId: String? = null,
    val covered: Boolean = false,
    val sortOrder: Int = 0,
)

/** 슬롯형 문항(매칭·분류·순서·분개 계정)의 한 칸 */
@Serializable
data class SlotDto(val label: String, val answer: String)

/** 계산문항의 공식 ID 와 입력값. 앱의 FormulaEngine 이 같은 값을 독립적으로 다시 계산해 검산한다. */
@Serializable
data class CalcSpecDto(val formulaId: String, val params: Map<String, Double> = emptyMap())

@Serializable
data class SubjectDto(
    val id: String,
    val name: String,
    val description: String = "",
    val sortOrder: Int = 0,
    val isActive: Boolean = true,
    /** v5: 공식 시험과목(ACC 회계학·BUS 경영학·ECO 경제원론·LAW 기업법·TAX 세법개론) */
    val examSubject: String = "",
)

/** v5: 공식 출제범위 마스터 목차 항목. conceptIds 가 실제 학습 가능한 개념이면 covered=true(콘텐츠 구축률 계산용). */
@Serializable
data class CurriculumItemDto(
    val id: String,
    val subjectId: String,
    val unit: String,
    val item: String,
    val conceptIds: List<String> = emptyList(),
    val covered: Boolean = false,
    val sortOrder: Int = 0,
)

@Serializable
data class ChapterDto(
    val id: String,
    val code: String,
    val subjectId: String,
    val title: String,
    val englishTitle: String = "",
    val sortOrder: Int = 0,
    /** v3: 학습 파트(개념체계와 재무제표 표시 / 재고자산 / 유형자산 / 차입원가) */
    val partTitle: String = "",
)

@Serializable
data class SectionDto(
    val id: String,
    val chapterId: String,
    val title: String,
    val sortOrder: Int = 0,
)

@Serializable
data class ExamScopeDto(
    val number: String,
    val title: String,
    val conceptIds: List<String>,
    val subjectId: String = "FA",
)

@Serializable
data class ConceptDto(
    val id: String,
    val subjectId: String,
    val chapterId: String,
    val sectionId: String,
    val parentConceptId: String? = null,
    val koreanName: String,
    val englishName: String = "",
    val definition: String,
    val whyItMatters: String = "",
    val detailedExplanation: String = "",
    val comparison: String = "",
    val example: String = "",
    val formula: String? = null,
    val examTips: String = "",
    val commonMistakes: String = "",
    val relatedConceptIds: List<String> = emptyList(),
    val prerequisiteConceptIds: List<String> = emptyList(),
    val examReferenceNumbers: List<String> = emptyList(),
    val standardReference: String = "",
    val effectiveFrom: String? = null,
    val effectiveTo: String? = null,
    val isHistorical: Boolean = false,
    val requiresReview: Boolean = false,
    val revisionNote: String? = null,
    val originalExamYear: Int? = null,
    val reviewStatus: String = "REVIEWED",
    val isRequired: Boolean = true,
    val isActive: Boolean = true,
    val sortOrder: Int = 0,
    val revision: Int = 1,
    val contentVersion: Int? = null,
    // v3 추가 필드
    val recognitionRules: String? = null,
    val measurementRules: String? = null,
    val exceptions: String? = null,
    val journalEntries: String? = null,
    val sourceProblemIds: List<String> = emptyList(),
    val verificationStatus: String = "AUTHOR_REVIEWED",
    // v4(기업법) 추가 필드
    val lawName: String? = null,
    val articleRefs: List<String> = emptyList(),
    val lawVersion: String? = null,
    val promulgationDate: String? = null,
    val transitionRule: String? = null,
    val examYear: Int? = null,
    /** APPLIES_2027 | PAST | NOT_IN_FORCE | CHECK_DATE */
    val examApplicability: String? = null,
    val sourceUrl: String? = null,
    val verifiedAt: String? = null,
    val caseLaw: String? = null,
    val examArea: String? = null,
)

@Serializable
data class QuestionDto(
    val id: String,
    val primaryConceptId: String,
    val conceptIds: List<String>,
    val subjectId: String,
    val chapterId: String,
    val questionType: String = "definition",
    val questionText: String,
    val blankAnswer: String,
    val acceptedAnswers: List<String> = emptyList(),
    val unit: String? = null,
    val explanation: String,
    val confusion: String? = null,
    val calculation: String? = null,
    val difficulty: Int = 1,
    val examReferenceNumbers: List<String> = emptyList(),
    val relatedConceptIds: List<String> = emptyList(),
    val standardReference: String = "",
    val effectiveFrom: String? = null,
    val effectiveTo: String? = null,
    val isHistorical: Boolean = false,
    val requiresReview: Boolean = false,
    val revisionNote: String? = null,
    val reviewStatus: String = "REVIEWED",
    val isActive: Boolean = true,
    val revision: Int = 1,
    val sortOrder: Int = 0,
    val contentVersion: Int? = null,
    // v3 추가 필드
    /** text(빈칸 입력) | choice(4지선다) | slots(매칭·분류·순서·분개 계정 선택) */
    val inputType: String = INPUT_TEXT,
    val choices: List<String> = emptyList(),
    val slots: List<SlotDto> = emptyList(),
    val calcSpec: CalcSpecDto? = null,
    val sourceProblemIds: List<String> = emptyList(),
    /** BOTH(기본·심화 모두) | OPTIONS(보기 선택형) */
    val mode: String = "BOTH",
    /** v4: STATUTE_TEXT_MATCHED(정답이 근거 조문 원문에 있음) | INTERPRETIVE(해석·선택형) */
    val answerVerification: String? = null,
    /** v4: 기업법 출제 영역(GENERAL·COMPANY·CPA_ACT·AUDIT_ACT) */
    val examArea: String? = null,
)

const val INPUT_TEXT = "text"
const val INPUT_CHOICE = "choice"
const val INPUT_SLOTS = "slots"

/** 콘텐츠·백업 JSON 공통 파서 설정. 알 수 없는 필드는 무시하여 향후 확장에 대비한다. */
val ContentJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    isLenient = false
}

/** 빈칸 표기. 모든 문항은 정확히 1개의 빈칸을 가진다. */
const val BLANK_TOKEN = "(______)"
