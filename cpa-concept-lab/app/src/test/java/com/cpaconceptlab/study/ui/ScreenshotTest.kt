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
        shot("01_home")
        rule.onNodeWithText("기본 문제").performScrollTo().performClick()
        waitText("초성 힌트", sub = true)
        shot("02_quiz_basic")
        rule.onNode(hasSetTextAction()).performTextInput("모르는 답")
        rule.onNodeWithText("제출").performClick()
        waitText("오답입니다")
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
    }
}
