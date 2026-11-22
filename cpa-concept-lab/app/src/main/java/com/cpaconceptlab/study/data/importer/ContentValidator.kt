package com.cpaconceptlab.study.data.importer

import com.cpaconceptlab.study.data.model.BLANK_TOKEN
import com.cpaconceptlab.study.data.model.ConceptDto
import com.cpaconceptlab.study.data.model.ContentPack
import com.cpaconceptlab.study.data.model.ExamScopeDto
import com.cpaconceptlab.study.data.model.QuestionDto
import com.cpaconceptlab.study.domain.AnswerGrader
import com.cpaconceptlab.study.utils.Chosung

/**
 * 개념 누락 자동검증기(요구사항 23의 12개 항목).
 * 단위 테스트(ContentCoverageTest)와 앱 설정 화면의 '콘텐츠 검증 리포트'에서 같은 코드를 사용한다.
 */
object ContentCoverageValidator {

    const val SUPPORTED_FORMAT_VERSION = 1

    data class Report(
        val requiredConcepts: Int,
        val registeredConcepts: Int,
        val explainedConcepts: Int,
        val linkedConcepts: Int,
        val activeQuestions: Int,
        val inactiveQuestions: Int,
        val basicModeAvailable: Int,
        val advancedModeAvailable: Int,
        val examScopesLinked: Int,
        val examScopeTotal: Int,
        val selectorCandidateConcepts: Int,
        val errors: List<String>,
    ) {
        val passed: Boolean get() = errors.isEmpty()

        fun toText(): String = buildString {
            appendLine("전체 필수 개념: ${requiredConcepts}개")
            appendLine("등록 개념: ${registeredConcepts}개")
            appendLine("설명 완료: ${explainedConcepts}개")
            appendLine("문제 연결 완료(기본 문항 2개 이상): ${linkedConcepts}개")
            appendLine("기본 문항(활성): ${activeQuestions}개")
            appendLine("비활성 문항(집계 제외): ${inactiveQuestions}개")
            appendLine("기본 모드 이용 가능: ${basicModeAvailable}개")
            appendLine("심화 모드 이용 가능: ${advancedModeAvailable}개")
            appendLine("출제범위 01~${examScopeTotal.toString().padStart(2, '0')} 연결: ${examScopesLinked}개")
            appendLine("균등출제 후보 개념: ${selectorCandidateConcepts}개")
            appendLine("검증 실패: ${errors.size}개")
            errors.take(100).forEach { appendLine(" - $it") }
        }
    }

    fun normalizeText(s: String) = AnswerGrader.normalize(s.replace(BLANK_TOKEN, "#"))

