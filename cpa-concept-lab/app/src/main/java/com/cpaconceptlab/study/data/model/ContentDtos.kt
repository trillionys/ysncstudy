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
)

@Serializable
data class SubjectDto(
    val id: String,
    val name: String,
    val description: String = "",
    val sortOrder: Int = 0,
    val isActive: Boolean = true,
)

@Serializable
data class ChapterDto(
    val id: String,
    val code: String,
    val subjectId: String,
    val title: String,
    val englishTitle: String = "",
    val sortOrder: Int = 0,
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
)

/** 콘텐츠·백업 JSON 공통 파서 설정. 알 수 없는 필드는 무시하여 향후 확장에 대비한다. */
val ContentJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    isLenient = false
}

/** 빈칸 표기. 모든 문항은 정확히 1개의 빈칸을 가진다. */
const val BLANK_TOKEN = "(______)"
