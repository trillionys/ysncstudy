package com.cpaconceptlab.study.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.cpaconceptlab.study.CpaApplication
import com.cpaconceptlab.study.MainActivity
import com.cpaconceptlab.study.data.model.BLANK_TOKEN
import com.cpaconceptlab.study.utils.Chosung
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 실제 MainActivity 를 실행하는 런타임 검증:
 * 앱 실행 → 콘텐츠 로딩 → 첫 문제 출력(초성 힌트) → 정답 입력·채점 → 학습기록 저장 → 오답 처리·오답노트 저장
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AppUiTest {
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

    private val app get() = ApplicationProvider.getApplicationContext<CpaApplication>()

    private fun waitText(text: String, substring: Boolean = false, timeout: Long = 60_000) =
        rule.waitUntil(timeout) { rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }

    private fun currentQuestionText(): String {
        val node = rule.onAllNodes(hasText("______", substring = true)).fetchSemanticsNodes().first()
        return node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString("") { it.text }
    }

    @Test fun launchLoadContentAnswerAndPersist() {
        acknowledgeNotice()
        // 1) 앱 실행 및 콘텐츠 로딩
        waitText("오늘의 학습")
        rule.onNodeWithText("전체 학습현황").assertExists()
        val snapshot = app.container.contentRepository.snapshot.value!!
        assertEquals(com.cpaconceptlab.study.TestContent.manifest.conceptCount, snapshot.concepts.size)

        // 2) 기본 모드·빈칸형만 선택해 시작 → 첫 문제와 초성 힌트 출력
        //    (전체 범위에는 4지선다·매칭형도 섞여 있으므로 '이어서 공부하기'에 빈칸형 필터를 지정한다)
        runBlocking {
            app.container.settingsRepository.update {
                it.copy(lastMode = "BASIC", lastScope = com.cpaconceptlab.study.domain.model.QuizScope(
                    com.cpaconceptlab.study.domain.model.ScopeType.ALL, kind = com.cpaconceptlab.study.domain.model.QuestionKind.BLANK,
                ).encode())
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("이어서 공부하기").performScrollTo().performClick()
        waitText("초성 힌트", substring = true)
        val shown = currentQuestionText()
        val q1 = snapshot.questions.first { it.questionText.replace(BLANK_TOKEN, " ______ ") == shown }
        rule.onNodeWithText(Chosung.hint(q1.blankAnswer)).assertExists()

        // 3) 정답 입력 → 채점 → 해설 표시
        rule.onNode(hasSetTextAction()).performTextInput(q1.blankAnswer)
        rule.onNodeWithText("제출").performClick()
        waitText("정답입니다")
        rule.onNodeWithText("[왜 이 답이 정답인지]").assertExists()

        // 4) 학습기록 저장 확인
        rule.waitUntil(20_000) { runBlocking { app.container.db.progressDao().attemptCount() } == 1 }
        val p1 = runBlocking { app.container.db.progressDao().questionProgress(q1.id, "BASIC") }!!
        assertTrue(p1.lastCorrect)
        assertEquals(1, p1.correctAttempts)

        // 5) 다음 문제: 오답 → 오답노트 저장
        rule.onNodeWithText("다음 문제").performScrollTo().performClick()
        rule.waitUntil(20_000) { runCatching { currentQuestionText() != shown }.getOrDefault(false) }
        val q2 = snapshot.questions.first { it.questionText.replace(BLANK_TOKEN, " ______ ") == currentQuestionText() }
        assertFalse(q1.primaryConceptId == q2.primaryConceptId)   // 균등 출제: 같은 세션에서 개념 반복 없음
        rule.onNode(hasSetTextAction()).performTextInput("전혀다른답")
        rule.onNodeWithText("제출").performClick()
        waitText("오답입니다")
        rule.waitUntil(20_000) { runBlocking { app.container.db.progressDao().allWrongNotes() }.any { it.questionId == q2.id } }
        val note = runBlocking { app.container.db.progressDao().wrongNote(q2.id, "BASIC") }!!
        assertEquals("전혀다른답", note.lastUserAnswer)
        assertFalse(note.isResolved)
    }
}