    fun validate(
        concepts: List<ConceptDto>,
        questions: List<QuestionDto>,
        examScopes: List<ExamScopeDto>,
        requiredConceptIds: List<String>,
        examScopeCount: Int = 46,
    ): Report {
        val errors = mutableListOf<String>()
        val conceptById = concepts.associateBy { it.id }
        val active = questions.filter { it.isActive }

        // 1. 필수 conceptId 존재
        requiredConceptIds.filter { it !in conceptById }.forEach { errors += "[1] 필수 개념 누락: $it" }

        // 2. 학습 설명 존재
        var explained = 0
        for (c in concepts) {
            val missing = listOf(
                "정의" to c.definition, "핵심 원리" to c.detailedExplanation, "필요한 이유" to c.whyItMatters,
                "유사 개념 차이" to c.comparison, "사례" to c.example, "출제 포인트" to c.examTips, "함정" to c.commonMistakes,
            ).filter { it.second.isBlank() }.map { it.first }
            if (missing.isEmpty()) explained++ else errors += "[2] ${c.id} 설명 누락: $missing"
        }

        // 3. 개념별 서로 다른 기본 문항 2개 이상(비활성 제외, 동일 문장 중복은 1개로 계산)
        val byPrimary = active.groupBy { it.primaryConceptId }
        var linked = 0
        for (c in concepts.filter { it.isActive }) {
            val distinct = byPrimary[c.id].orEmpty().map { normalizeText(it.questionText) }.distinct().size
            if (distinct >= 2) linked++ else errors += "[3] ${c.id} 서로 다른 기본 문항 ${distinct}개(<2)"
        }

        // 4. 출제범위 01~N 연결
        val scopeNums = examScopes.associateBy { it.number }
        var scopesLinked = 0
        for (n in 1..examScopeCount) {
            val key = n.toString().padStart(2, '0')
            val s = scopeNums[key]
            if (s == null) { errors += "[4] 출제범위 $key 미등록"; continue }
            if (s.conceptIds.isEmpty()) { errors += "[4] 출제범위 $key 연결 개념 없음"; continue }
            val qs = active.count { q -> q.conceptIds.any { it in s.conceptIds } || q.primaryConceptId in s.conceptIds }
            if (qs < 3) errors += "[4] 출제범위 $key 관련 문항 ${qs}개(<3)" else scopesLinked++
            s.conceptIds.filter { it !in conceptById }.forEach { errors += "[9] 출제범위 $key 미존재 개념 $it" }
        }

        // 5~9, 11. 문항 무결성
        val nameOwners = concepts.groupBy { AnswerGrader.normalize(it.koreanName) }.mapValues { e -> e.value.map { it.id }.toSet() }
        val seenText = mutableMapOf<String, String>()
        var basicOk = 0
        for (q in questions) {
            if (q.explanation.isBlank()) errors += "[5] ${q.id} 해설 없음"
            if (AnswerGrader.normalize(q.blankAnswer).isEmpty()) errors += "[6] ${q.id} 정답 없음"
            for (alt in q.acceptedAnswers) {
                val owners = nameOwners[AnswerGrader.normalize(alt)].orEmpty() - q.conceptIds.toSet() - q.primaryConceptId
                if (owners.isNotEmpty() && AnswerGrader.normalize(alt) != AnswerGrader.normalize(q.blankAnswer)) {
                    errors += "[7] ${q.id} 동의어 '$alt' 가 다른 개념명($owners)과 같아 모순"
                }
            }
            val key = normalizeText(q.questionText)
            seenText[key]?.let { errors += "[8] 문제 문장 중복: ${q.id} = $it" }
            seenText[key] = q.id
            (q.conceptIds + q.primaryConceptId).distinct().filter { it !in conceptById }
                .forEach { errors += "[9] ${q.id} 미존재 개념 $it" }
            if (q.isActive) {
                val blanks = q.questionText.windowed(BLANK_TOKEN.length).count { it == BLANK_TOKEN }
                val hintOk = Chosung.hint(q.blankAnswer).isNotBlank()
                if (blanks == 1 && hintOk) basicOk++ else errors += "[11] ${q.id} 빈칸 ${blanks}개 또는 힌트 생성 불가"
            }
        }

        // 10. 비활성 문항만으로 연결된 개념이 '연결 완료'로 계산되지 않는지
        val inactive = questions.filter { !it.isActive }
        val coveredOnlyByInactive = inactive.map { it.primaryConceptId }.toSet() - byPrimary.keys
        coveredOnlyByInactive.forEach { errors += "[10] $it 는 비활성 문항만 연결됨(정상 문항으로 계산하면 안 됨)" }

        // 12. 모든 활성 개념이 균등출제 후보에 포함되는지
        val candidateConcepts = active.flatMap { it.conceptIds + it.primaryConceptId }.toSet()
        concepts.filter { it.isActive && it.id !in candidateConcepts }.forEach { errors += "[12] ${it.id} 균등출제 후보 아님" }

        return Report(
            requiredConcepts = requiredConceptIds.size,
            registeredConcepts = concepts.size,
            explainedConcepts = explained,
            linkedConcepts = linked,
            activeQuestions = active.size,
            inactiveQuestions = inactive.size,
            basicModeAvailable = basicOk,
            advancedModeAvailable = basicOk,
            examScopesLinked = scopesLinked,
            examScopeTotal = examScopeCount,
            selectorCandidateConcepts = concepts.count { it.isActive && it.id in candidateConcepts },
            errors = errors,
        )
    }
}

/**
 * 가져오기용 팩 검증기(요구사항 20). 기존 DB 의 ID 와 리비전을 고려해 처리 계획을 만든다.
 * 오류가 하나라도 있으면 가져오기는 실행되지 않는다.
 */
object PackValidator {

    data class Plan(
        val errors: List<String>,
        val warnings: List<String>,
        val newConcepts: Int,
        val updatedConcepts: Int,
        val skippedConcepts: Int,
        val newQuestions: Int,
        val updatedQuestions: Int,
        val skippedQuestions: Int,
    ) {
        val ok: Boolean get() = errors.isEmpty()
    }

    private val ID_PATTERN = Regex("[A-Za-z0-9_.\\-]{1,64}")

