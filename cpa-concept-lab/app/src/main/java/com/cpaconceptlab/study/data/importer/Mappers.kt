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
import com.cpaconceptlab.study.data.local.ProblemSourceEntity
import com.cpaconceptlab.study.data.local.ScopeItemEntity
import com.cpaconceptlab.study.data.model.CalcSpecDto
import com.cpaconceptlab.study.data.model.ContentJson
import com.cpaconceptlab.study.data.model.ProblemSourceDto
import com.cpaconceptlab.study.data.model.ScopeItemDto
import com.cpaconceptlab.study.data.model.SlotDto
import com.cpaconceptlab.study.data.local.CaseNoteEntity
import com.cpaconceptlab.study.data.local.ExamAreaEntity
import com.cpaconceptlab.study.data.local.LawArticleEntity
import com.cpaconceptlab.study.data.local.LawVersionEntity
import com.cpaconceptlab.study.data.local.NumericRuleEntity
import com.cpaconceptlab.study.data.model.CaseNoteDto
import com.cpaconceptlab.study.data.model.CurriculumItemDto
import com.cpaconceptlab.study.data.model.ExamAreaDto
import com.cpaconceptlab.study.data.model.LawArticleDto
import com.cpaconceptlab.study.data.model.LawVersionDto
import com.cpaconceptlab.study.data.model.NumericRuleDto
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

private val PARAMS_SER = MapSerializer(String.serializer(), Double.serializer())

fun encodeParams(params: Map<String, Double>): String = ContentJson.encodeToString(PARAMS_SER, params)
fun decodeParams(json: String?): Map<String, Double> =
    if (json.isNullOrBlank()) emptyMap() else runCatching { ContentJson.decodeFromString(PARAMS_SER, json) }.getOrElse { emptyMap() }

/** 문항 엔티티의 계산 명세(없으면 null) */
fun QuestionEntity.calcSpec(): CalcSpecDto? = calcFormulaId?.let { CalcSpecDto(it, decodeParams(calcParams)) }

fun SubjectDto.toEntity() = SubjectEntity(id, name, description, sortOrder, isActive, examSubject)
fun CurriculumItemDto.toEntity() = com.cpaconceptlab.study.data.local.CurriculumItemEntity(id, subjectId, unit, item, conceptIds, covered, sortOrder)
fun ChapterDto.toEntity() = ChapterEntity(id, code, subjectId, title, englishTitle, sortOrder, partTitle)
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
    recognitionRules = recognitionRules, measurementRules = measurementRules, exceptions = exceptions,
    journalEntries = journalEntries, sourceProblemIds = sourceProblemIds, verificationStatus = verificationStatus,
    lawName = lawName, articleRefs = articleRefs, lawVersion = lawVersion, promulgationDate = promulgationDate,
    transitionRule = transitionRule, examYear = examYear, examApplicability = examApplicability, sourceUrl = sourceUrl,
    verifiedAt = verifiedAt, caseLaw = caseLaw, examArea = examArea,
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
    inputType = inputType, choices = choices, slotLabels = slots.map { it.label }, slotAnswers = slots.map { it.answer },
    calcFormulaId = calcSpec?.formulaId, calcParams = calcSpec?.let { encodeParams(it.params) },
    sourceProblemIds = sourceProblemIds, answerVerification = answerVerification, examArea = examArea,
)

fun ConceptEntity.toDto() = ConceptDto(
    id, subjectId, chapterId, sectionId, parentConceptId, koreanName, englishName, definition, whyItMatters,
    detailedExplanation, comparison, example, formula, examTips, commonMistakes, relatedConceptIds,
    prerequisiteConceptIds, examReferenceNumbers, standardReference, effectiveFrom, effectiveTo, isHistorical,
    requiresReview, revisionNote, originalExamYear, reviewStatus, isRequired, isActive, sortOrder, revision, contentVersion,
    recognitionRules, measurementRules, exceptions, journalEntries, sourceProblemIds, verificationStatus,
    lawName, articleRefs, lawVersion, promulgationDate, transitionRule, examYear, examApplicability, sourceUrl,
    verifiedAt, caseLaw, examArea,
)

fun QuestionEntity.toDto() = QuestionDto(
    id, primaryConceptId, conceptIds, subjectId, chapterId, questionType, questionText, blankAnswer, acceptedAnswers,
    unit, explanation, confusion, calculation, difficulty, examReferenceNumbers, relatedConceptIds, standardReference,
    effectiveFrom, effectiveTo, isHistorical, requiresReview, revisionNote, reviewStatus, isActive, revision, sortOrder,
    contentVersion, inputType, choices, slotLabels.zip(slotAnswers).map { (l, a) -> SlotDto(l, a) }, calcSpec(), sourceProblemIds,
    if (inputType == "text") "BOTH" else "OPTIONS", answerVerification, examArea,
)

fun ProblemSourceDto.toEntity() = ProblemSourceEntity(
    sourceId, sourceFile, sourcePage, originalNumber, topic, conceptIds, formula, solutionSteps, keyTrap,
    linkedQuestionIds, readingConfidence, verificationStatus, reviewRequired, note,
)

fun ScopeItemDto.toEntity() = ScopeItemEntity(id, area, item, conceptId, covered, sortOrder)

fun ExamScopeEntity.toDto() = ExamScopeDto(number, title, conceptIds, subjectId)

fun LawArticleDto.toEntity() = LawArticleEntity(
    id, lawName, lawShortName, articleId, articleNo, branch, title, text, part, chapter, deleted, inScope, status,
    statusLabel, note, examArea, conceptIds, questionCount, lawVersion, effectiveDate, sourceUrl, verifiedAt,
)

fun NumericRuleDto.toEntity() = NumericRuleEntity(
    ruleId, lawName, article, ruleName, subject, condition, value, unit, exception, effectiveDate, sourceUrl, conceptId, questionId,
)

fun CaseNoteDto.toEntity() = CaseNoteEntity(id, conceptId, topic, holding, caseNumber, decisionDate, court, articles, verificationStatus)
fun ExamAreaDto.toEntity() = ExamAreaEntity(id, name, minPercent, maxPercent, mockQuestions, questionCount, subjectId)
fun LawVersionDto.toEntity() = LawVersionEntity(lawShortName, lawName, lawVersion, promulgationDate, effectiveDate, sourceUrl, verifiedAt, examYear)
