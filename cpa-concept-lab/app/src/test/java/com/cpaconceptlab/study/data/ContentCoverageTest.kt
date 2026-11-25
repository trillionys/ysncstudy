package com.cpaconceptlab.study.data

import com.cpaconceptlab.study.TestContent
import com.cpaconceptlab.study.data.importer.ContentCoverageValidator
import com.cpaconceptlab.study.data.importer.ContentParser
import com.cpaconceptlab.study.data.importer.PackValidator
import com.cpaconceptlab.study.data.model.BLANK_TOKEN
import com.cpaconceptlab.study.data.model.INPUT_TEXT
import com.cpaconceptlab.study.domain.AnswerGrader
import com.cpaconceptlab.study.utils.Chosung
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/** 실제 번들 콘텐츠에 대한 개념 누락 자동검증(요구사항 23) — 빌드마다 실행된다. */
class ContentCoverageTest {
    private val packs = TestContent.packs
    private val concepts = packs.flatMap { it.concepts }
    private val questions = packs.flatMap { it.questions }
    private val scopes = packs.flatMap { it.examScopes }
    private val required = TestContent.manifest.requiredConceptIds

    // 필수 개념 285개 A01~K32 전체가 등록되어 있고 검증 실패가 0건
    @Test fun allRequiredConceptsRegisteredAndZeroFailures() {
        val report = ContentCoverageValidator.validate(concepts, questions, scopes, required)
        File(TestContent.reportDir(), "content-validation.txt").writeText(report.toText())
        println(report.toText())
        assertEquals(285, required.size)
        assertTrue("필수 개념 이상 등록", report.registeredConcepts >= required.size)
        assertEquals(TestContent.manifest.conceptCount, report.registeredConcepts)
        assertEquals(report.registeredConcepts, report.explainedConcepts)
        assertEquals(report.registeredConcepts, report.linkedConcepts)
        assertEquals(46, report.examScopesLinked)
        assertEquals(report.activeQuestions, report.basicModeAvailable)
        assertEquals(report.activeQuestions, report.advancedModeAvailable)
        assertEquals(report.registeredConcepts, report.selectorCandidateConcepts)
        assertTrue("검증 실패: ${report.errors}", report.passed)
    }

    // 필수 범위 코드 패턴 A~K 와 개수
    @Test fun requiredCodePatternAtoK() {
        val expected = mapOf('A' to 13, 'B' to 19, 'C' to 24, 'D' to 36, 'E' to 21, 'F' to 20, 'G' to 19, 'H' to 30, 'I' to 45, 'J' to 26, 'K' to 32)
        val actual = required.groupingBy { it.first() }.eachCount()
        assertEquals(expected, actual)
        expected.forEach { (letter, n) ->
            (1..n).forEach { i -> assertTrue("$letter${i.toString().padStart(2, '0')} 누락", "$letter${i.toString().padStart(2, '0')}" in required) }
        }
    }

    // 기본 문항은 최소 240개 이상이며 개념당 2문항 이상
    @Test fun atLeast240QuestionsAndTwoPerConcept() {
        val active = questions.filter { it.isActive }
        assertTrue(active.size >= maxOf(240, required.size * 2))
        val per = active.groupBy { it.primaryConceptId }.mapValues { it.value.size }
        required.forEach { assertTrue("$it 문항 부족", (per[it] ?: 0) >= 2) }
    }

    // 출제범위 01~46 은 각각 3문항 이상 연결
    @Test fun examScopes01to46HaveThreeQuestionsEach() {
        for (n in 1..46) {
            val key = n.toString().padStart(2, '0')
            val scope = scopes.firstOrNull { it.number == key } ?: fail("출제범위 $key 없음").let { return }
            val count = questions.count { q -> q.isActive && q.conceptIds.any { it in scope.conceptIds } }
            assertTrue("출제범위 $key 문항 $count", count >= 3)
        }
    }

