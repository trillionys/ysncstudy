package com.cpaconceptlab.study.ui.home

import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.remember
import com.cpaconceptlab.study.domain.usecase.ExamSubjectProgress
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cpaconceptlab.study.data.repository.UserSettings
import com.cpaconceptlab.study.domain.model.QuizMode
import com.cpaconceptlab.study.domain.model.QuizScope
import com.cpaconceptlab.study.domain.model.ScopeType
import com.cpaconceptlab.study.ui.DashboardState
import com.cpaconceptlab.study.ui.components.ProgressLine
import com.cpaconceptlab.study.ui.components.SectionCard
import com.cpaconceptlab.study.ui.components.StatTile
import com.cpaconceptlab.study.ui.components.VSpace
import com.cpaconceptlab.study.ui.components.pct
import com.cpaconceptlab.study.ui.theme.LocalStatusColors

@Composable
fun HomeScreen(
    dash: DashboardState,
    settings: UserSettings,
    onStartQuiz: (QuizMode, QuizScope) -> Unit,
    onOpenSetup: () -> Unit,
    onOpenExamSubject: (String) -> Unit,
    onOpenGoal: () -> Unit = {},
) {
    val ov = dash.overview
    val status = LocalStatusColors.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        OverallProgressCard(dash, onOpenExamSubject)
        VSpace(12)
        // 오늘의 학습
        SectionCard(title = "오늘의 학습") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("오늘 복습할 문제", "${ov.dueQuestions}", Modifier.weight(1f), accent = if (ov.dueQuestions > 0) status.warn else null)
                StatTile("미학습 개념", "${ov.unstudiedConcepts}", Modifier.weight(1f))
            }
            VSpace(8)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("오늘 푼 문제", "${ov.todayUniqueSolved}", Modifier.weight(1f), sub = "제출 ${ov.todaySolved}회")
                StatTile("연속 학습일", "${ov.streak}일", Modifier.weight(1f))
            }
            VSpace(10)
            DailyGoalLine(settings, ov.todayUniqueSolved, onOpenGoal)
        }
        VSpace(12)

        // 빠른 시작
        SectionCard(title = "빠른 시작") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onStartQuiz(QuizMode.BASIC, QuizScope(ScopeType.ALL)) }, modifier = Modifier.weight(1f)) { Text("기본 문제") }
                Button(onClick = { onStartQuiz(QuizMode.ADVANCED, QuizScope(ScopeType.ALL)) }, modifier = Modifier.weight(1f)) { Text("심화 문제") }
            }
            VSpace(8)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { onStartQuiz(QuizMode.BASIC, QuizScope(ScopeType.WRONG)) }, modifier = Modifier.weight(1f)) { Text("오답 복습 ${ov.unresolvedWrong}") }
                FilledTonalButton(onClick = { onStartQuiz(QuizMode.of(settings.lastMode), QuizScope(ScopeType.DUE)) }, modifier = Modifier.weight(1f)) { Text("오늘의 복습") }
            }
            VSpace(8)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onStartQuiz(QuizMode.of(settings.lastMode), QuizScope.decode(settings.lastScope)) }, modifier = Modifier.weight(1f)) { Text("이어서 공부하기") }
                OutlinedButton(onClick = onOpenSetup, modifier = Modifier.weight(1f)) { Text("범위 선택") }
            }
        }
        VSpace(12)

        VSpace(24)
    }
}

/** 전체 학습현황: 공식 시험과목 5개 요약. 과목을 누르면 세부 콘텐츠팩별 진도를 보여준다. */
@Composable
fun OverallProgressCard(dash: DashboardState, onOpen: (String) -> Unit) {
    val stats = remember(dash) { ExamSubjectProgress.summary(dash.content, dash.user) }
    SectionCard(title = "전체 학습현황") {
        stats.forEach { st ->
            ExamSubjectRow(st, onClick = { onOpen(st.id) })
        }
        Text(
            "개념 진도 = 학습 완료 개념(설명 확인 + 기본·심화 정답) / 활성 개념 · 정답률 = 정답 제출 / 전체 제출",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun ExamSubjectRow(st: ExamSubjectProgress.Stat, onClick: (() -> Unit)?) {
    val status = LocalStatusColors.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).let { if (onClick != null) it.clickable(onClick = onClick) else it },
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(st.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                when {
                    !st.hasContent -> Text("콘텐츠 없음", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    st.complete -> Text("✓ 완료", style = MaterialTheme.typography.labelLarge, color = status.correct)
                    else -> Text(pct(st.conceptRatio), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (st.hasContent) {
                Text(
                    "학습 완료 ${st.completedConcepts} / 전체 ${st.totalConcepts}개념 · 정답률 ${pct(st.accuracy)} · 복습 필요 ${st.reviewNeeded}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                VSpace(6)
                LinearProgressIndicator(
                    progress = { (st.conceptRatio ?: 0.0).toFloat() },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = if (st.complete) status.correct else MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    drawStopIndicator = {},
                )
            }
            st.buildRatio?.let {
                VSpace(4)
                Text("콘텐츠 구축 ${pct(it)} (출제범위 목차 ${st.curriculumCovered}/${st.curriculumTotal})",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}


/** 오늘의 문제 목표 진행률. 목표를 넘겨도 계속 풀 수 있고(초과 % 표시), 무제한이면 푼 문제 수만 표시한다. */
@Composable
fun DailyGoalLine(settings: UserSettings, solved: Int, onOpenGoal: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).testTag("daily-goal")) {
            if (settings.goalUnlimited) {
                Text("오늘 학습: ${"%,d".format(solved)}문제", style = MaterialTheme.typography.titleSmall)
                Text("자유 학습 중", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val goal = settings.dailyGoal.coerceAtLeast(1)
                val rate = solved * 100.0 / goal
                Text("오늘 목표 ${"%,d".format(goal)}문제 · 푼 문제 ${"%,d".format(solved)}문제", style = MaterialTheme.typography.titleSmall)
                Text(if (solved >= goal) "목표 달성: ${"%.1f".format(rate)}%" else "달성률: ${"%.1f".format(rate)}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (solved >= goal) LocalStatusColors.current.correct else MaterialTheme.colorScheme.onSurfaceVariant)
                VSpace(4)
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { (solved.toFloat() / goal).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), drawStopIndicator = {},
                )
            }
        }
        androidx.compose.material3.TextButton(onClick = onOpenGoal) { Text("목표 변경") }
    }
}
