package com.cpaconceptlab.study.data.importer

import com.cpaconceptlab.study.data.local.ChapterEntity
import com.cpaconceptlab.study.data.local.ConceptEntity
import com.cpaconceptlab.study.data.local.ExamScopeEntity
import com.cpaconceptlab.study.data.local.QuestionEntity
import com.cpaconceptlab.study.data.local.SectionEntity
import com.cpaconceptlab.study.data.local.SubjectEntity
import com.cpaconceptlab.study.data.model.ChapterDto
import com.cpaconceptlab.study.data.model.ConceptDto
import com.cpaconceptlab.study.data.model.ExamScopeDto
import com.cpaconceptlab.study.data.model.QuestionDto
import com.cpaconceptlab.study.data.model.SectionDto
import com.cpaconceptlab.study.data.model.SubjectDto

fun SubjectDto.toEntity() = SubjectEntity(id, name, description, sortOrder, isActive)
fun ChapterDto.toEntity() = ChapterEntity(id, code, subjectId, title, englishTitle, sortOrder)
fun SectionDto.toEntity() = SectionEntity(id, chapterId, title, sortOrder)
fun ExamScopeDto.toEntity() = ExamScopeEntity(number, subjectId, title, conceptIds)

fun ConceptDto.toEntity(packVersion: Int) = ConceptEntity(
    id = id, subjectId = subjectId, chapterId = chapterId, sectionId = sectionId, parentConceptId = parentConceptId,
    koreanName = koreanName, englishName = englishName, definition = definition, whyItMatters = whyItMatters,
    detailedExplanation = detailedExplanation, comparison = comparison, example = example, formula = formula,
    examTips = examTips, commonMistakes = commonMistakes, relatedConceptIds = relatedConceptIds,
    prerequisiteConceptIds = prerequisiteConceptIds, examReferenceNumbers = examReferenceNumbers,
    standardReference = standardReference, effectiveFrom = effectiveFrom, effectiveTo = effectiveTo,
    isHistorical = isHistorical, requiresReview = requiresReview, revisionNote = revisionNote,
    originalExamYear = originalExamYear, reviewStatus = reviewStatus, isRequired = isRequired, isActive = isActive,
    sortOrder = sortOrder, revision = revision, contentVersion = contentVersion ?: packVersion,
)

fun QuestionDto.toEntity(packVersion: Int) = QuestionEntity(
    id = id, primaryConceptId = primaryConceptId,
    conceptIds = (listOf(primaryConceptId) + conceptIds).distinct(),
    subjectId = subjectId, chapterId = chapterId, questionType = questionType, questionText = questionText,
    blankAnswer = blankAnswer, acceptedAnswers = (listOf(blankAnswer) + acceptedAnswers).distinct(), unit = unit,
    explanation = explanation, confusion = confusion, calculation = calculation, difficulty = difficulty,
    examReferenceNumbers = examReferenceNumbers, relatedConceptIds = relatedConceptIds,
    standardReference = standardReference, effectiveFrom = effectiveFrom, effectiveTo = effectiveTo,
    isHistorical = isHistorical, requiresReview = requiresReview, revisionNote = revisionNote,
    reviewStatus = reviewStatus, isActive = isActive, revision = revision, sortOrder = sortOrder,
    contentVersion = contentVersion ?: packVersion,
)

fun ConceptEntity.toDto() = ConceptDto(
    id, subjectId, chapterId, sectionId, parentConceptId, koreanName, englishName, definition, whyItMatters,
    detailedExplanation, comparison, example, formula, examTips, commonMistakes, relatedConceptIds,
    prerequisiteConceptIds, examReferenceNumbers, standardReference, effectiveFrom, effectiveTo, isHistorical,
    requiresReview, revisionNote, originalExamYear, reviewStatus, isRequired, isActive, sortOrder, revision, contentVersion,
)

fun QuestionEntity.toDto() = QuestionDto(
    id, primaryConceptId, conceptIds, subjectId, chapterId, questionType, questionText, blankAnswer, acceptedAnswers,
    unit, explanation, confusion, calculation, difficulty, examReferenceNumbers, relatedConceptIds, standardReference,
    effectiveFrom, effectiveTo, isHistorical, requiresReview, revisionNote, reviewStatus, isActive, revision, sortOrder,
    contentVersion,
)

fun ExamScopeEntity.toDto() = ExamScopeDto(number, title, conceptIds, subjectId)