    // 모든 문항은 빈칸 1개, 정답·해설 존재, 두 모드 응시 가능
    @Test fun everyQuestionHasOneBlankAnswerExplanationAndBothModes() {
        questions.filter { it.inputType == INPUT_TEXT }.forEach { q ->
            assertEquals("${q.id} 빈칸 수", 1, q.questionText.windowed(BLANK_TOKEN.length).count { it == BLANK_TOKEN })
            assertTrue("${q.id} 정답", q.blankAnswer.isNotBlank())
            assertTrue("${q.id} 해설", q.explanation.isNotBlank())
            assertTrue("${q.id} 초성", Chosung.hint(q.blankAnswer).isNotBlank())
            // 정답은 자기 자신의 정답 목록으로 반드시 정답 처리되어야 한다
            assertTrue("${q.id} 자기 정답 채점", AnswerGrader.grade(q.blankAnswer, q.blankAnswer, q.acceptedAnswers, q.unit).isCorrect)
        }
    }

    // 계산 문항은 계산과정을 포함하고 숫자 변형 문제가 존재
    @Test fun calculationQuestionsHaveStepsAndNumericVariants() {
        val calc = questions.filter { it.questionType == "calculation" }
        assertTrue(calc.size >= 40)
        calc.forEach { assertFalse("${it.id} 계산과정 없음", it.calculation.isNullOrBlank()) }
        // 자본유지(G)·측정(H) 계산 개념은 서로 다른 숫자의 변형문제를 가진다
        for (cid in listOf("G14", "G15", "G16")) {
            val answers = calc.filter { it.primaryConceptId == cid }.map { it.blankAnswer }.distinct()
            assertTrue("$cid 변형문제 부족", answers.size >= 2)
        }
    }

    // 문제 문장 중복 감지
    @Test fun detectsDuplicateQuestionText() {
        val texts = questions.map { ContentCoverageValidator.normalizeText(it.questionText) }
        assertEquals(texts.size, texts.toSet().size)
        // 검증기가 중복을 실제로 잡아내는지 확인
        val dup = questions.take(3) + questions.first().copy(id = "DUP-Q01")
        val r = ContentCoverageValidator.validate(concepts, dup, scopes, emptyList(), examScopeCount = 0)
        assertTrue(r.errors.any { it.startsWith("[8]") && "DUP-Q01" in it })
    }

    // 동의어 모순과 미존재 개념 참조 감지
    @Test fun detectsSynonymContradictionAndUnknownConcept() {
        val q = questions.first { it.primaryConceptId == "D03" }
        val bad = q.copy(id = "BAD-Q01", questionText = q.questionText + " ", acceptedAnswers = q.acceptedAnswers + "충실한 표현", conceptIds = listOf("D03", "Z99"))
        val r = ContentCoverageValidator.validate(concepts, listOf(bad), scopes, emptyList(), examScopeCount = 0)
        assertTrue(r.errors.any { it.startsWith("[7]") })
        assertTrue(r.errors.any { it.startsWith("[9]") && "Z99" in it })
    }

    // 비활성 문항은 연결 완료로 계산되지 않음
    @Test fun inactiveQuestionsAreNotCountedAsCoverage() {
        val target = "A01"
        val modified = questions.map { if (it.primaryConceptId == target) it.copy(isActive = false) else it }
        val r = ContentCoverageValidator.validate(concepts, modified, scopes, required)
        assertTrue(r.errors.any { it.startsWith("[3]") && target in it })
        assertTrue(r.errors.any { it.startsWith("[10]") && target in it })
        assertEquals(questions.size - modified.count { !it.isActive }, r.activeQuestions)
    }

    // 유효하지 않은 콘텐츠 JSON 은 예외 없이 형식 오류로 처리
    @Test fun invalidContentJsonIsReportedNotThrown() {
        listOf("", "{", "[]", "{\"contentVersion\":\"abc\"}", "not json").forEach { bad ->
            try {
                ContentParser.parsePack(bad); fail("오류가 발생해야 함: $bad")
            } catch (e: ContentParser.ContentFormatException) { /* 기대 */ }
        }
    }