    fun validate(
        packs: List<ContentPack>,
        existingConceptRevisions: Map<String, Int>,
        existingQuestionRevisions: Map<String, Int>,
        existingChapterIds: Set<String>,
        existingSectionIds: Set<String>,
        existingSubjectIds: Set<String>,
    ): Plan {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        if (packs.isEmpty()) errors += "가져올 콘텐츠가 없습니다."
        for (p in packs) {
            if (p.formatVersion > ContentCoverageValidator.SUPPORTED_FORMAT_VERSION) {
                errors += "지원하지 않는 형식 버전(${p.formatVersion}) — 앱을 업데이트하세요."
            }
            if (p.contentVersion < 1) errors += "콘텐츠 버전(contentVersion)이 올바르지 않습니다: ${p.contentVersion}"
        }
        val concepts = packs.flatMap { it.concepts }
        val questions = packs.flatMap { it.questions }
        val subjects = existingSubjectIds + packs.flatMap { it.subjects }.map { it.id }
        val chapters = existingChapterIds + packs.flatMap { it.chapters }.map { it.id }
        val sections = existingSectionIds + packs.flatMap { it.sections }.map { it.id }

        concepts.groupBy { it.id }.filter { it.value.size > 1 }.keys.forEach { errors += "팩 내부 conceptId 중복: $it" }
        questions.groupBy { it.id }.filter { it.value.size > 1 }.keys.forEach { errors += "팩 내부 questionId 중복: $it" }
        packs.flatMap { it.chapters }.filter { it.subjectId !in subjects }.forEach { errors += "대단원 ${it.id}: 과목 ${it.subjectId} 없음" }
        packs.flatMap { it.sections }.filter { it.chapterId !in chapters }.forEach { errors += "중단원 ${it.id}: 대단원 ${it.chapterId} 없음" }

        val allConceptIds = existingConceptRevisions.keys + concepts.map { it.id }
        var newC = 0; var updC = 0; var skipC = 0
        for (c in concepts) {
            if (!ID_PATTERN.matches(c.id)) errors += "conceptId 형식 오류: '${c.id}'"
            if (c.koreanName.isBlank()) errors += "${c.id}: 개념명 없음"
            if (c.definition.isBlank()) errors += "${c.id}: 정의(설명) 없음"
            if (c.chapterId !in chapters) errors += "${c.id}: 대단원 ${c.chapterId} 없음"
            if (c.sectionId !in sections) errors += "${c.id}: 중단원 ${c.sectionId} 없음"
            val old = existingConceptRevisions[c.id]
            when {
                old == null -> newC++
                c.revision > old -> updC++
                c.revision == old -> skipC++
                else -> { skipC++; warnings += "${c.id}: 기존 리비전($old)보다 낮은 리비전(${c.revision}) — 건너뜀" }
            }
        }
        var newQ = 0; var updQ = 0; var skipQ = 0
        for (q in questions) {
            if (!ID_PATTERN.matches(q.id)) errors += "questionId 형식 오류: '${q.id}'"
            if (q.blankAnswer.isBlank()) errors += "${q.id}: 정답(blankAnswer) 없음"
            if (q.explanation.isBlank()) errors += "${q.id}: 해설(explanation) 없음"
            val blanks = q.questionText.windowed(BLANK_TOKEN.length).count { it == BLANK_TOKEN }
            if (blanks != 1) errors += "${q.id}: 빈칸 '$BLANK_TOKEN' 은 정확히 1개여야 합니다(현재 $blanks)"
            val linkedIds = (q.conceptIds + q.primaryConceptId).distinct()
            if (q.primaryConceptId.isBlank()) errors += "${q.id}: 개념 연결(primaryConceptId) 없음"
            linkedIds.filter { it !in allConceptIds }.forEach { errors += "${q.id}: 존재하지 않는 개념 $it 참조" }
            if (q.chapterId !in chapters) errors += "${q.id}: 대단원 ${q.chapterId} 없음"
            val old = existingQuestionRevisions[q.id]
            when {
                old == null -> newQ++
                q.revision > old -> updQ++
                q.revision == old -> skipQ++
                else -> { skipQ++; warnings += "${q.id}: 기존 리비전($old)보다 낮음 — 건너뜀" }
            }
        }
        if (skipC + skipQ > 0) warnings += "이미 같은 리비전으로 존재하는 항목 ${skipC + skipQ}개는 중복 삽입 없이 건너뜁니다."
        return Plan(errors, warnings, newC, updC, skipC, newQ, updQ, skipQ)
    }
}
