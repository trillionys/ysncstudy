package com.cpaconceptlab.study.ui

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.cpaconceptlab.study.CpaApplication
import com.cpaconceptlab.study.MainActivity
import com.cpaconceptlab.study.TestContent
import com.cpaconceptlab.study.domain.model.QuizMode
import com.cpaconceptlab.study.domain.usecase.ReviewScheduler
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Google Play 스토어 등록용 스크린샷(실제 앱 화면).
 *  - 휴대전화 1080×1920(9:16), 7인치 태블릿 1200×1920, 10인치 태블릿 1600×2560 — 모두 Play 규격(한 변 320~3840px, 긴 변 ≤ 짧은 변의 2배).
 *  - 빈 화면(0%)이 아니라 실제 사용 모습을 보이도록, 화면을 찍기 전에 앱 저장소 API 로 예시 학습기록을 넣는다
 *    (일부 개념 학습 완료 + 일부 오답). 화면 자체는 조작하지 않은 실제 앱 화면이다.
 * 결과: app/build/reports/content/store/{phone,tablet7,tablet10}/NN_name.png
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StoreScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun waitText(t: String, sub: Boolean = false, ms: Long = 60_000) =
        rule.waitUntil(ms) { rule.onAllNodesWithText(t, substring = sub).fetchSemanticsNodes().isNotEmpty() }

    private fun shot(device: String, name: String) {
        rule.waitForIdle()
        Thread.sleep(300)
        rule.waitForIdle()
        val dir = File(TestContent.reportDir(), "store/$device").apply { mkdirs() }
        rule.activityRule.scenario.onActivity { activity ->
            val view = activity.window.decorView
            val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bmp))
            File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun tab(label: String) = rule.onNode(hasText(label) and hasClickAction()).performClick()
    private fun back() = rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
    private fun home() {
        repeat(8) {
            rule.waitForIdle()
            if (rule.onAllNodesWithText("오늘의 학습").fetchSemanticsNodes().isNotEmpty()) return
            if (rule.onAllNodes(hasText("홈") and hasClickAction()).fetchSemanticsNodes().isNotEmpty()) { tab("홈"); return@repeat }
            back()
        }
        waitText("오늘의 학습")
    }

    private fun start() {
        rule.waitUntil(60_000) {
            rule.onAllNodesWithText("확인했습니다").fetchSemanticsNodes().isNotEmpty() || rule.onAllNodesWithText("오늘의 학습").fetchSemanticsNodes().isNotEmpty()
        }
        if (rule.onAllNodesWithText("확인했습니다").fetchSemanticsNodes().isNotEmpty()) rule.onNodeWithText("확인했습니다").performClick()
        waitText("오늘의 학습")
        seed()
    }

    /** 예시 학습기록: 과목별 앞쪽 개념 일부를 학습 완료(열람 + 기본·심화 정답), 일부 문항은 오답 */
    private fun seed() = runBlocking {
        var container: com.cpaconceptlab.study.AppContainer? = null
        rule.activityRule.scenario.onActivity { container = (it.application as CpaApplication).container }
        val c = container!!
        val content = c.contentRepository.snapshot.filterNotNull().first()
        c.settingsRepository.update { it.copy(dailyGoal = 100, goalUnlimited = false) }
        val now = System.currentTimeMillis()
        val scheduler = ReviewScheduler()
        val plan = mapOf("IA" to 60, "AA" to 6, "CM" to 8, "FM" to 10, "EC" to 9, "CPA-LAW" to 70, "TX" to 45, "GA" to 3, "HR" to 5)
        var t = now - 3 * 3600_000L
        plan.forEach { (subject, n) ->
            content.concepts.filter { it.subjectId == subject }.sortedBy { it.sortOrder }.take(n).forEachIndexed { i, con ->
                val qs = content.questionsByPrimary[con.id].orEmpty()
                val q = qs.firstOrNull() ?: return@forEachIndexed
                val map = mapOf(con.id to qs.map { it.id })
                c.studyRepository.markConceptViewed(con.id, t)
                val wrong = i % 7 == 3
                c.studyRepository.recordAnswer(q, QuizMode.BASIC, q.blankAnswer, true, 8_000, t, scheduler, map)
                c.studyRepository.recordAnswer(q, QuizMode.ADVANCED, if (wrong) "오답" else q.blankAnswer, !wrong, 15_000, t + 1000, scheduler, map)
                t += 60_000
            }
        }
        Thread.sleep(500)
    }

    private fun openSubjectSearch(subjectLabel: String, query: String, conceptName: String) {
        tab("과목")
        waitText("중급회계")
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(subjectLabel))
        rule.onNode(hasText(subjectLabel) and hasClickAction()).performClick()
        rule.onNode(hasSetTextAction()).performTextInput(query)
        waitText(conceptName)
        rule.onNode(hasText(conceptName) and hasClickAction() and androidx.compose.ui.test.hasSetTextAction().not()).performClick()
        waitText("간단 요약")
    }

    private fun capture(device: String, full: Boolean) {
        start()
        // 1. 홈: 전체 학습현황 + 오늘의 학습
        shot(device, "01_home")
        // 2. 과목별 학습현황(세법개론: 세율표 데이터·단원별 진도)
        rule.onNode(hasText("세법개론") and hasClickAction()).performScrollTo().performClick()
        waitText("세율표", sub = true)
        shot(device, "02_subject_tax")
        home()
        // 3·4. 개념 학습: 간단 요약 / 상세 학습(계산 예제·분개)
        openSubjectSearch("중급회계", "감가상각의 정의", "감가상각의 정의")
        shot(device, "03_concept_summary")
        rule.onNode(hasText("상세 학습", substring = true) and hasClickAction()).performClick()
        waitText("정액법 기본 계산과 분개")
        rule.onAllNodes(hasText("감가상각대상금액 = 10,000,000", substring = true))[0].performScrollTo()
        shot(device, "04_concept_detail")
        // 5·6. 문제 풀이(초성 힌트) → 해설
        rule.onNode(hasText("기본 문제") and hasClickAction()).performScrollTo().performClick()
        waitText("모르겠음")
        shot(device, "05_quiz")
        if (!full) return
        if (rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()) {
            rule.onNode(hasSetTextAction()).performTextInput("상각")
            rule.onNodeWithText("제출").performClick()
        } else rule.onNodeWithText("모르겠음").performClick()
        waitText("관련 개념 자세히 보기")
        shot(device, "06_feedback")
        home()
        // 7. 계산 문제 + 투명 필기
        openSubjectSearch("세법개론", "법인세율", "과세표준과 산출세액(법인세율)")
        rule.onNode(hasText("계산 연습", substring = true) and hasClickAction()).performScrollTo().performClick()
        rule.waitUntil(60_000) { rule.onAllNodes(hasTestTag("ink-button")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(hasTestTag("ink-button")).performClick()
        waitText("필기 끄기")
        // 문제 문장 위에 밑줄 두 줄(과세표준 금액, 산출세액) — 실제 필기 입력 경로(터치)로 그린다
        val q = rule.onAllNodes(hasText("과세표준이", substring = true))[0].fetchSemanticsNode().boundsInRoot
        val lineH = q.height / 3f
        rule.onRoot().performTouchInput { swipe(Offset(q.left + q.width * 0.38f, q.top + lineH * 1.92f), Offset(q.left + q.width * 0.80f, q.top + lineH * 1.95f), 300) }
        rule.onRoot().performTouchInput { swipe(Offset(q.left, q.top + lineH * 2.92f), Offset(q.left + q.width * 0.22f, q.top + lineH * 2.94f), 200) }
        shot(device, "07_calc_ink")
        rule.onNodeWithText("필기 끄기").performClick()
        home()
        // 8. 복습 주기 설정(프리셋 + 날짜 미리보기)
        tab("설정")
        waitText("복습 주기")
        rule.onNode(hasText("복습 주기") and hasClickAction()).performClick()
        waitText("복습 일정 미리보기")
        rule.onAllNodes(hasText("1차 복습", substring = true))[0].performScrollTo()
        shot(device, "08_review_settings")
    }

    @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    @Test fun phone() = capture("phone", full = true)

    @Config(qualifiers = "w600dp-h960dp-xhdpi")
    @Test fun tablet7() = capture("tablet7", full = false)

    @Config(qualifiers = "w800dp-h1280dp-xhdpi")
    @Test fun tablet10() = capture("tablet10", full = false)
}