    // 가져오기 팩 검증 - 정답·해설·개념연결·빈칸 누락 거부
    @Test fun importPackValidationRejectsMissingFields() {
        val ok = questions.first()
        val pack = TestContent.packs.first().copy(
            questions = listOf(
                ok.copy(id = "NEW-Q01", blankAnswer = ""),
                ok.copy(id = "NEW-Q02", explanation = ""),
                ok.copy(id = "NEW-Q03", primaryConceptId = "Z01", conceptIds = listOf("Z01")),
                ok.copy(id = "NEW-Q04", questionText = "빈칸 없음"),
                ok.copy(id = "NEW-Q04", questionText = "중복 ID $BLANK_TOKEN"),
            ),
            concepts = emptyList(), subjects = emptyList(), chapters = emptyList(), sections = emptyList(), examScopes = emptyList(),
        )
        val plan = PackValidator.validate(
            listOf(pack), concepts.associate { it.id to it.revision }, questions.associate { it.id to it.revision },
            packs.flatMap { it.chapters }.map { it.id }.toSet(), packs.flatMap { it.sections }.map { it.id }.toSet(), setOf("FA"),
        )
        assertFalse(plan.ok)
        assertTrue(plan.errors.any { "NEW-Q01" in it && "정답" in it })
        assertTrue(plan.errors.any { "NEW-Q02" in it && "해설" in it })
        assertTrue(plan.errors.any { "NEW-Q03" in it && "Z01" in it })
        assertTrue(plan.errors.any { "NEW-Q04" in it && "빈칸" in it })
        assertTrue(plan.errors.any { "중복" in it && "NEW-Q04" in it })
    }

    /**
     * v8 구축률 100% 검증(tools/content/coverage_validator.py 와 같은 기준을 앱 데이터로 독립 계산).
     * 목차 항목마다: 연결 개념 존재·활성·같은 시험과목, 개념마다 정의·간단 요약·DEFINITION 외 상세 섹션 2개 이상,
     * 연결 개념들의 활성 문항 합계 3개 이상(OX 없음, 정답·해설 있음). 저장된 covered 값은 판정에 쓰지 않는다.
     */
    @Test fun everyCurriculumItemComplete() {
        val subjects = packs.flatMap { it.subjects }.associateBy { it.id }
        val byId = concepts.associateBy { it.id }
        val activeQ = questions.filter { it.isActive }.groupBy { it.primaryConceptId }
        val items = packs.flatMap { it.curriculumItems }
        val failures = mutableListOf<String>()
        items.forEach { it ->
            val exam = subjects[it.subjectId]?.examSubject
            if (it.conceptIds.isEmpty()) { failures += "${it.id} ${it.item}: 연결 개념 없음"; return@forEach }
            val cons = it.conceptIds.map { cid -> byId[cid] }
            if (cons.any { c -> c == null || !c.isActive || subjects[c.subjectId]?.examSubject != exam }) failures += "${it.id} ${it.item}: 연결 개념 오류"
            val ok = cons.filterNotNull().filter { c -> c.isActive }
            ok.forEach { c ->
                val details = c.detailSections.count { d -> d.type != "DEFINITION" && d.body.isNotBlank() }
                if (c.definition.isBlank() || c.shortSummary.isNullOrBlank() || details < 2) failures += "${it.id}: ${c.id} 설명 부족"
            }
            val qs = ok.flatMap { c -> activeQ[c.id].orEmpty() }
            if (qs.size < 3) failures += "${it.id} ${it.item}: 활성 문항 ${qs.size}개"
            qs.forEach { q -> if (q.blankAnswer.isBlank() || q.explanation.isBlank()) failures += "${q.id}: 정답·해설 없음" }
        }
        val byExam = items.groupBy { subjects[it.subjectId]?.examSubject }.mapValues { it.value.size }
        File(TestContent.reportDir(), "curriculum-coverage.txt").writeText(
            "목차 ${items.size}개 · 과목별 $byExam · 미충족 ${failures.size}건\n" + failures.joinToString("\n"),
        )
        assertEquals(1233, items.size)
        assertTrue("미충족 목차: ${failures.take(20)}", failures.isEmpty())
        assertTrue("저장된 covered 값과 판정 일치", items.all { it.covered })
    }
}
