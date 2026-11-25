package com.cpaconceptlab.study.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.geometry.Offset
import com.cpaconceptlab.study.MainActivity
import com.cpaconceptlab.study.TestContent
import androidx.compose.ui.test.performScrollToNode
import org.junit.Rule
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 주요 화면 캡처(디자인 확인용). 결과: app/build/reports/content/screens 폴더의 PNG */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    /** v1.0.7: 최초 실행 시 '학습 콘텐츠 이용 안내'가 뜬다. 확인하면 다시 뜨지 않는다. */
    private fun acknowledgeNotice() {
        rule.waitUntil(60_000) {
            rule.onAllNodesWithText("확인했습니다").fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodesWithText("오늘의 학습").fetchSemanticsNodes().isNotEmpty()
        }
        if (rule.onAllNodesWithText("확인했습니다").fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithText("확인했습니다").performClick()
            rule.waitUntil(10_000) { rule.onAllNodesWithText("확인했습니다").fetchSemanticsNodes().isEmpty() }
        }
    }

    private fun waitText(t: String, sub: Boolean = false) =
        rule.waitUntil(60_000) { rule.onAllNodesWithText(t, substring = sub).fetchSemanticsNodes().isNotEmpty() }

    private fun shot(name: String) {
        rule.waitForIdle()
        val dir = File(TestContent.reportDir(), "screens").apply { mkdirs() }
        rule.activityRule.scenario.onActivity { activity ->
            val view = activity.window.decorView
            val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bmp))
            File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun tab(label: String) =
        rule.onNode(androidx.compose.ui.test.hasText(label) and androidx.compose.ui.test.hasClickAction()).performClick()

    // v6: 홈 → 세법개론 상세(세율표 데이터 카드, 법률 단원별 진도, 기본·심화·계산 정답률)
    @Test fun captureTaxSubject() {
        acknowledgeNotice()
        waitText("전체 학습현황")
        rule.onNode(androidx.compose.ui.test.hasText("세법개론") and androidx.compose.ui.test.hasClickAction()).performScrollTo().performClick()
        waitText("세율표", sub = true)
        waitText("법인세 — 법인세법 제55조①1호", sub = true)
        shot("10_tax_subject")
        rule.onNode(androidx.compose.ui.test.hasScrollToNodeAction())
            .performScrollToNode(androidx.compose.ui.test.hasText("계산", substring = true))
        waitText("오답 개념", sub = true)
        shot("11_tax_subject_stats")
    }

    // 문제 풀이 도구: 계산문제의 계산기(답에 넣기)와 모든 문제의 투명 필기
    @Test fun calculatorAndInkOnQuestions() {
        acknowledgeNotice()
        waitText("전체 학습현황")
        rule.onNode(androidx.compose.ui.test.hasText("세법개론") and androidx.compose.ui.test.hasClickAction()).performScrollTo().performClick()
        waitText("세율표", sub = true)
        rule.onNode(androidx.compose.ui.test.hasText("학습 가능", substring = true) and androidx.compose.ui.test.hasClickAction())
            .performScrollTo().performClick()
        waitText("학습 모드")
        rule.onNode(androidx.compose.ui.test.hasScrollToNodeAction())
            .performScrollToNode(androidx.compose.ui.test.hasText("계산", substring = false))
        rule.onAllNodes(androidx.compose.ui.test.hasText("계산") and androidx.compose.ui.test.hasClickAction())[0].performClick()
        rule.waitUntil(60_000) { rule.onAllNodes(androidx.compose.ui.test.hasTestTag("ink-button")).fetchSemanticsNodes().isNotEmpty() }
        // 계산기
        rule.onNode(androidx.compose.ui.test.hasTestTag("calculator-button")).performClick()
        listOf("2", "0", "0", "×", "1", "5", "%").forEach { k ->
            rule.onNode(androidx.compose.ui.test.hasText(k) and androidx.compose.ui.test.hasClickAction()).performClick()
        }
        rule.onNode(androidx.compose.ui.test.hasTestTag("calculator-equals")).performClick()
        rule.onNode(androidx.compose.ui.test.hasTestTag("calculator-display")).assert(androidx.compose.ui.test.hasText("30"))
        shot("12_calculator")
        rule.onNodeWithText("답에 넣기").performClick()
        rule.onNode(hasSetTextAction()).assert(androidx.compose.ui.test.hasText("30"))
        // 필기: 켜고 문제 위에 밑줄 한 획
        rule.onNode(androidx.compose.ui.test.hasTestTag("ink-button")).performClick()
        waitText("필기 끄기")
        rule.onRoot().performTouchInput { swipe(Offset(80f, 420f), Offset(900f, 430f), 300) }
        shot("13_ink")
        rule.onNodeWithText("필기 끄기").performClick()
        waitText("필기 (1)")
    }

    // v1.0.7 시나리오: 중급회계 → 유형자산 단원 문제 풀기(난이도·유형 선택) / 감가상각 상세 학습(공식·분개)
    @Test fun unitDifficultyAndDepreciationDetail() {
        acknowledgeNotice()
        tab("과목")
        waitText("중급회계")
        rule.onNodeWithText("중급회계").performClick()
        waitText("유형자산")
        rule.onNode(androidx.compose.ui.test.hasScrollToNodeAction()).performScrollToNode(androidx.compose.ui.test.hasText("유형자산"))
        // 유형자산 파트 카드의 '문제 풀기' → 난이도·유형 시트
        val partButtons = rule.onAllNodes(androidx.compose.ui.test.hasText("문제 풀기") and androidx.compose.ui.test.hasClickAction())
        val idx = (0 until partButtons.fetchSemanticsNodes().size).first { i ->
            runCatching { partButtons[i].assertExists(); true }.getOrDefault(false)
        }
        partButtons[idx].performScrollTo().performClick()
        rule.waitUntil(30_000) { rule.onAllNodes(androidx.compose.ui.test.hasTestTag("start-quiz-sheet")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(androidx.compose.ui.test.hasText("어려움") and androidx.compose.ui.test.hasClickAction()).performClick()
        rule.onNode(androidx.compose.ui.test.hasTestTag("start-quiz-count")).assert(androidx.compose.ui.test.hasText("출제 가능한 문제", substring = true))
        shot("09_unit_sheet")
        rule.onNode(androidx.compose.ui.test.hasTestTag("start-quiz-button")).performClick()
        waitText("어려움")
        shot("09b_unit_quiz_hard")
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitText("문제 풀기")
    }

    @Test fun captureMainScreens() {
        acknowledgeNotice()
        waitText("오늘의 학습")
        // v5: 홈에는 공식 시험과목별 '전체 학습현황'이 있고 '기출범위별 학습현황'은 없다
        waitText("전체 학습현황")
        listOf("회계학", "경영학", "경제원론", "기업법", "세법개론").forEach { waitText(it) }
        assertTrue(rule.onAllNodesWithText("기출범위별", substring = true).fetchSemanticsNodes().isEmpty())
        shot("01_home")
        rule.onNodeWithText("기본 문제").performScrollTo().performClick()
        // 전체 범위에는 빈칸·4지선다·매칭형이 섞여 있다(v4: 기업법 포함). 문항 형식과 무관하게 진행한다.
        waitText("모르겠음")
        shot("02_quiz_basic")
        if (rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()) {
            rule.onNode(hasSetTextAction()).performTextInput("모르는 답")
            rule.onNodeWithText("제출").performClick()
            waitText("오답입니다")
        } else {
            rule.onNodeWithText("모르겠음").performClick()
            waitText("정답을 확인하세요")
        }
        shot("03_quiz_feedback")
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitText("오늘의 학습")
        tab("과목")
        // v7: '재무회계' 독립 팩은 없고 중급회계로 통합되었다
        waitText("중급회계")
        assertTrue(rule.onAllNodesWithText("재무회계").fetchSemanticsNodes().isEmpty())
        rule.onNodeWithText("중급회계").performClick()
        waitText("유형자산")
        shot("04a_part_cards")
        rule.onNode(androidx.compose.ui.test.hasScrollToNodeAction())
            .performScrollToNode(androidx.compose.ui.test.hasText("D. 재무정보의 질적특성"))
        rule.onNodeWithText("D. 재무정보의 질적특성").performClick()
        waitText("목적적합성")
        shot("04_subject_tree")
        rule.onNode(androidx.compose.ui.test.hasText("목적적합성") and androidx.compose.ui.test.hasClickAction()).performScrollTo().performClick()
        waitText("간단 요약")
        waitText("상세 학습", sub = true)
        shot("05_concept_summary")
        rule.onNode(androidx.compose.ui.test.hasText("상세 학습", substring = true) and androidx.compose.ui.test.hasClickAction()).performClick()
        waitText("개념의 정의")
        shot("05b_concept_detail")
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitText("통계")
        tab("통계")
        waitText("학습 요약")
        shot("06_stats")
        tab("오답노트")
        waitText("오답노트 (", sub = true)
        shot("07_wrong_notes")
        // v4: 기업법 과목 화면(학습 모드 2종·조문 커버리지)
        tab("과목")
        waitText("중급회계")
        rule.onNode(androidx.compose.ui.test.hasScrollToNodeAction()).performScrollToNode(androidx.compose.ui.test.hasText("기업법"))
        rule.onNodeWithText("기업법").performClick()
        waitText("학습 모드")
        shot("08_law_subject")
    }
}
