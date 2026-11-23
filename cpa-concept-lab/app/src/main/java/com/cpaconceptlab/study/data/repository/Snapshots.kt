package com.cpaconceptlab.study.data.repository

import com.cpaconceptlab.study.data.local.ChapterEntity
import com.cpaconceptlab.study.data.local.ConceptEntity
import com.cpaconceptlab.study.data.local.ConceptProgressEntity
import com.cpaconceptlab.study.data.local.ExamScopeEntity
import com.cpaconceptlab.study.data.local.ProblemSourceEntity
import com.cpaconceptlab.study.data.local.ScopeItemEntity
import com.cpaconceptlab.study.data.local.QuestionEntity
import com.cpaconceptlab.study.data.local.QuestionProgressEntity
import com.cpaconceptlab.study.data.local.SectionEntity
import com.cpaconceptlab.study.data.local.StudyDayEntity
import com.cpaconceptlab.study.data.local.SubjectEntity
import com.cpaconceptlab.study.data.local.WrongNoteEntity

/**
 * 메모리에 올린 콘텐츠 스냅샷. 콘텐츠 규모(수백~수천 문항)에서는 전체를 메모리에서 다루는 편이
 * 단순하고 빠르다. 가져오기 후에는 다시 로드된다.
 * 진도율 분모는 항상 이 스냅샷의 '활성' 개념·문항 수다.
 */
data class ContentSnapshot(
    val subjects: List<SubjectEntity>,
    val chapters: List<ChapterEntity>,
    val sections: List<SectionEntity>,
    val allConcepts: List<ConceptEntity>,
    val allQuestions: List<QuestionEntity>,
    val examScopes: List<ExamScopeEntity>,
    val bundledVersion: Int,
    val problemSources: List<ProblemSourceEntity> = emptyList(),
    val scopeItems: List<ScopeItemEntity> = emptyList(),
) {
    val concepts: List<ConceptEntity> = allConcepts.filter { it.isActive }
    val questions: List<QuestionEntity> = allQuestions.filter { it.isActive }
    val conceptById: Map<String, ConceptEntity> = allConcepts.associateBy { it.id }
    val questionById: Map<String, QuestionEntity> = allQuestions.associateBy { it.id }
    val chapterById: Map<String, ChapterEntity> = chapters.associateBy { it.id }
    val sectionById: Map<String, SectionEntity> = sections.associateBy { it.id }
    /** 개념별 소속(주개념) 활성 문항 */
    val questionsByPrimary: Map<String, List<QuestionEntity>> = questions.groupBy { it.primaryConceptId }
    /** 개념별 연결(주·보조 개념 모두) 활성 문항 */
    val questionsByLinked: Map<String, List<QuestionEntity>> =
        questions.flatMap { q -> q.conceptIds.map { it to q } }.groupBy({ it.first }, { it.second })

    fun chaptersOf(subjectId: String) = chapters.filter { it.subjectId == subjectId }.sortedBy { it.sortOrder }
    fun sectionsOf(chapterId: String) = sections.filter { it.chapterId == chapterId }.sortedBy { it.sortOrder }
    fun conceptsOfSection(sectionId: String) = concepts.filter { it.sectionId == sectionId }.sortedBy { it.sortOrder }
    fun conceptsOfChapter(chapterId: String) = concepts.filter { it.chapterId == chapterId }.sortedBy { it.sortOrder }

    companion object {
        val EMPTY = ContentSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), 0)
    }
}

data class UserSnapshot(
    val questionProgress: List<QuestionProgressEntity>,
    val conceptProgress: List<ConceptProgressEntity>,
    val wrongNotes: List<WrongNoteEntity>,
    val studyDays: List<StudyDayEntity>,
) {
    val progressMap: Map<Pair<String, String>, QuestionProgressEntity> =
        questionProgress.associateBy { it.questionId to it.mode }
    val conceptProgressMap: Map<String, ConceptProgressEntity> = conceptProgress.associateBy { it.conceptId }

    companion object {
        val EMPTY = UserSnapshot(emptyList(), emptyList(), emptyList(), emptyList())
    }
}
