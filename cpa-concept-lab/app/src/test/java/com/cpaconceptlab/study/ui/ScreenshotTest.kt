package com.cpaconceptlab.study.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
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

    @Test fun captureMainScreens() {
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
        waitText("재무회계")
        rule.onNodeWithText("재무회계").performClick()
        waitText("유형자산")
        shot("04a_part_cards")
        rule.onNode(androidx.compose.ui.test.hasScrollToNodeAction())
            .performScrollToNode(androidx.compose.ui.test.hasText("D. 재무정보의 질적특성"))
        rule.onNodeWithText("D. 재무정보의 질적특성").performClick()
        waitText("목적적합성")
        shot("04_subject_tree")
        rule.onNode(androidx.compose.ui.test.hasText("목적적합성") and androidx.compose.ui.test.hasClickAction()).performScrollTo().performClick()
        waitText("① 정확한 정의")
        shot("05_concept")
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
        waitText("재무회계")
        rule.onNode(androidx.compose.ui.test.hasScrollToNodeAction()).performScrollToNode(androidx.compose.ui.test.hasText("기업법"))
        rule.onNodeWithText("기업법").performClick()
        waitText("학습 모드")
        shot("08_law_subject")
    }
}
